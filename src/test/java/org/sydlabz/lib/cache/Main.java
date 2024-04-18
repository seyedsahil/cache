package org.sydlabz.lib.cache;

import javax.management.timer.Timer;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class Main {
    private static final int CACHE_SIZE = 10000000;
    private static final int THREAD_COUNT = 100;
    private static final int KEY_SIZE = 16;

    public static void main(String[] args) {
        test1();
    }

    private static void test1() {
        CacheConfiguration cacheConfiguration = new CacheConfiguration.Builder()
                .initialInvalidationDelay(Timer.ONE_SECOND * 4)
                .invalidationLifeTime(200)
                .invalidationFrequency(Timer.ONE_HOUR)
                .cacheSize(CACHE_SIZE).build();
        TestDataSource dataSource = new TestDataSource();
        Cache cache = new Cache("test-cache", dataSource, cacheConfiguration);
        Vector<String> keyStore = new Vector<>();
        double[] time;

        println("Thread Count: " + THREAD_COUNT);
        println("Cache Size: " + CACHE_SIZE);
        println("Key Size: " + KEY_SIZE);

        time = doPutOperations(cache, keyStore);
        System.out.println("Cached Records Count after Put Finishes (Invalidation Executed In Between): " + cache.getSize());

        println("Total of Cache.put(key): " + time[2] + " nanoseconds / " + time[3] + " milliseconds");
        println("Average of Cache.put(key): " + time[0] + " nanoseconds / " + time[1] + " milliseconds");

        time = doGetOperations(cache, keyStore);

        println("Total of Cache.get(key): " + time[2] + " nanoseconds / " + time[3] + " milliseconds");
        println("Average of Cache.get(key): " + time[0] + " nanoseconds / " + time[1] + " milliseconds");

        time = doRemoveOperations(cache, keyStore);

        println("Total of Cache.remove(key): " + time[2] + " nanoseconds / " + time[3] + " milliseconds");
        println("Average of Cache.remove(key): " + time[0] + " nanoseconds / " + time[1] + " milliseconds");

        cache.shutdown();
    }

    private static double[] doPutOperations(Cache cache, Vector<String> keyStore) {
        return executeTest(() -> {
            String key = randomString(KEY_SIZE);

            keyStore.add(key);

            return timeOf(() -> cache.put(key, new TestData(randomString(512))));
        });
    }

    private static double[] doGetOperations(Cache cache, Vector<String> keyStore) {
        Random random = new Random();

        return executeTest(() -> {
            String key = keyStore.get(random.nextInt(CACHE_SIZE));

            return timeOf(() -> cache.get(key));
        });
    }

    private static double[] doRemoveOperations(Cache cache, Vector<String> keyStore) {
        Random random = new Random();

        return executeTest(() -> {
            String key = keyStore.get(random.nextInt(CACHE_SIZE));

            return timeOf(() -> cache.remove(key));
        });
    }

    private static long timeOf(Timed action) {
        long start = System.nanoTime();

        action.execute();

        return System.nanoTime() - start;
    }

    private static void println(Object text) {
        System.out.println(text);
    }

    private static String randomString(final int size) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        char[] randomString = new char[size];
        Random random = new Random();

        for (int i = 0; i < size; i++) {
            int index = random.nextInt(characters.length());

            randomString[i] = characters.charAt(index);
        }

        return new String(randomString);
    }

    private static double[] executeTest(Scenario<Long> scenario) {
        AtomicLong accumulatedTime = new AtomicLong(0L);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        final int limit = CACHE_SIZE / THREAD_COUNT;
        Thread[] threads = new Thread[THREAD_COUNT];

        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i] = new Thread(() -> {
                int j = 0;

                while (j < limit) {
                    long time = scenario.execute();

                    accumulatedTime.getAndAdd(time);
                    j++;
                }

                latch.countDown();
            });
            threads[i].start();
        }

        try {
            latch.await();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }

        double[] time = new double[4];
        double doubleValue = accumulatedTime.get() + 0.0;

        time[0] = doubleValue / CACHE_SIZE;
        time[1] = time[0] / 1000000;
        time[2] = doubleValue;
        time[3] = doubleValue / 1000000 / THREAD_COUNT;

        return time;
    }
}
