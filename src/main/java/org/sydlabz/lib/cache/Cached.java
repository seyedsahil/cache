package org.sydlabz.lib.cache;

final class Cached {
    private final String recordKey;
    private final Cacheable cachedData;

    private final long createdTime;
    private long lastAccessedTime;
    private long accessCount;

    Cached(final String recordKey, final Cacheable cachedData) {
        this.recordKey = recordKey;
        this.cachedData = cachedData;
        this.createdTime = System.currentTimeMillis();
        this.lastAccessedTime = createdTime;
        this.accessCount = 1L;
    }

    public String getRecordKey() {
        return this.recordKey;
    }

    public Cacheable getCachedData() {
        return this.cachedData;
    }

    public long getCreatedTime() {
        return this.createdTime;
    }

    public long getLastAccessedTime() {
        return this.lastAccessedTime;
    }

    public long getAccessCount() {
        return this.accessCount;
    }

    public void setAccessCount(long accessCount) {
        this.accessCount = accessCount;
    }

    void incrementAccessCount() {
        this.accessCount++;
    }

    void setLastAccessedTime() {
        this.lastAccessedTime = System.currentTimeMillis();
    }
}
