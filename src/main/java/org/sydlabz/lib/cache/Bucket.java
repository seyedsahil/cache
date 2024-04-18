package org.sydlabz.lib.cache;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

final class Bucket {
    private final DataStore dataStore;
    private final transient CacheConfiguration cacheConfiguration;
    private final transient DataSource dataSource;

    Bucket(final CacheConfiguration cacheConfiguration, final DataSource dataSource) {
        this.dataStore = new DataStore();
        this.cacheConfiguration = cacheConfiguration;
        this.dataSource = dataSource;
    }

    int size() {
        return this.dataStore.size();
    }

    boolean isEmpty() {
        return this.dataStore.isEmpty();
    }

    public synchronized void doInvalidate(final long currentTime) {
        Iterator<Map.Entry<String, Cached>> iterator = this.dataStore.entrySet().iterator();

        while (iterator.hasNext()) {
            invalidate(currentTime, iterator);
        }
    }

    private void invalidate(final long currentTime, final Iterator<Map.Entry<String, Cached>> iterator) {
        Map.Entry<String, Cached> cachedRecordEntry = iterator.next();
        Cached cachedRecord = cachedRecordEntry.getValue();
        InvalidationStrategy invalidationStrategy = this.cacheConfiguration.getInvalidationStrategy();

        if (InvalidationStrategy.TIME_TO_LIVE == invalidationStrategy) {
            if (currentTime - cachedRecord.getCreatedTime() > this.cacheConfiguration.getInvalidationLifeTime()) {
                iterator.remove();
            }
        } else if (InvalidationStrategy.TIME_BASED == invalidationStrategy) {
            if (currentTime - cachedRecord.getLastAccessedTime() > this.cacheConfiguration.getInvalidationLifeTime()) {
                iterator.remove();
            }
        } else if (InvalidationStrategy.REFRESH == invalidationStrategy) {
            if (currentTime - cachedRecord.getCreatedTime() > cacheConfiguration.getInvalidationLifeTime()) {
                String recordKey = cachedRecordEntry.getKey();
                Cacheable data = this.dataSource.load(recordKey);

                if (!Util.isUsable(data) && !this.cacheConfiguration.isCacheNullValues()) {
                    return;
                }

                Cached freshRecord = new Cached(recordKey, data);

                freshRecord.setAccessCount(cachedRecord.getAccessCount() + 1);
                this.dataStore.put(recordKey, freshRecord);
                iterator.remove();
            }
        }
    }

    Cached get(final String recordKey) {
        return this.dataStore.get(recordKey);
    }

    synchronized void put(final String recordKey, final Cached cachedRecord, final AtomicLong cachedRecordsCount) {
        int sizeBefore = this.size();

        this.dataStore.put(recordKey, cachedRecord);
        cachedRecordsCount.getAndAdd(this.size() - sizeBefore);
    }

    synchronized void remove(final String recordKey, final AtomicLong cachedRecordsCount) {
        int sizeBefore = this.size();

        this.dataStore.remove(recordKey);
        cachedRecordsCount.getAndAdd(this.size() - sizeBefore);
    }
}
