package org.sydlabz.sd.core.cache;

import static javax.management.timer.Timer.ONE_MINUTE;

/**
 * @author seyedsahil
 * @version 1.0
 */
public final class CachePolicy {
    private static CachePolicy defaultCachePolicy;
    private final long cacheSize;
    private long delay;
    private long invalidationFrequency;
    private InvalidationStrategy invalidationStrategy;
    private long invalidationLifeTime;
    private EvictionStrategy evictionStrategy;
    private WriteStrategy writeStrategy;
    private long writeBehindFrequency;

    private CachePolicy() {
        this.cacheSize = 16;
        this.invalidationFrequency = 5 * ONE_MINUTE;
        this.delay = ONE_MINUTE;
        this.invalidationStrategy = InvalidationStrategy.TIME_TO_LIVE;
        this.invalidationLifeTime = 3 * ONE_MINUTE;
        this.evictionStrategy = EvictionStrategy.FIFO;
        this.writeStrategy = WriteStrategy.WRITE_THROUGH;
        this.writeBehindFrequency = 5 * ONE_MINUTE;
    }

    public static CachePolicy getDefaultPolicy() {
        if (CachePolicy.defaultCachePolicy == null) {
            CachePolicy.defaultCachePolicy = new CachePolicy();
        }

        return CachePolicy.defaultCachePolicy;
    }

    public long getCacheSize() {
        return this.cacheSize;
    }

    public long getInvalidationFrequency() {
        return this.invalidationFrequency;
    }

    public long getDelay() {
        return this.delay;
    }

    public long getInvalidationLifeTime() {
        return this.invalidationLifeTime;
    }

    public InvalidationStrategy getInvalidationStrategy() {
        return this.invalidationStrategy;
    }

    public EvictionStrategy getEvictionStrategy() {
        return this.evictionStrategy;
    }

    public WriteStrategy getWriteStrategy() {
        return this.writeStrategy;
    }

    public long getWriteBehindFrequency() {
        return this.writeBehindFrequency;
    }

    public static class Builder {
        private final CachePolicy cachePolicy;

        public Builder() {
            this.cachePolicy = new CachePolicy();
        }

        public Builder delay(long delay) {
            this.cachePolicy.delay = delay;

            return this;
        }

        public Builder invalidationFrequency(long invalidationFrequency) {
            this.cachePolicy.invalidationFrequency = invalidationFrequency;

            return this;
        }

        public Builder invalidationStrategy(InvalidationStrategy invalidationStrategy) {
            this.cachePolicy.invalidationStrategy = invalidationStrategy;

            return this;
        }

        public Builder invalidationLifeTime(long invalidationLifeTime) {
            this.cachePolicy.invalidationLifeTime = invalidationLifeTime;

            return this;
        }

        public Builder evictionStrategy(EvictionStrategy evictionStrategy) {
            this.cachePolicy.evictionStrategy = evictionStrategy;

            return this;
        }

        public Builder writeStrategy(WriteStrategy writeStrategy) {
            this.cachePolicy.writeStrategy = writeStrategy;

            return this;
        }

        public Builder writeBehindFrequency(long writeBehindFrequency) {
            this.cachePolicy.writeBehindFrequency = writeBehindFrequency;

            return this;
        }

        public CachePolicy build() {
            return this.cachePolicy;
        }
    }
}
