package org.sydlabz.lib.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class Util {
    static final long HASH_KEY_RANGE = Integer.MAX_VALUE + 1L;
    static final int BUCKET_COUNT = 1024;
    private static final int ERROR_HASH = -1;
    private static final int FNV_OFFSET_BASIS_32 = 0x811c9dc5;
    private static final int FNV_PRIME_32 = 0x01000193;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Fast hash algorithm - Fowler-Noll-Vo.
     *
     * @param input is the key which needs to be hashed.
     * @return the hashed value which is a 32-bit integer.
     */
    static int hash(String input) {
        if (!Util.isUsable(input)) {
            return Util.ERROR_HASH;
        }

        int hash = Util.FNV_OFFSET_BASIS_32;
        char[] content = input.toCharArray();

        for (char value : content) {
            hash ^= value;
            hash *= Util.FNV_PRIME_32;
        }

        return hash;
    }

    static boolean isUsable(Object object) {
        if (object == null) {
            return false;
        } else if (object instanceof String) {
            return !((String) object).isEmpty();
        }

        return true;
    }

    static String toJson(Object object) {
        return Util.GSON.toJson(object);
    }

    static String time(long time) {
        Instant instant = Instant.ofEpochMilli(time);
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

        return localDateTime.format(Util.formatter);
    }

    static void record(String method) {
        System.out.println(method + ": " + time(System.currentTimeMillis()));
    }
}
