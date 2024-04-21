package org.sydlabz.lib.cache;

import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;

public class EvictionQueue {
    private static final Queue<Cached> waitingQueue = new LinkedList<>();
    private static final int LIMIT = 100;

    private final PriorityQueue<Cached> priorityQueue;

    EvictionQueue(final EvictionStrategy evictionStrategy) {
        this.priorityQueue = new PriorityQueue<>(Cached.getComparator(evictionStrategy));
    }

    synchronized void remove(final Cached cachedRecord) {
        this.remove(cachedRecord.getRecordKey());
    }

    public void remove(String recordKey) {
        boolean removed = this.priorityQueue.removeIf(cached -> cached.getRecordKey().equals(recordKey));

        if (removed && this.priorityQueue.isEmpty()) {
            this.waitingQueueToPriorityQueue();
        }
    }

    synchronized void offer(final Cached cachedRecord) {
        if (this.priorityQueue.size() == EvictionQueue.LIMIT) {
            EvictionQueue.waitingQueue.offer(cachedRecord);
        } else {
            this.priorityQueue.offer(cachedRecord);
        }
    }

    synchronized Cached poll() {
        Cached evictedCachedRecord = this.priorityQueue.poll();

        if (this.priorityQueue.isEmpty()) {
            this.waitingQueueToPriorityQueue();
        }

        return evictedCachedRecord;
    }

    private synchronized void waitingQueueToPriorityQueue() {
        for (int i = 0; i < EvictionQueue.LIMIT && i < EvictionQueue.waitingQueue.size(); i++) {
            Cached waitingRecord = EvictionQueue.waitingQueue.poll();

            if (Util.isUsable(waitingRecord)) {
                this.priorityQueue.offer(waitingRecord);
            }
        }
    }
}
