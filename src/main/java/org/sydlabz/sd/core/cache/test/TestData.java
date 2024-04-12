package org.sydlabz.sd.core.cache.test;

import org.sydlabz.sd.core.cache.Cacheable;

public class TestData implements Cacheable {
    private final String data;

    public TestData(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }

    @Override
    public String toString() {
        return "{data='" + data + "'}";
    }
}
