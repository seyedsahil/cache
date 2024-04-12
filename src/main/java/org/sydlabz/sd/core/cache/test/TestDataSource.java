package org.sydlabz.sd.core.cache.test;

import org.sydlabz.sd.core.cache.Cacheable;
import org.sydlabz.sd.core.cache.DataSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TestDataSource implements DataSource {
    private final Map<String, Cacheable> colorMap = new HashMap<>();

    public TestDataSource() {
        init();
    }

    private void init() {
        Random random = new Random();

        for (int i = 0; i < 64; i++) {
            int red = random.nextInt(256);
            int green = random.nextInt(256);
            int blue = random.nextInt(256);
            String color = String.format("#%02X%02X%02X", red, green, blue);

            colorMap.put(String.valueOf(i), new TestData(color));
        }
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

    }
}
