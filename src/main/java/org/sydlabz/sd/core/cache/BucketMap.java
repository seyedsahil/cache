package org.sydlabz.sd.core.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.IntStream;

class BucketMap {
    private static final long RANGE = Integer.MAX_VALUE + 1L;

    private final TreeMap<Integer, Bucket> hashRing;
    private final int bucketCount;
    private int size;

    BucketMap(int bucketCount) {
        if (bucketCount <= 0 || BucketMap.RANGE % bucketCount != 0) {
            throw new IllegalArgumentException("bucketCount and range not compatible");
        }

        this.hashRing = new TreeMap<>();
        this.size = 0;
        this.bucketCount = bucketCount;

        addBuckets(bucketCount);
    }

    private synchronized void addBuckets(int size) {
        int interval = (int) (BucketMap.RANGE / bucketCount);

        IntStream.range(0, size).forEach(index -> this.addBucket(index * interval));
    }

    private synchronized void addBucket(int bucketKey) {
        this.hashRing.put(bucketKey, new Bucket(bucketKey));
    }

    private synchronized Bucket getBucket(String key) {
        int hashKey = Math.abs(Util.hash(key));

        if (this.hashRing.containsKey(hashKey)) {
            return this.hashRing.get(hashKey);
        }

        SortedMap<Integer, Bucket> hashRingTail = this.hashRing.tailMap(hashKey);
        hashKey = hashRingTail.isEmpty() ? this.hashRing.firstKey() : hashRingTail.firstKey();

        return this.hashRing.get(hashKey);
    }

    synchronized Collection<Bucket> getBuckets() {
        return Collections.unmodifiableCollection(this.hashRing.values());
    }

    synchronized CacheableWrapper get(String key) {
        Bucket bucket = this.getBucket(key);
        DataStore dataStore = bucket.getDataStore();
        CacheableWrapper cachedRecord = dataStore.get(key);

        if (Util.usable(cachedRecord)) {
            bucket.setLastAccessedTime();
            bucket.incrementAccessCount();
        }

        return cachedRecord;
    }

    synchronized void put(String key, CacheableWrapper cachedRecord) {
        Bucket bucket = this.getBucket(key);
        DataStore dataStore = bucket.getDataStore();
        int sizeBefore = bucket.size();

        dataStore.put(key, cachedRecord);
        bucket.setLastAccessedTime();
        bucket.incrementAccessCount();
        this.size += bucket.size() - sizeBefore;
    }

    synchronized long size() {
        return this.size;
    }

    synchronized boolean isEmpty() {
        return this.size == 0;
    }

    synchronized void remove(String key) {
        Bucket bucket = this.getBucket(key);
        DataStore dataStore = bucket.getDataStore();
        int sizeBefore = bucket.size();

        dataStore.remove(key);

        this.size += bucket.size() - sizeBefore;
    }

    synchronized void clear() {
        this.size = 0;
        this.hashRing.clear();
        this.addBuckets(this.bucketCount);
    }

    int getBucketCount() {
        return this.bucketCount;
    }
}
