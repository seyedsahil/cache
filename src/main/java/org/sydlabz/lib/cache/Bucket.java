package org.sydlabz.lib.cache;

import java.util.Iterator;
import java.util.Map;

final class Bucket {
    private final int bucketKey;
    private final DataStore dataStore;
    private final transient CacheConfiguration cacheConfiguration;
    private final transient DataSource dataSource;

    Bucket(final int bucketKey, final CacheConfiguration cacheConfiguration, final DataSource dataSource) {
        this.bucketKey = bucketKey;
        this.dataStore = new DataStore();
        this.cacheConfiguration = cacheConfiguration;
        this.dataSource = dataSource;
    }

    int getBucketKey() {
        return this.bucketKey;
    }

    int size() {
        return this.dataStore.size();
    }

    boolean isEmpty() {
        return this.dataStore.isEmpty();
    }

    public void doInvalidate(final long currentTime) {
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

    Cached get(String recordKey) {
        return this.dataStore.get(recordKey);
    }

    void put(String recordKey, Cached cachedRecord) {
        this.dataStore.put(recordKey, cachedRecord);
    }

    void remove(String recordKey) {
        this.dataStore.remove(recordKey);
    }
}
