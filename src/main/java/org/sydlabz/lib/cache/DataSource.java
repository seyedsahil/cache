package org.sydlabz.lib.cache;

public interface DataSource {
    Cacheable load(String key);

    String getName();

    void save(String key, Cacheable data);

    void update(String key, Cacheable data);

    void sync();
}
