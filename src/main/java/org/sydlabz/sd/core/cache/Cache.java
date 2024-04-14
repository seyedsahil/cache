package org.sydlabz.sd.core.cache;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A lightweight implementation of a general purpose cache created for education purpose.
 * <p>
 * Feel free to create a pull request if you think this can be optimized :)
 *
 * @author seyedsahil
 * @version 1.0
 */
public final class Cache {
    private static final Random random = new Random();

    private final BucketMap dataStoreMap;
    private final DataSource dataSource;
    private final LinkedList<String> evictionQueue;
    private final LinkedList<DataSourceItem> dataSyncQueue;
    private final Timer invalidationTimer;
    private final Timer writeBehindTimer;
    private final CachePolicy cachePolicy;
    private final String name;
    private final Executor executor;
    private CountDownLatch latch;
    private boolean active;

    public Cache(String name, DataSource dataSource, CachePolicy cachePolicy) {
        this.name = name;

        this.dataStoreMap = new BucketMap(16);
        this.dataSource = dataSource;
        this.cachePolicy = cachePolicy;

        this.evictionQueue = new LinkedList<>();
        this.dataSyncQueue = new LinkedList<>();

        this.invalidationTimer = new Timer();
        this.writeBehindTimer = new Timer();

        this.executor = Executors.newCachedThreadPool();

        this.attachBackgroundTasks();

        this.active = true;
    }

    private synchronized void attachBackgroundTasks() {
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

        CacheableWrapper cachedRecord = this.dataStoreMap.get(key);

        if (Util.usable(cachedRecord)) {
            return fromCache(cachedRecord);
        } else {
            return fromDataSource(key);
        }
    }

    private synchronized Optional<Cacheable> fromCache(CacheableWrapper cachedRecord) {
        String key = cachedRecord.getKey();

        cachedRecord.incrementAccessCount();
        cachedRecord.setLastAccessedTime(System.currentTimeMillis());

        EvictionStrategy evictionStrategy = this.cachePolicy.getEvictionStrategy();

        if (evictionStrategy != EvictionStrategy.FIFO) {
            this.evictionQueue.remove(key);
            this.evictionQueue.offer(key);
        }

        return Optional.of(cachedRecord.getData());
    }

    private synchronized Optional<Cacheable> fromDataSource(String key) {
        Cacheable data = this.dataSource.load(key);

        if (!Util.usable(data)) {
            return Optional.empty();
        }

        if (this.full()) {
            this.doCacheEviction();
        }

        this.dataStoreMap.put(key, new CacheableWrapper(key, data));
        this.evictionQueue.offer(key);

        return Optional.of(data);
    }

    private synchronized boolean full() {
        return this.dataStoreMap.size() == this.cachePolicy.getCacheSize();
    }

    public synchronized boolean isFull() {
        return this.full();
    }

    public synchronized void put(String key, Cacheable data) {
        if (!this.active) {
            throw new RuntimeException("put called with inactive cache");
        }

        CacheableWrapper freshRecord = new CacheableWrapper(key, data);
        CacheableWrapper cachedRecord = this.dataStoreMap.get(key);
        boolean isUpdate = false;

        if (Util.usable(cachedRecord)) {
            isUpdate = true;
            freshRecord.setAccessCount(cachedRecord.getAccessCount() + 1);
        }

        this.dataStoreMap.put(key, freshRecord);

        this.addToEvictionQueue(freshRecord, !isUpdate);
        this.applyWriteStrategy(freshRecord, isUpdate);
    }

    private synchronized void addToEvictionQueue(CacheableWrapper cachedRecord, boolean freshRecord) {
        String key = cachedRecord.getKey();

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
        String key = cachedRecord.getKey();
        WriteStrategy writeStrategy = this.cachePolicy.getWriteStrategy();

        if (writeStrategy == WriteStrategy.WRITE_THROUGH) {
            if (isUpdate) {
                this.dataSource.update(key, cachedRecord.getData());
            } else {
                this.dataSource.save(key, cachedRecord.getData());
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
            CacheableWrapper cachedRecord = dataSourceItem.cachedRecord();

            if (dataSourceItem.isUpdate()) {
                this.dataSource.update(cachedRecord.getKey(), cachedRecord.getData());
            } else {
                this.dataSource.save(cachedRecord.getKey(), cachedRecord.getData());
            }
        });
        this.dataSyncQueue.clear();
    }

    private synchronized void doCacheEviction() {
        if (this.evictionQueue.isEmpty() || this.dataStoreMap.isEmpty()) {
            return;
        }

        EvictionStrategy evictionStrategy = this.cachePolicy.getEvictionStrategy();

        if (evictionStrategy == EvictionStrategy.FIFO) {
            this.dataStoreMap.remove(this.evictionQueue.poll());
        } else if (evictionStrategy == EvictionStrategy.RANDOM) {
            int randomIndex = Cache.random.nextInt(evictionQueue.size());
            String key = this.evictionQueue.get(randomIndex);

            this.evictionQueue.remove(key);
            this.dataStoreMap.remove(key);
        } else {
            Collection<Bucket> buckets = this.dataStoreMap.getBuckets();
            final Comparator<Bucket> bucketComparator = getBucketComparator(evictionStrategy);
            Optional<Bucket> bucketHolder = buckets.stream().min(bucketComparator);

            if (bucketHolder.isPresent()) {
                DataStore dataStore = bucketHolder.get().getDataStore();

                if (!dataStore.isEmpty()) {
                    Comparator<Map.Entry<String, CacheableWrapper>> cachedDataComparator = getCachedDataComparator(evictionStrategy);
                    Optional<Map.Entry<String, CacheableWrapper>> cachedRecordHolder = dataStore.entrySet().stream().min(cachedDataComparator);

                    Map.Entry<String, CacheableWrapper> cachedRecordEntry = cachedRecordHolder.get();
                    String key = cachedRecordEntry.getKey();

                    this.evictionQueue.remove(key);
                    this.dataStoreMap.remove(key);
                }
            } else {
                throw new RuntimeException("unexpected scenario - this is not supposed to happen");
            }
        }
    }

    private Comparator<Map.Entry<String, CacheableWrapper>> getCachedDataComparator(EvictionStrategy evictionStrategy) {
        if (evictionStrategy == EvictionStrategy.LRU) {
            return (entry1, entry2) -> Math.toIntExact(entry1.getValue().getLastAccessedTime() - entry2.getValue().getLastAccessedTime());
        } else { // evictionStrategy == EvictionStrategy.LFU
            return (entry1, entry2) -> Math.toIntExact(entry1.getValue().getAccessCount() - entry2.getValue().getAccessCount());
        }
    }

    private Comparator<Bucket> getBucketComparator(EvictionStrategy evictionStrategy) {
        if (evictionStrategy == EvictionStrategy.LRU) {
            return (bucket1, bucket2) -> Math.toIntExact(bucket1.getLastAccessedTime() - bucket2.getLastAccessedTime());
        } else { // evictionStrategy == EvictionStrategy.LFU
            return (bucket1, bucket2) -> Math.toIntExact(bucket1.getAccessCount() - bucket2.getAccessCount());
        }
    }

    private synchronized void invalidateCachedRecords() {
        if (this.evictionQueue.isEmpty() || this.dataStoreMap.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        Collection<Bucket> buckets = this.dataStoreMap.getBuckets();
        this.latch = new CountDownLatch(this.dataStoreMap.getBucketCount());

        for (Bucket bucket : buckets) {
            if (!bucket.isEmpty()) {
                executor.execute(() -> {
                    Iterator<String> iterator = bucket.getDataStore().keySet().iterator();

                    while (iterator.hasNext()) {
                        this.applyInvalidationStrategy(currentTime, iterator);
                    }

                    latch.countDown();
                });
            } else {
                latch.countDown();
            }
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException("unexpected error while waiting for completion");
        }
    }

    private synchronized void applyInvalidationStrategy(long currentTime, Iterator<String> iterator) {
        String key = iterator.next();
        CacheableWrapper record = this.dataStoreMap.get(key);
        InvalidationStrategy invalidationStrategy = this.cachePolicy.getInvalidationStrategy();

        if (invalidationStrategy == InvalidationStrategy.TIME_TO_LIVE) {
            if (currentTime - record.getCreatedTime() > this.cachePolicy.getInvalidationLifeTime()) {
                this.delete(key, iterator);
            }
        } else if (invalidationStrategy == InvalidationStrategy.TIME_BASED) {
            if (currentTime - record.getLastAccessedTime() > this.cachePolicy.getInvalidationLifeTime()) {
                this.delete(key, iterator);
            }
        } else if (invalidationStrategy == InvalidationStrategy.REFRESH) {
            if (currentTime - record.getCreatedTime() > cachePolicy.getInvalidationLifeTime()) {
                this.evictionQueue.remove(key);
                this.dataStoreMap.remove(key);

                Cacheable data = this.dataSource.load(key);

                if (!Util.usable(data)) {
                    return;
                }

                CacheableWrapper freshRecord = new CacheableWrapper(key, data);

                freshRecord.setAccessCount(record.getAccessCount() + 1);
                this.dataStoreMap.put(key, freshRecord);
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

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException("unexpected error while waiting for completion");
        }

        this.invalidationTimer.cancel();
        this.writeBehindTimer.cancel();
        this.dataStoreMap.clear();
        this.evictionQueue.clear();
        this.dataSyncQueue.clear();
    }

    @Override
    public String toString() {
        return "{" +
                "nm=" + this.name +
                "ds=" + this.dataStoreMap.getBuckets() +
                ", eq=" + this.evictionQueue +
                '}';
    }
}
