package org.sydlabz.sd.core.cache;

import java.text.MessageFormat;
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

    private static Cache instance;

    private final Map<String, CacheableWrapper> cachedDataStore;
    private final DataSource dataSource;
    private final LinkedList<String> evictionQueue;
    private final LinkedList<CacheableWrapper> writeBehindQueue;
    private final Timer invalidationTimer;
    private final Timer writeBehindTimer;
    private final CachePolicy cachePolicy;
    private boolean active;

    private Cache(DataSource dataSource) {
        this(dataSource, CachePolicy.getDefaultPolicy());
    }

    private Cache(DataSource dataSource, CachePolicy cachePolicy) {
        this.cachedDataStore = new ConcurrentHashMap<>();
        this.dataSource = dataSource;
        this.cachePolicy = cachePolicy;

        this.evictionQueue = new LinkedList<>();
        this.writeBehindQueue = new LinkedList<>();

        this.invalidationTimer = new Timer();
        this.writeBehindTimer = new Timer();

        this.invalidationTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                invalidate();
            }
        }, this.cachePolicy.getDelay(), this.cachePolicy.getInvalidationFrequency());

        if (this.cachePolicy.getWriteStrategy() == WriteStrategy.BEHIND) {
            this.writeBehindTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    writeBehind();
                }
            }, this.cachePolicy.getDelay(), this.cachePolicy.getWriteBehindFrequency());
        }

        this.active = true;
    }

    public static Cache getInstance(DataSource dataSource) {
        if (Cache.instance == null) {
            Cache.instance = new Cache(dataSource);
        }

        return Cache.instance;
    }

    private static void debug(String text, Object... args) {
        if (DEBUG_MODE) {
            System.out.println(MessageFormat.format(text, args));
        }
    }

    public synchronized Optional<Cacheable> get(final String key) {
        if (!this.active) {
            throw new RuntimeException("Cache is not active");
        }

        CacheableWrapper cachedRecord = this.cachedDataStore.get(key);

        if (Util.usable(cachedRecord)) {
            debug("action=get key={0} status=hit", key);

            cachedRecord.accessCount++;
            cachedRecord.lastAccessedTime = System.currentTimeMillis();

            return Optional.of(cachedRecord.data);
        }

        debug("action=get key={0} status=miss", key);
        debug("action=get key={0} event=load source={1}", key, this.dataSource.getName());

        Cacheable data = this.dataSource.load(key);

        if (!Util.usable(data)) {
            debug("action=get key={0} event=load source={1} status=miss", key, this.dataSource.getName());

            return Optional.empty();
        }

        if (full()) {
            debug("action=get key={0} event=evict reason=full strategy={1}", key, this.cachePolicy.getEvictionStrategy());

            this.evict();
        }

        debug("action=get key={0} event=store status=success", key);

        this.cachedDataStore.put(key, new CacheableWrapper(key, data));
        this.evictionQueue.offer(key);

        return Optional.of(data);
    }

    private boolean full() {
        return this.cachedDataStore.size() == this.cachePolicy.getCacheSize();
    }

    public synchronized void put(String key, Cacheable data) {
        if (!this.active) {
            throw new RuntimeException("Cache is not active");
        }

        CacheableWrapper freshRecord = new CacheableWrapper(key, data);
        CacheableWrapper cachedRecord = this.cachedDataStore.get(key);

        if (Util.usable(cachedRecord)) {
            debug("action=put key={0} event=check status=present source=cache", key);

            freshRecord.accessCount = cachedRecord.accessCount;

            this.evictionQueue.remove(key);
        } else {
            debug("action=put key={0} event=check status=notFound source=cache", key);
        }

        this.cachedDataStore.put(key, freshRecord);
        this.evictionQueue.offer(key);

        debug("action=put key={0} event=refresh status=success", key);

        applyWriteStrategy(freshRecord);
    }

    private synchronized void applyWriteStrategy(CacheableWrapper cachedRecord) {
        String key = cachedRecord.key;

        debug("action=put key={0} event=write status=progress source={1}", key, this.dataSource.getName());

        WriteStrategy writeStrategy = this.cachePolicy.getWriteStrategy();

        if (writeStrategy == WriteStrategy.THROUGH) {
            this.dataSource.save(key, cachedRecord.data);

            debug("action=put key={0} event=write status=success source={1} strategy={2}", key, this.dataSource.getName(), writeStrategy);
        } else if (writeStrategy == WriteStrategy.BEHIND) {
            this.writeBehindQueue.add(cachedRecord);

            debug("action=put key={0} event=write status=success source={1} strategy={2}", key, this.dataSource.getName(), writeStrategy);
        }
    }

    private synchronized void writeBehind() {
        WriteStrategy writeStrategy = this.cachePolicy.getWriteStrategy();

        if (this.writeBehindQueue.isEmpty()) {
            debug("action=sync event=save status=skipped reason=noData source={0} strategy={1}", this.dataSource.getName(), writeStrategy);

            return;
        }

        this.writeBehindQueue.forEach(cachedRecord -> {
            debug("action=sync key={0} event=save status=success source={0} source={1} strategy={2}", cachedRecord.key, this.dataSource.getName(), writeStrategy);

            this.dataSource.save(cachedRecord.key, cachedRecord.data);
        });

        debug("action=sync event=save status=success source={0} strategy={1}", this.dataSource.getName(), writeStrategy);

        this.writeBehindQueue.clear();
    }

    private synchronized void evict() {
        if (this.evictionQueue.isEmpty() || this.cachedDataStore.isEmpty()) {
            debug("action=evict status=skipped reason=empty");

            return;
        }

        EvictionStrategy evictionStrategy = this.cachePolicy.getEvictionStrategy();

        if (evictionStrategy == EvictionStrategy.FIFO) {
            String key = this.evictionQueue.poll();

            this.cachedDataStore.remove(key);

            debug("action=get key={0} event=evict status=success strategy={1}", key, evictionStrategy);
        } else if (evictionStrategy == EvictionStrategy.LRU) {
            Map.Entry<String, CacheableWrapper> cachedRecord = this.cachedDataStore.entrySet().stream().min((entry1, entry2) -> Math.toIntExact(entry1.getValue().lastAccessedTime - entry2.getValue().lastAccessedTime)).get();

            String key = cachedRecord.getKey();

            this.evictionQueue.remove(key);
            this.cachedDataStore.remove(key);

            debug("action=get key={0} event=evict status=success strategy={1}", key, evictionStrategy);
        } else if (evictionStrategy == EvictionStrategy.LFU) {
            Map.Entry<String, CacheableWrapper> cachedRecord = this.cachedDataStore.entrySet().stream().min((entry1, entry2) -> Math.toIntExact(entry1.getValue().accessCount - entry2.getValue().accessCount)).get();

            String key = cachedRecord.getKey();

            this.evictionQueue.remove(key);
            this.cachedDataStore.remove(key);

            debug("action=get key={0} event=evict status=success strategy={1}", key, evictionStrategy);
        } else if (evictionStrategy == EvictionStrategy.RANDOM) {
            int randomIndex = Cache.random.nextInt(evictionQueue.size());
            String key = this.evictionQueue.get(randomIndex);

            this.cachedDataStore.remove(key);
            this.evictionQueue.remove(key);

            debug("action=get key={0} event=evict status=success strategy={1}", key, evictionStrategy);
        }
    }

    private synchronized void invalidate() {
        if (this.evictionQueue.isEmpty() || this.cachedDataStore.isEmpty()) {
            debug("action=invalidate status=skipped reason=empty");

            return;
        }

        debug("action=invalidate status=progress");

        long currentTime = System.currentTimeMillis();
        Iterator<String> iterator = this.cachedDataStore.keySet().iterator();

        while (iterator.hasNext()) {
            applyInvalidationStrategy(currentTime, iterator);
        }
    }

    private synchronized void applyInvalidationStrategy(long currentTime, Iterator<String> iterator) {
        String key = iterator.next();

        CacheableWrapper record = this.cachedDataStore.get(key);

        boolean invalidated = false;

        InvalidationStrategy invalidationStrategy = this.cachePolicy.getInvalidationStrategy();

        if (invalidationStrategy == InvalidationStrategy.TTL) {
            if (currentTime - record.createdTime > this.cachePolicy.getInvalidationLifeTime()) {
                iterator.remove();

                this.evictionQueue.remove(key);

                debug("action=invalidate key={0} status=success strategy={1}", key, invalidationStrategy);

                invalidated = true;
            }
        } else if (invalidationStrategy == InvalidationStrategy.TIME_BASED) {
            if (currentTime - record.lastAccessedTime > this.cachePolicy.getInvalidationLifeTime()) {
                iterator.remove();

                this.evictionQueue.remove(key);

                debug("action=invalidate key={0} status=success strategy={1}", key, invalidationStrategy);

                invalidated = true;
            }
        } else if (invalidationStrategy == InvalidationStrategy.REFRESH) {
            if (currentTime - record.createdTime > cachePolicy.getInvalidationLifeTime()) {
                iterator.remove();

                this.evictionQueue.remove(key);

                debug("action=invalidate key={0} status=success strategy={1}", key, invalidationStrategy);

                debug("action=invalidate key={0} event=refresh source={1} strategy={2}", this.dataSource.getName(), invalidationStrategy);

                Cacheable data = this.dataSource.load(key);

                if (!Util.usable(data)) {
                    debug("action=invalidate key={0} event=refresh status=skipped reason=unavailable source={1} strategy={2}", this.dataSource.getName(), invalidationStrategy);

                    return;
                }

                CacheableWrapper freshRecord = new CacheableWrapper(key, data);

                freshRecord.accessCount = record.accessCount;

                this.cachedDataStore.put(key, freshRecord);
                this.evictionQueue.offer(key);

                debug("action=invalidate key={0} event=refresh status=success source={1} strategy={2}", this.dataSource.getName(), invalidationStrategy);

                invalidated = true;
            }
        }

        if (!invalidated) {
            debug("action=invalidate status=skipped reason=notMet key={0}", key);
        }
    }

    public synchronized void shutdown(boolean force) {
        WriteStrategy writeStrategy = this.cachePolicy.getWriteStrategy();

        if (force || writeStrategy == WriteStrategy.THROUGH) {
            shutdown();
        } else if (writeStrategy == WriteStrategy.BEHIND) {
            this.writeBehind();

            shutdown();
        }
    }

    private synchronized void shutdown() {
        this.active = false;
        this.invalidationTimer.cancel();
        this.writeBehindTimer.cancel();
        this.cachedDataStore.clear();
        this.evictionQueue.clear();
        this.writeBehindQueue.clear();
    }

    @Override
    public String toString() {
        return "{" +
                "ds=" + this.cachedDataStore.values() +
                ", eq=" + this.evictionQueue +
                '}';
    }

    private static class CacheableWrapper {
        private final Cacheable data;
        private final long createdTime;
        private final String key;
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
}
