package org.sydlabz.sd.core.cache;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * @author seyedsahil
 * @version 1.0
 */
public final class Util {
    private static DateTimeFormatter formatter;
    private static MessageDigest messageDigest;

    static {
        try {
            Util.formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            Util.messageDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("no md5 algorithm");
        }
    }

    public static String time(long time) {
        Instant instant = Instant.ofEpochMilli(time);
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

        return localDateTime.format(Util.formatter);
    }

    public static boolean usable(Object object) {
        return object != null;
    }

    public static int hash(String value) {
        Util.messageDigest.reset();
        Util.messageDigest.update(value.getBytes());

        byte[] digest = messageDigest.digest();
        int hash = 0;

        for (int i = 0; i < 4; i++) {
            hash |= ((digest[i] & 0xFF) << (i * 8));
        }

        return hash;
    }
}
