package org.sydlabz.sd.core.cache;

/**
 * @author seyedsahil
 * @version 1.0
 */
public interface DataSource {
    Cacheable load(String key);

    String getName();

    void save(String key, Cacheable data);

    void update(String key, Cacheable data);

    void sync();
}
