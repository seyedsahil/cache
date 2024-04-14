package org.sydlabz.sd.core.cache.test;

import org.sydlabz.sd.core.cache.*;

import javax.management.timer.Timer;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        TestDataSource dataSource = new TestDataSource();
        CachePolicy cachePolicy;

        cachePolicy = new CachePolicy.Builder()
                .cacheSize(2)
                .evictionStrategy(EvictionStrategy.FIFO)
                .invalidationStrategy(InvalidationStrategy.TIME_TO_LIVE)
                .writeStrategy(WriteStrategy.WRITE_THROUGH).build();

        run(cachePolicy, dataSource);

        cachePolicy = new CachePolicy.Builder()
                .cacheSize(2)
                .evictionStrategy(EvictionStrategy.LRU)
                .invalidationStrategy(InvalidationStrategy.TIME_BASED)
                .writeStrategy(WriteStrategy.WRITE_THROUGH).build();

        run(cachePolicy, dataSource);

        cachePolicy = new CachePolicy.Builder()
                .cacheSize(128)
                .evictionStrategy(EvictionStrategy.FIFO)
                .invalidationStrategy(InvalidationStrategy.TIME_TO_LIVE)
                .invalidationFrequency(10 * Timer.ONE_HOUR)
                .writeBehindFrequency(Timer.ONE_MINUTE / 2)
                .writeStrategy(WriteStrategy.WRITE_BEHIND).build();

        run(cachePolicy, dataSource);
    }

    private static void run(CachePolicy cachePolicy, DataSource dataSource) throws InterruptedException {
        Cache cache = new Cache("test", dataSource, cachePolicy);
        cache.put("98", new TestData("ha ha"));
        cache.put("70", new TestData("he he"));
        cache.put("70", new TestData("hu hu"));
        System.out.println(cache);

        Thread.sleep(cachePolicy.getDelay() + cachePolicy.getWriteBehindFrequency() + Timer.ONE_MINUTE);

        System.out.println(cache.get("128").orElse(null));
        System.out.println(cache.get("60").orElse(null));

        Thread.sleep(1500);

        System.out.println(cache.get("60").orElse(null));

        System.out.println(cache);

        cache.put("128", new TestData("ABCD"));
        cache.put("60", new TestData("PQRS"));

        Thread.sleep(3000);

        System.out.println(cache.get("128").orElse(null));
        System.out.println(cache.get("60").orElse(null));

        Thread.sleep(2000);

        System.out.println(cache.get("60").orElse(null));

        System.out.println(cache);

        System.out.println(cache.get("46").orElse(null));

        System.out.println(cache);

        Thread.sleep(2000);

        System.out.println(cache.get("51").orElse(null));

        cache.put("51", new TestData("Hey"));

        System.out.println(cache);

        Thread.sleep(cachePolicy.getDelay() + cachePolicy.getInvalidationFrequency());

        System.out.println(cache);

        cache.shutdown(false);
    }
}