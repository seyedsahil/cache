package org.sydlabz.sd.core.cache;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A lightweight implementation of a general purpose cache created for education purpose.
 * <p>
 * Feel free to create a pull request if you think this can be optimized :)
 *
 * @author seyedsahil
 * @version 1.0
 */
public final class Cache {
    private static final boolean DEBUG_MODE = true;
    private static final Random random = new Random();

    private final Map<String, CacheableWrapper> cachedDataStore;
    private final DataSource dataSource;
    private final LinkedList<String> evictionQueue;
    private final LinkedList<DataSourceItem> dataSyncQueue;
    private final Timer invalidationTimer;
    private final Timer writeBehindTimer;
    private final CachePolicy cachePolicy;
    private final String name;
    private boolean active;

    public Cache(String name, DataSource dataSource) {
        this(name, dataSource, CachePolicy.getDefaultPolicy());
    }

    public Cache(String name, DataSource dataSource, CachePolicy cachePolicy) {
        this.name = name;

        this.cachedDataStore = new ConcurrentHashMap<>();
        this.dataSource = dataSource;
        this.cachePolicy = cachePolicy;

        this.evictionQueue = new LinkedList<>();
        this.dataSyncQueue = new LinkedList<>();

        this.invalidationTimer = new Timer();
        this.writeBehindTimer = new Timer();

        this.attachBackgroundTasks();

        this.active = true;
    }

    private void attachBackgroundTasks() {
        this.invalidationTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                invalidateCachedRecords();
            }
        }, this.cachePolicy.getDelay(), this.cachePolicy.getInvalidationFrequency());

        if (this.cachePolicy.getWriteStrategy() == WriteStrategy.WRITE_BEHIND) {
            this.writeBehindTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    syncWithDataSource();
                }
            }, this.cachePolicy.getDelay(), this.cachePolicy.getWriteBehindFrequency());
        }
    }

    public synchronized Optional<Cacheable> get(final String key) {
        if (!this.active) {
            throw new RuntimeException("get called with inactive cache");
        }

        CacheableWrapper cachedRecord = this.cachedDataStore.get(key);

        if (Util.usable(cachedRecord)) {
            return fromCache(cachedRecord);
        } else {
            return fromDataSource(key);
        }
    }

    private synchronized Optional<Cacheable> fromCache(CacheableWrapper cachedRecord) {
        String key = cachedRecord.key;

        cachedRecord.accessCount++;
        cachedRecord.lastAccessedTime = System.currentTimeMillis();

        EvictionStrategy evictionStrategy = this.cachePolicy.getEvictionStrategy();

        if (evictionStrategy != EvictionStrategy.FIFO) {
            this.evictionQueue.remove(key);
            this.evictionQueue.offer(key);
        }

        return Optional.of(cachedRecord.data);
    }

    private Optional<Cacheable> fromDataSource(String key) {
        Cacheable data = this.dataSource.load(key);

        if (!Util.usable(data)) {
            return Optional.empty();
        }

        if (this.full()) {
            this.doCacheEviction();
        }

        this.cachedDataStore.put(key, new CacheableWrapper(key, data));
        this.evictionQueue.offer(key);

        return Optional.of(data);
    }

    private boolean full() {
        return this.cachedDataStore.size() == this.cachePolicy.getCacheSize();
    }

    public synchronized void put(String key, Cacheable data) {
        if (!this.active) {
            throw new RuntimeException("put called with inactive cache");
        }

        CacheableWrapper freshRecord = new CacheableWrapper(key, data);
        CacheableWrapper cachedRecord = this.cachedDataStore.get(key);
        boolean isUpdate = false;

        if (Util.usable(cachedRecord)) {
            isUpdate = true;
            freshRecord.accessCount = cachedRecord.accessCount + 1;
        }

        this.cachedDataStore.put(key, freshRecord);

        this.updateEvictionQueue(freshRecord, !isUpdate);
        this.applyWriteStrategy(freshRecord, isUpdate);
    }

    private synchronized void updateEvictionQueue(CacheableWrapper cachedRecord, boolean freshRecord) {
        String key = cachedRecord.key;

        if (this.cachePolicy.getEvictionStrategy() == EvictionStrategy.FIFO) {
            if (!freshRecord) {
                this.evictionQueue.remove(key);
            }

            this.evictionQueue.offer(key);
        } else {
            if (freshRecord) {
                this.evictionQueue.offer(key);
            }
        }
    }

    private synchronized void applyWriteStrategy(CacheableWrapper cachedRecord, boolean isUpdate) {
        String key = cachedRecord.key;
        WriteStrategy writeStrategy = this.cachePolicy.getWriteStrategy();

        if (writeStrategy == WriteStrategy.WRITE_THROUGH) {
            if (isUpdate) {
                this.dataSource.update(key, cachedRecord.data);
            } else {
                this.dataSource.save(key, cachedRecord.data);
            }
        } else if (writeStrategy == WriteStrategy.WRITE_BEHIND) {
            this.dataSyncQueue.add(new DataSourceItem(cachedRecord, isUpdate));
        }
    }

    private synchronized void syncWithDataSource() {
        if (this.dataSyncQueue.isEmpty()) {
            return;
        }

        this.dataSyncQueue.forEach(dataSourceItem -> {
            CacheableWrapper cachedRecord = dataSourceItem.cachedRecord;

            if (dataSourceItem.isUpdate) {
                this.dataSource.update(cachedRecord.key, cachedRecord.data);
            } else {
                this.dataSource.save(cachedRecord.key, cachedRecord.data);
            }
        });
        this.dataSyncQueue.clear();
    }

    private synchronized void doCacheEviction() {
        if (this.evictionQueue.isEmpty() || this.cachedDataStore.isEmpty()) {
            return;
        }

        EvictionStrategy evictionStrategy = this.cachePolicy.getEvictionStrategy();

        if (evictionStrategy == EvictionStrategy.FIFO) {
            this.cachedDataStore.remove(this.evictionQueue.poll());
        } else if (evictionStrategy == EvictionStrategy.LRU) {
            Map.Entry<String, CacheableWrapper> cachedRecord = this.cachedDataStore.entrySet().stream().min((entry1, entry2) -> Math.toIntExact(entry1.getValue().lastAccessedTime - entry2.getValue().lastAccessedTime)).get();

            this.delete(cachedRecord.getKey());
        } else if (evictionStrategy == EvictionStrategy.LFU) {
            Map.Entry<String, CacheableWrapper> cachedRecord = this.cachedDataStore.entrySet().stream().min((entry1, entry2) -> Math.toIntExact(entry1.getValue().accessCount - entry2.getValue().accessCount)).get();

            this.delete(cachedRecord.getKey());
        } else if (evictionStrategy == EvictionStrategy.RANDOM) {
            int randomIndex = Cache.random.nextInt(evictionQueue.size());

            this.delete(this.evictionQueue.get(randomIndex));
        }
    }

    private synchronized void delete(String key) {
        this.evictionQueue.remove(key);
        this.cachedDataStore.remove(key);
    }

    private synchronized void invalidateCachedRecords() {
        if (this.evictionQueue.isEmpty() || this.cachedDataStore.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        Iterator<String> iterator = this.cachedDataStore.keySet().iterator();

        while (iterator.hasNext()) {
            this.applyInvalidationStrategy(currentTime, iterator);
        }

        System.out.println(this);
    }

    private synchronized void applyInvalidationStrategy(long currentTime, Iterator<String> iterator) {
        String key = iterator.next();
        CacheableWrapper record = this.cachedDataStore.get(key);
        InvalidationStrategy invalidationStrategy = this.cachePolicy.getInvalidationStrategy();

        if (invalidationStrategy == InvalidationStrategy.TIME_TO_LIVE) {
            if (currentTime - record.createdTime > this.cachePolicy.getInvalidationLifeTime()) {
                this.delete(key, iterator);
            }
        } else if (invalidationStrategy == InvalidationStrategy.TIME_BASED) {
            if (currentTime - record.lastAccessedTime > this.cachePolicy.getInvalidationLifeTime()) {
                this.delete(key, iterator);
            }
        } else if (invalidationStrategy == InvalidationStrategy.REFRESH) {
            if (currentTime - record.createdTime > cachePolicy.getInvalidationLifeTime()) {
                this.delete(key);

                Cacheable data = this.dataSource.load(key);

                if (!Util.usable(data)) {
                    return;
                }

                CacheableWrapper freshRecord = new CacheableWrapper(key, data);

                freshRecord.accessCount = record.accessCount + 1;
                this.cachedDataStore.put(key, freshRecord);
                this.evictionQueue.offer(key);
            }
        }
    }

    private synchronized void delete(String key, Iterator<String> iterator) {
        iterator.remove();
        this.evictionQueue.remove(key);
    }

    public synchronized void shutdown(boolean force) {
        WriteStrategy writeStrategy = this.cachePolicy.getWriteStrategy();

        if (force || writeStrategy == WriteStrategy.WRITE_THROUGH) {
            this.shutdown();
        } else if (writeStrategy == WriteStrategy.WRITE_BEHIND) {
            this.syncWithDataSource();

            this.shutdown();
        }
    }

    private synchronized void shutdown() {
        this.active = false;
        this.invalidationTimer.cancel();
        this.writeBehindTimer.cancel();
        this.cachedDataStore.clear();
        this.evictionQueue.clear();
        this.dataSyncQueue.clear();
    }

    @Override
    public String toString() {
        return "{" +
                "nm=" + this.name +
                "ds=" + this.cachedDataStore.values() +
                ", eq=" + this.evictionQueue +
                '}';
    }

    private static class CacheableWrapper {
        private final String key;
        private final Cacheable data;
        private final long createdTime;
        private long lastAccessedTime;
        private long accessCount;

        public CacheableWrapper(String key, Cacheable data) {
            this.data = data;
            this.createdTime = System.currentTimeMillis();
            this.lastAccessedTime = createdTime;
            this.accessCount = 1;
            this.key = key;
        }

        @Override
        public String toString() {
            return "{" +
                    "ct=" + Util.time(this.createdTime) +
                    ", lt=" + Util.time(this.lastAccessedTime) +
                    ", ac=" + this.accessCount +
                    ", k=" + this.key +
                    '}';
        }
    }

    private record DataSourceItem(CacheableWrapper cachedRecord, boolean isUpdate) {
    }
}
