package org.sydlabz.lib.cache;

import java.util.LinkedList;
import java.util.TimerTask;

final class DataSyncTask extends TimerTask {
    private final LinkedList<DataSourceItem> dataSyncQueue;
    private final DataSource dataSource;

    DataSyncTask(final LinkedList<DataSourceItem> dataSyncQueue, final DataSource dataSource) {
        this.dataSyncQueue = dataSyncQueue;
        this.dataSource = dataSource;
    }

    @Override
    public void run() {
        this.doDataSync();
    }

    synchronized void doDataSync() {
        if (this.dataSyncQueue.isEmpty()) {
            return;
        }

        this.dataSyncQueue.forEach(dataSourceItem -> {
            Cached cachedRecord = dataSourceItem.cachedRecord();

            if (dataSourceItem.isUpdate()) {
                this.dataSource.update(cachedRecord.getRecordKey(), cachedRecord.getCachedData());
            } else {
                this.dataSource.save(cachedRecord.getRecordKey(), cachedRecord.getCachedData());
            }
        });
        this.dataSyncQueue.clear();
    }
}
