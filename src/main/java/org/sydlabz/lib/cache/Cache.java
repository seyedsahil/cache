package org.sydlabz.lib.cache;

import java.util.LinkedList;
import java.util.Optional;
import java.util.Timer;

public final class Cache {
    private final String name;
    private final CacheConfiguration cacheConfiguration;

    private final BucketMap dataStore;
    private final DataSource dataSource;
    private transient Timer invalidationTimer;
    private transient InvalidationTask invalidationTask;

    private LinkedList<DataSourceItem> dataSyncQueue;
    private transient Timer dataSyncTimer;
    private transient DataSyncTask dataSyncTask;

    private boolean active;

    public Cache(final String name, final DataSource dataSource, final CacheConfiguration cacheConfiguration) {
        this.name = name;
        this.cacheConfiguration = cacheConfiguration;
        this.dataSource = dataSource;
        this.dataStore = new BucketMap(Util.BUCKET_COUNT, cacheConfiguration, dataSource);

        this.configureDataSync();
        this.configureInvalidation();

        this.active = true;
    }

    private void configureDataSync() {
        if (WriteStrategy.WRITE_BEHIND == this.cacheConfiguration.getWriteStrategy()) {
            this.dataSyncQueue = new LinkedList<>();
            this.dataSyncTimer = new Timer();
            this.dataSyncTask = new DataSyncTask(this.dataSyncQueue, this.dataSource);
            this.dataSyncTimer.schedule(this.dataSyncTask, this.cacheConfiguration.getInitialDataSyncDelay(), this.cacheConfiguration.getDataSyncFrequency());
        }
    }

    private void configureInvalidation() {
        if (this.cacheConfiguration.isInvalidationEnabled()) {
            this.invalidationTimer = new Timer();
            this.invalidationTask = new InvalidationTask(this.dataStore);
            this.invalidationTimer.schedule(this.invalidationTask, this.cacheConfiguration.getInitialInvalidationDelay(), this.cacheConfiguration.getInvalidationFrequency());
        }
    }

    public Optional<Cacheable> get(final String key) {
        this.validateState();
        this.validateKey(key);

        Cached cachedRecord = this.dataStore.get(key);

        if (Util.isUsable(cachedRecord)) {
            return getFromCache(cachedRecord);
        } else {
            return getFromDataSource(key);
        }
    }

    private Optional<Cacheable> getFromCache(final Cached cachedRecord) {
        cachedRecord.incrementAccessCount();
        cachedRecord.setLastAccessedTime();

        return Optional.of(cachedRecord.getCachedData());
    }

    private Optional<Cacheable> getFromDataSource(final String key) {
        Cacheable data = this.dataSource.load(key);

        if (!Util.isUsable(data) && !this.cacheConfiguration.isCacheNullValues()) {
            return Optional.empty();
        }

        this.dataStore.put(key, new Cached(key, data));

        return Optional.of(data);
    }


    public void put(final String key, final Cacheable data) {
        this.validateState();
        this.validateKey(key);

        if (!Util.isUsable(data) && !this.cacheConfiguration.isCacheNullValues()) {
            return;
        }

        Cached freshRecord = new Cached(key, data);
        Cached cachedRecord = this.dataStore.get(key);
        boolean isUpdate = false;

        if (Util.isUsable(cachedRecord)) {
            isUpdate = true;
            freshRecord.setAccessCount(cachedRecord.getAccessCount() + 1);
        }

        this.dataStore.put(key, freshRecord);

        executeWriteStrategy(key, isUpdate, freshRecord);
    }

    private void executeWriteStrategy(final String key, final boolean isUpdate, final Cached cachedRecord) {
        WriteStrategy writeStrategy = this.cacheConfiguration.getWriteStrategy();

        if (WriteStrategy.WRITE_THROUGH == writeStrategy) {
            if (isUpdate) {
                this.dataSource.update(key, cachedRecord.getCachedData());
            } else {
                this.dataSource.save(key, cachedRecord.getCachedData());
            }
        } else if (WriteStrategy.WRITE_BEHIND == writeStrategy) {
            this.dataSyncQueue.add(new DataSourceItem(cachedRecord, isUpdate));
        }
    }

    public void remove(final String key) {
        this.validateState();
        this.validateKey(key);

        this.dataStore.remove(key);
    }

    private void validateState() {
        if (!this.active) {
            throw new RuntimeException("get called with inactive cache");
        }
    }

    private void validateKey(String key) {
        if (!Util.isUsable(key)) {
            throw new NullKeyException("key undefined");
        }
    }

    public void shutdown() {
        this.shutdown(false);
    }

    public void shutdown(final boolean force) {
        WriteStrategy writeStrategy = this.cacheConfiguration.getWriteStrategy();

        if (force || WriteStrategy.WRITE_THROUGH == writeStrategy) {
            this.shutdownInternal();
        } else if (writeStrategy == WriteStrategy.WRITE_BEHIND) {
            this.dataSyncTask.doDataSync();
            this.shutdownInternal();
        }
    }

    private void shutdownInternal() {
        if (this.cacheConfiguration.isInvalidationEnabled()) {
            this.invalidationTask.await();
            this.invalidationTimer.cancel();
        }

        if (WriteStrategy.WRITE_BEHIND == this.cacheConfiguration.getWriteStrategy()) {
            this.dataSyncQueue.clear();
            this.dataSyncTimer.cancel();
        }

        this.dataStore.clear();
        this.active = false;
    }

    public String getName() {
        return this.name;
    }

    public long getSize() {
        return this.dataStore.getCachedRecordsCount();
    }

    int getBucketCount() {
        return this.dataStore.getBucketCount();
    }

    @Override
    public String toString() {
        return Util.toJson(this);
    }
}
