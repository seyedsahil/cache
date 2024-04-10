package org.sydlabz.sd.core.cache;

/**
 * @author seyedsahil
 * @version 1.0
 */
public interface Source {
    Cacheable load(String key);

    String getName();

    void save(String key, Cacheable dataRecord);
}
