package org.sydlabz.lib.cache;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

final class Bucket {
    private final DataStore dataStore;
    private final transient CacheConfiguration cacheConfiguration;
    private final transient DataSource dataSource;
    private final EvictionQueue evictionQueue;

    Bucket(final CacheConfiguration cacheConfiguration, final DataSource dataSource) {
        this.dataStore = new DataStore();
        this.cacheConfiguration = cacheConfiguration;
        this.dataSource = dataSource;
        this.evictionQueue = new EvictionQueue(cacheConfiguration.getEvictionStrategy());
    }

    int size() {
        return this.dataStore.size();
    }

    boolean isEmpty() {
        return this.dataStore.isEmpty();
    }

    public synchronized void doInvalidate(final long currentTime, BucketMap bucketMap) {
        Iterator<Map.Entry<String, Cached>> iterator = this.dataStore.entrySet().iterator();
        int sizeBefore = this.dataStore.size();

        while (iterator.hasNext()) {
            invalidateRecord(currentTime, iterator);
        }

        bucketMap.decrementCountBy(sizeBefore - this.dataStore.size());
    }

    private void invalidateRecord(final long currentTime, final Iterator<Map.Entry<String, Cached>> iterator) {
        Map.Entry<String, Cached> cachedRecordEntry = iterator.next();
        Cached cachedRecord = cachedRecordEntry.getValue();
        InvalidationStrategy invalidationStrategy = this.cacheConfiguration.getInvalidationStrategy();

        if (InvalidationStrategy.TIME_TO_LIVE == invalidationStrategy) {
            if (currentTime - cachedRecord.getCreatedTime() > this.cacheConfiguration.getInvalidationLifeTime()) {
                this.evictionQueue.remove(cachedRecord);
                iterator.remove();
            }
        } else if (InvalidationStrategy.TIME_BASED == invalidationStrategy) {
            if (currentTime - cachedRecord.getLastAccessedTime() > this.cacheConfiguration.getInvalidationLifeTime()) {
                this.evictionQueue.remove(cachedRecord);
                iterator.remove();
            }
        } else if (InvalidationStrategy.REFRESH == invalidationStrategy) {
            if (currentTime - cachedRecord.getCreatedTime() > cacheConfiguration.getInvalidationLifeTime()) {
                String recordKey = cachedRecordEntry.getKey();

                this.evictionQueue.remove(cachedRecord);
                iterator.remove();

                Cacheable data = this.dataSource.load(recordKey);

                if (!Util.isUsable(data) && !this.cacheConfiguration.isCacheNullValues()) {
                    return;
                }

                Cached freshRecord = new Cached(recordKey, data);

                freshRecord.setAccessCount(cachedRecord.getAccessCount() + 1);
                this.evictionQueue.offer(freshRecord);
                this.dataStore.put(recordKey, freshRecord);
            }
        }
    }

    Cached getAndUpdate(final String recordKey) {
        Cached cachedRecord = this.dataStore.get(recordKey);

        if (Util.isUsable(cachedRecord)) {
            cachedRecord.incrementAccessCount();
            cachedRecord.setLastAccessedTime();

            this.evictionQueue.remove(cachedRecord);
            this.evictionQueue.offer(cachedRecord);
        }

        return cachedRecord;
    }

    public Cached getOnly(String recordKey) {
        return this.dataStore.get(recordKey);
    }

    synchronized void put(final String recordKey, final Cached cachedRecord, final AtomicLong cachedRecordsCount) {
        int sizeBefore = this.size();

        this.dataStore.put(recordKey, cachedRecord);

        this.evictionQueue.remove(cachedRecord);
        this.evictionQueue.offer(cachedRecord);

        cachedRecordsCount.getAndAdd(this.size() - sizeBefore);
    }

    synchronized void remove(final String recordKey, final AtomicLong cachedRecordsCount) {
        int sizeBefore = this.size();

        this.evictionQueue.remove(recordKey);

        this.dataStore.remove(recordKey);
        cachedRecordsCount.getAndAdd(this.size() - sizeBefore);
    }

    synchronized void evict(final AtomicLong cachedRecordsCount) {
        Cached cachedRecord = this.evictionQueue.poll();

        if (Util.isUsable(cachedRecord)) {
            this.dataStore.remove(cachedRecord.getRecordKey());
            cachedRecordsCount.getAndAdd(-1);
        }
    }
}
