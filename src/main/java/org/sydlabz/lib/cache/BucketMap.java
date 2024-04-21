package org.sydlabz.lib.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

final class BucketMap {
    private final TreeMap<Integer, Bucket> hashRing;
    private final int bucketCount;
    private final transient CacheConfiguration cacheConfiguration;
    private final transient DataSource dataSource;

    private AtomicLong cachedRecordsCount;

    BucketMap(final int bucketCount, final CacheConfiguration cacheConfiguration, final DataSource dataSource) {
        this.validate(bucketCount);

        this.bucketCount = bucketCount;
        this.hashRing = new TreeMap<>();
        this.cacheConfiguration = cacheConfiguration;
        this.dataSource = dataSource;
        this.cachedRecordsCount = new AtomicLong(0L);

        this.createAndAttachBuckets();
    }

    private void validate(final int bucketCount) {
        if (bucketCount <= 0 || Util.HASH_KEY_RANGE % bucketCount != 0) {
            throw new IllegalArgumentException("bucketCount and range not compatible");
        }
    }

    private void createAndAttachBuckets() {
        int interval = (int) (Util.HASH_KEY_RANGE / bucketCount);

        IntStream.range(0, this.bucketCount).forEach(index -> {
            int bucketKey = index * interval;
            this.hashRing.put(bucketKey, new Bucket(cacheConfiguration, dataSource));
        });
    }

    private Bucket getBucket(final String key) {
        int hashKey = Util.hash(key);
        Bucket bucket = this.hashRing.get(hashKey);

        if (Util.isUsable(bucket)) {
            return bucket;
        }

        SortedMap<Integer, Bucket> hashRingTail = this.hashRing.tailMap(hashKey);
        int nearestHashKey = hashRingTail.isEmpty() ? this.hashRing.firstKey() : hashRingTail.firstKey();

        return this.hashRing.get(nearestHashKey);
    }

    Cached getAndUpdate(final String recordKey) {
        return this.getBucket(recordKey).getAndUpdate(recordKey);
    }

    public Cached getOnly(String recordKey) {
        return this.getBucket(recordKey).getOnly(recordKey);
    }

    void put(final String recordKey, final Cached cachedRecord) {
        this.getBucket(recordKey).put(recordKey, cachedRecord, this.cachedRecordsCount);
    }

    void remove(final String recordKey) {
        this.getBucket(recordKey).remove(recordKey, this.cachedRecordsCount);
    }

    synchronized void clear() {
        this.cachedRecordsCount = new AtomicLong(0L);
        this.hashRing.clear();
        this.createAndAttachBuckets();
    }

    boolean isEmpty() {
        return cachedRecordsCount.get() == 0L;
    }

    long getCachedRecordsCount() {
        return cachedRecordsCount.get();
    }

    Collection<Bucket> getBuckets() {
        return Collections.unmodifiableCollection(this.hashRing.values());
    }

    int getBucketCount() {
        return this.bucketCount;
    }

    synchronized void decrementCountBy(int delta) {
        this.cachedRecordsCount.getAndAdd(-delta);
    }

    synchronized void doEviction() {
        for (Bucket bucket : this.hashRing.values()) {
            bucket.evict(this.cachedRecordsCount);
        }
    }
}
