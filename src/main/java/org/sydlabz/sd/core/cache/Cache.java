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
 *
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
    private final Source dataSource;
    private final LinkedList<String> evictionQueue;
    private final LinkedList<CacheableWrapper> writeBehindQueue;
    private final Timer invalidationTimer;
    private final Timer writeBehindTimer;
    private final Policy cachePolicy;
    private boolean active;

    private Cache(Source dataSource) {
        this(dataSource, Policy.getDefaultPolicy());
    }

    private Cache(Source dataSource, Policy cachePolicy) {
        this.cachedDataStore = new ConcurrentHashMap<>();
        this.dataSource = dataSource;
        this.cachePolicy = cachePolicy;
        this.evictionQueue = new LinkedList<>();
        this.writeBehindQueue = new LinkedList<>();
        this.invalidationTimer = new Timer();
        this.invalidationTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                invalidate();
            }
        }, this.cachePolicy.getDelay(), this.cachePolicy.getInvalidationFrequency());
        this.writeBehindTimer = new Timer();

        if (this.cachePolicy.getWriteStrategy() == Write.BEHIND) {
            this.writeBehindTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    writeBehind();
                }
            }, this.cachePolicy.getDelay(), this.cachePolicy.getWriteBehindFrequency());
        }

        this.active = true;
    }

    public static Cache getInstance(Source source) {
        if (Cache.instance == null) {
            Cache.instance = new Cache(source);
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

        if (this.cachedDataStore.containsKey(key)) {
            debug("action=get key={0} status=hit", key);

            CacheableWrapper wrapper = this.cachedDataStore.get(key);

            wrapper.accessCount++;

            wrapper.lastAccessedTime = System.currentTimeMillis();

            return Optional.of(wrapper.dataRecord);
        }

        debug("action=get key={0} status=miss", key);

        debug("action=get key={0} event=load source={1}", key, this.dataSource.getName());

        Cacheable dataRecord = this.dataSource.load(key);

        if (dataRecord == null) {
            debug("action=get key={0} event=load source={1} status=miss", key, this.dataSource.getName());

            return Optional.empty();
        }

        if (this.cachedDataStore.size() == this.cachePolicy.getCacheSize()) {
            debug("action=get key={0} event=evict reason=full strategy={1}", key, this.cachePolicy.getEvictionStrategy());

            this.evict();
        }

        debug("action=get key={0} event=store status=success", key);

        this.cachedDataStore.put(key, new CacheableWrapper(key, dataRecord));
        this.evictionQueue.offer(key);

        return Optional.of(dataRecord);
    }

    public synchronized void put(String key, Cacheable dataRecord) {
        if (!this.active) {
            throw new RuntimeException("Cache is not active");
        }

        CacheableWrapper cachedRecord = this.cachedDataStore.get(key);
        CacheableWrapper freshRecord = new CacheableWrapper(key, dataRecord);

        if (cachedRecord != null) {
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

    private synchronized void applyWriteStrategy(CacheableWrapper record) {
        String key = record.key;

        debug("action=put key={0} event=write status=progreess source={1}", key, this.dataSource.getName());

        Write writeStrategy = this.cachePolicy.getWriteStrategy();

        if (writeStrategy == Write.THROUGH) {
            this.dataSource.save(key, record.dataRecord);

            debug("action=put key={0} event=write status=success source={1} strategy={2}", key, this.dataSource.getName(), writeStrategy);
        } else if (writeStrategy == Write.BEHIND) {
            this.writeBehindQueue.add(record);

            debug("action=put key={0} event=write status=success source={1} strategy={2}", key, this.dataSource.getName(), writeStrategy);
        }
    }

    private synchronized void writeBehind() {
        Write writeStrategy = this.cachePolicy.getWriteStrategy();

        if (this.writeBehindQueue.isEmpty()) {
            debug("action=sync event=save status=skipped reason=noData source={0} strategy={1}", this.dataSource.getName(), writeStrategy);

            return;
        }

        this.writeBehindQueue.forEach(record -> {
            debug("action=sync key={0} event=save status=success source={0} source={1} strategy={2}", record.key, this.dataSource.getName(), writeStrategy);

            this.dataSource.save(record.key, record.dataRecord);
        });

        debug("action=sync event=save status=success source={0} strategy={1}", this.dataSource.getName(), writeStrategy);

        this.writeBehindQueue.clear();
    }

    private synchronized void evict() {
        if (this.evictionQueue.isEmpty() || this.cachedDataStore.isEmpty()) {
            debug("action=evict status=skipped reason=empty");

            return;
        }

        Eviction evictionStrategy = this.cachePolicy.getEvictionStrategy();

        if (evictionStrategy == Eviction.FIFO) {
            String key = this.evictionQueue.poll();

            this.cachedDataStore.remove(key);

            debug("action=get key={0} event=evict status=success strategy={1}", key, evictionStrategy);
        } else if (evictionStrategy == Eviction.LRU) {
            Map.Entry<String, CacheableWrapper> record = this.cachedDataStore.entrySet().stream().min((entry1, entry2) -> Math.toIntExact(entry1.getValue().lastAccessedTime - entry2.getValue().lastAccessedTime)).get();

            String key = record.getKey();

            this.evictionQueue.remove(key);
            this.cachedDataStore.remove(key);

            debug("action=get key={0} event=evict status=success strategy={1}", key, evictionStrategy);
        } else if (evictionStrategy == Eviction.LFU) {
            Map.Entry<String, CacheableWrapper> record = this.cachedDataStore.entrySet().stream().min((entry1, entry2) -> Math.toIntExact(entry1.getValue().accessCount - entry2.getValue().accessCount)).get();

            String key = record.getKey();

            this.evictionQueue.remove(key);
            this.cachedDataStore.remove(key);

            debug("action=get key={0} event=evict status=success strategy={1}", key, evictionStrategy);
        } else if (evictionStrategy == Eviction.RANDOM) {
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

        Invalidation invalidationStrategy = this.cachePolicy.getInvalidationStrategy();

        if (invalidationStrategy == Invalidation.TTL) {
            if (currentTime - record.createdTime > this.cachePolicy.getInvalidationLifeTime()) {
                iterator.remove();

                this.evictionQueue.remove(key);

                debug("action=invalidate key={0} status=success strategy={1}", key, invalidationStrategy);

                invalidated = true;
            }
        } else if (invalidationStrategy == Invalidation.TIME_BASED) {
            if (currentTime - record.lastAccessedTime > this.cachePolicy.getInvalidationLifeTime()) {

                iterator.remove();

                this.evictionQueue.remove(key);

                debug("action=invalidate key={0} status=success strategy={1}", key, invalidationStrategy);

                invalidated = true;
            }
        } else if (invalidationStrategy == Invalidation.REFRESH) {
            if (currentTime - record.createdTime > cachePolicy.getInvalidationLifeTime()) {
                debug("status: invalidation, key: " + key + ", mode: refresh");

                iterator.remove();

                this.evictionQueue.remove(key);

                debug("action=invalidate key={0} status=success strategy={1}", key, invalidationStrategy);

                debug("action=invalidate key={0} event=refresh source={1} strategy={2}", this.dataSource.getName(), invalidationStrategy);

                Cacheable dataRecord = this.dataSource.load(key);

                if (dataRecord == null) {
                    debug("action=invalidate key={0} event=refresh status=skipped reason=unavailable source={1} strategy={2}", this.dataSource.getName(), invalidationStrategy);

                    return;
                }

                CacheableWrapper freshRecord = new CacheableWrapper(key, dataRecord);

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

    public synchronized void shutdown() {
        this.active = false;
        this.invalidationTimer.cancel();
        this.cachedDataStore.clear();
        this.evictionQueue.clear();
    }

    @Override
    public String toString() {
        return "{" +
                "ds=" + this.cachedDataStore.values() +
                ", eq=" + this.evictionQueue +
                '}';
    }

    private static class CacheableWrapper {
        private final Cacheable dataRecord;
        private final long createdTime;
        private final String key;
        private long lastAccessedTime;
        private long accessCount;

        public CacheableWrapper(String key, Cacheable dataRecord) {
            this.dataRecord = dataRecord;
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
