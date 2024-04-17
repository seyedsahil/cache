package org.sydlabz.lib.cache;

import java.util.Collection;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class InvalidationTask extends TimerTask {
    private final BucketMap dataStore;

    private final ExecutorService executor;
    private CountDownLatch latch;

    InvalidationTask(final BucketMap dataStore) {
        this.dataStore = dataStore;
        this.latch = null;
        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public void run() {
        this.invalidate();
    }

    private void invalidate() {
        if (this.dataStore.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        Collection<Bucket> buckets = dataStore.getBuckets();

        this.latch = new CountDownLatch(this.dataStore.getBucketCount());

        for (Bucket bucket : buckets) {
            if (bucket.isEmpty()) {
                this.latch.countDown();
            } else {
                this.executor.execute(() -> {
                    bucket.doInvalidate(currentTime);
                    this.latch.countDown();
                });
            }
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException("unexpected error while waiting for completion");
        }
    }

    void await() {
        if (Util.isUsable(this.latch)) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException("unexpected error while waiting for completion");
            }
        }
    }
}
