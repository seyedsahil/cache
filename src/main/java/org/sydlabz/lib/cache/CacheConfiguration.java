package org.sydlabz.lib.cache;

import javax.management.timer.Timer;

public final class CacheConfiguration {
    private static final CacheConfiguration defaultCacheConfiguration = new CacheConfiguration();

    private long cacheSize;

    private boolean invalidationEnabled;
    private long initialInvalidationDelay;
    private long invalidationLifeTime;
    private long invalidationFrequency;
    private InvalidationStrategy invalidationStrategy;

    private EvictionStrategy evictionStrategy;

    private long dataSyncFrequency;
    private long initialDataSyncDelay;
    private WriteStrategy writeStrategy;

    private boolean cacheNullValues;

    private CacheConfiguration() {
        this.cacheSize = 16;

        this.invalidationEnabled = true;
        this.initialInvalidationDelay = Timer.ONE_MINUTE;
        this.invalidationLifeTime = Timer.ONE_MINUTE;
        this.invalidationFrequency = Timer.ONE_MINUTE;
        this.invalidationStrategy = InvalidationStrategy.TIME_TO_LIVE;

        this.evictionStrategy = EvictionStrategy.FIFO;

        this.dataSyncFrequency = Timer.ONE_MINUTE;
        this.initialDataSyncDelay = Timer.ONE_MINUTE;
        this.writeStrategy = WriteStrategy.WRITE_THROUGH;

        this.cacheNullValues = false;

    }

    public static CacheConfiguration getDefaultConfiguration() {
        return CacheConfiguration.defaultCacheConfiguration;
    }

    public long getCacheSize() {
        return this.cacheSize;
    }

    public boolean isInvalidationEnabled() {
        return this.invalidationEnabled;
    }

    public long getInitialInvalidationDelay() {
        return this.initialInvalidationDelay;
    }

    public long getInvalidationLifeTime() {
        return this.invalidationLifeTime;
    }

    public long getInvalidationFrequency() {
        return this.invalidationFrequency;
    }

    public InvalidationStrategy getInvalidationStrategy() {
        return this.invalidationStrategy;
    }

    public EvictionStrategy getEvictionStrategy() {
        return this.evictionStrategy;
    }

    public long getDataSyncFrequency() {
        return this.dataSyncFrequency;
    }

    public long getInitialDataSyncDelay() {
        return this.initialDataSyncDelay;
    }

    public WriteStrategy getWriteStrategy() {
        return this.writeStrategy;
    }

    public boolean isCacheNullValues() {
        return this.cacheNullValues;
    }

    public static class Builder {
        private final CacheConfiguration cacheConfiguration;

        public Builder() {
            this.cacheConfiguration = new CacheConfiguration();
        }

        public Builder cacheSize(final int cacheSize) {
            this.cacheConfiguration.cacheSize = cacheSize;

            return this;
        }

        public Builder isInvalidationEnabled(final boolean invalidationEnabled) {
            this.cacheConfiguration.invalidationEnabled = invalidationEnabled;

            return this;
        }

        public Builder initialInvalidationDelay(final long initialInvalidationDelay) {
            this.cacheConfiguration.initialInvalidationDelay = initialInvalidationDelay;

            return this;
        }

        public Builder invalidationLifeTime(final long invalidationLifeTime) {
            this.cacheConfiguration.invalidationLifeTime = invalidationLifeTime;

            return this;
        }

        public Builder invalidationFrequency(final long invalidationFrequency) {
            this.cacheConfiguration.invalidationFrequency = invalidationFrequency;

            return this;
        }

        public Builder invalidationStrategy(final InvalidationStrategy invalidationStrategy) {
            this.cacheConfiguration.invalidationStrategy = invalidationStrategy;

            return this;
        }

        public Builder evictionStrategy(final EvictionStrategy evictionStrategy) {
            this.cacheConfiguration.evictionStrategy = evictionStrategy;

            return this;
        }

        public Builder dataSyncFrequency(final long dataSyncFrequency) {
            this.cacheConfiguration.dataSyncFrequency = dataSyncFrequency;

            return this;
        }

        public Builder initialDataSyncDelay(final long initialDataSyncDelay) {
            this.cacheConfiguration.initialDataSyncDelay = initialDataSyncDelay;

            return this;
        }

        public Builder writeStrategy(final WriteStrategy writeStrategy) {
            this.cacheConfiguration.writeStrategy = writeStrategy;

            return this;
        }

        public Builder cacheNullValues(final boolean cacheNulls) {
            this.cacheConfiguration.cacheNullValues = cacheNulls;

            return this;
        }

        public CacheConfiguration build() {
            return this.cacheConfiguration;
        }
    }
}
