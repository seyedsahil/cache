package org.sydlabz.sd.core.cache;

class Bucket {
    private int key;
    private DataStore dataStore;
    private long lastAccessedTime;
    private long accessCount;

    Bucket(int key) {
        this.key = key;
        this.dataStore = new DataStore();
        this.lastAccessedTime = System.currentTimeMillis();
        this.accessCount = 0;
    }

    int getKey() {
        return this.key;
    }

    void setKey(int key) {
        this.key = key;
    }

    DataStore getDataStore() {
        return this.dataStore;
    }

    void setDataStore(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    long getLastAccessedTime() {
        return this.lastAccessedTime;
    }

    void setLastAccessedTime() {
        this.lastAccessedTime = System.currentTimeMillis();
    }

    long getAccessCount() {
        return this.accessCount;
    }

    void incrementAccessCount() {
        this.accessCount++;
    }

    int size() {
        return this.dataStore.size();
    }

    boolean isEmpty() {
        return this.dataStore.isEmpty();
    }

    @Override
    public String toString() {
        return "{" +
                "k=" + key +
                ", ds=" + dataStore +
                ", at=" + lastAccessedTime +
                ", ac=" + accessCount +
                '}';
    }
}
