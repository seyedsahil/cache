package org.sydlabz.lib.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TestDataSource implements DataSource {
    private final Map<String, Cacheable> colorMap = new ConcurrentHashMap<>();

    public TestDataSource() {

    }

    @Override
    public Cacheable load(String key) {
        return colorMap.get(key);
    }

    @Override
    public String getName() {
        return "dictionary";
    }

    @Override
    public void save(String key, Cacheable dataRecord) {
        colorMap.put(key, dataRecord);
    }

    @Override
    public void update(String key, Cacheable data) {

    }

    @Override
    public void sync() {
        throw new UnsupportedOperationException("Not Yet Implemented");
    }
}