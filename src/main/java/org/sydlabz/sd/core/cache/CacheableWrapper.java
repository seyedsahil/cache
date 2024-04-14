package org.sydlabz.sd.core.cache;

class CacheableWrapper {
    private String key;
    private Cacheable data;
    private long createdTime;
    private long lastAccessedTime;
    private long accessCount;

    CacheableWrapper(String key, Cacheable data) {
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

    void incrementAccessCount() {
        this.accessCount++;
    }

    String getKey() {
        return this.key;
    }

    void setKey(String key) {
        this.key = key;
    }

    Cacheable getData() {
        return this.data;
    }

    void setData(Cacheable data) {
        this.data = data;
    }

    long getCreatedTime() {
        return this.createdTime;
    }

    void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    long getLastAccessedTime() {
        return this.lastAccessedTime;
    }

    void setLastAccessedTime(long lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }

    long getAccessCount() {
        return this.accessCount;
    }

    void setAccessCount(long accessCount) {
        this.accessCount = accessCount;
    }
}