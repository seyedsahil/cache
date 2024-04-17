package org.sydlabz.lib.cache;

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
        CacheConfiguration cacheConfiguration = new CacheConfiguration.Builder().isInvalidationEnabled(false).cacheSize(CACHE_SIZE).build();
        TestDataSource dataSource = new TestDataSource();
        Cache cache = new Cache("test-cache", dataSource, cacheConfiguration);
        Vector<String> keyStore = new Vector<>();
        double[] averageTime;

        Thread[] threads = new Thread[THREAD_COUNT];
        AtomicLong accumulatedTime = new AtomicLong(0L);

        println("Thread Count: " + THREAD_COUNT);
        println("Cache Size: " + CACHE_SIZE);
        println("Key Size: " + KEY_SIZE);

        averageTime = doPutOperations(cache, threads, keyStore, accumulatedTime);

        println("Average of Cache.put(key): " + averageTime[0] + " nanoseconds / " + averageTime[1] + " milliseconds");

        averageTime = doGetOperations(cache, threads, keyStore, accumulatedTime);

        println("Average of Cache.get(key): " + averageTime[0] + " nanoseconds / " + averageTime[1] + " milliseconds");

        averageTime = doRemoveOperations(cache, threads, keyStore, accumulatedTime);

        println("Average of Cache.remove(key): " + averageTime[0] + " nanoseconds / " + averageTime[1] + " milliseconds");

        cache.shutdown();
    }

    private static double[] doPutOperations(Cache cache, Thread[] threads, Vector<String> keyStore, AtomicLong accumulatedTime) {
        accumulatedTime.set(0L);

        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        int limit = CACHE_SIZE / THREAD_COUNT;

        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i] = new Thread(() -> {
                int j = 0;

                while (j < limit) {
                    String key = randomString(KEY_SIZE);

                    keyStore.add(key);

                    KeyValue keyValue = new KeyValue(key, new TestData(randomString(512)));
                    long time = timeOf(() -> cache.put(keyValue.key(), keyValue.value()));

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

        double[] average = new double[2];

        average[0] = (accumulatedTime.get() + 0.0) / CACHE_SIZE;
        average[1] = average[0] / 1000000;

        accumulatedTime.set(0L);

        return average;
    }

    private static double[] doGetOperations(Cache cache, Thread[] threads, Vector<String> keyStore, AtomicLong accumulatedTime) {
        accumulatedTime.set(0L);

        Random r = new Random();
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        int limit = CACHE_SIZE / THREAD_COUNT;

        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i] = new Thread(() -> {
                int j = 0;

                while (j < limit) {
                    String key = keyStore.get(r.nextInt(CACHE_SIZE));
                    long time = timeOf(() -> cache.get(key));

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

        double[] average = new double[2];

        average[0] = (accumulatedTime.get() + 0.0) / CACHE_SIZE;
        average[1] = average[0] / 1000000;

        accumulatedTime.set(0L);

        return average;
    }

    private static double[] doRemoveOperations(Cache cache, Thread[] threads, Vector<String> keyStore, AtomicLong accumulatedTime) {
        accumulatedTime.set(0L);

        Random r = new Random();
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        int limit = CACHE_SIZE / THREAD_COUNT;

        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i] = new Thread(() -> {
                int j = 0;

                while (j < limit) {
                    String key = keyStore.get(r.nextInt(CACHE_SIZE));
                    long time = timeOf(() -> cache.remove(key));

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

        double[] average = new double[2];

        average[0] = (accumulatedTime.get() + 0.0) / CACHE_SIZE;
        average[1] = average[0] / 1000000;

        accumulatedTime.set(0L);

        return average;
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
}
