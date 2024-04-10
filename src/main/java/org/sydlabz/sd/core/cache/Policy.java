package org.sydlabz.sd.core.cache;

import static javax.management.timer.Timer.ONE_MINUTE;

/**
 * @author seyedsahil
 * @version 1.0
 */
public final class Policy {
    private static Policy defaultPolicy;
    private final long cacheSize;
    private long delay;
    private long invalidationFrequency;
    private Invalidation invalidationStrategy;
    private long invalidationLifeTime;
    private Eviction evictionStrategy;
    private Write writeStrategy;
    private long writeBehindFrequency;

    private Policy() {
        this.cacheSize = 16;
        this.invalidationFrequency = 5 * ONE_MINUTE;
        this.delay = ONE_MINUTE;
        this.invalidationStrategy = Invalidation.TTL;
        this.invalidationLifeTime = 3 * ONE_MINUTE;
        this.evictionStrategy = Eviction.FIFO;
        this.writeStrategy = Write.THROUGH;
        this.writeBehindFrequency = 5 * ONE_MINUTE;
    }

    public static Policy getDefaultPolicy() {
        if (Policy.defaultPolicy == null) {
            Policy.defaultPolicy = new Policy();
        }

        return Policy.defaultPolicy;
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

    public Invalidation getInvalidationStrategy() {
        return this.invalidationStrategy;
    }

    public Eviction getEvictionStrategy() {
        return this.evictionStrategy;
    }

    public Write getWriteStrategy() {
        return this.writeStrategy;
    }

    public long getWriteBehindFrequency() {
        return this.writeBehindFrequency;
    }

    public static class Builder {
        private final Policy policy;

        public Builder() {
            this.policy = new Policy();
        }

        public Builder delay(long delay) {
            this.policy.delay = delay;

            return this;
        }

        public Builder invalidationFrequency(long invalidationFrequency) {
            this.policy.invalidationFrequency = invalidationFrequency;

            return this;
        }

        public Builder invalidationStrategy(Invalidation invalidationStrategy) {
            this.policy.invalidationStrategy = invalidationStrategy;

            return this;
        }

        public Builder invalidationLifeTime(long invalidationLifeTime) {
            this.policy.invalidationLifeTime = invalidationLifeTime;

            return this;
        }

        public Builder evictionStrategy(Eviction evictionStrategy) {
            this.policy.evictionStrategy = evictionStrategy;

            return this;
        }

        public Builder writeStrategy(Write writeStrategy) {
            this.policy.writeStrategy = writeStrategy;

            return this;
        }

        public Builder writeBehindFrequency(long writeBehindFrequency) {
            this.policy.writeBehindFrequency = writeBehindFrequency;

            return this;
        }

        public Policy build() {
            return this.policy;
        }
    }
}
