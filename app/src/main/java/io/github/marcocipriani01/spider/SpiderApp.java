package io.github.marcocipriani01.spider;

import android.app.Application;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;

public class SpiderApp extends Application {

    public static final String APP_NAME = "Spider";
    public static final String DOWNLOAD_FOLDER = "SpiderSFTP";
    public static Session session;
    public static ChannelSftp channel;
    public static String currentPath;
    public static byte[] private_bytes;

    /**
     * Returns the Tag for a class to be used in Android logging statements
     */
    public static String getTag(Class<?> clazz) {
        return APP_NAME + "." + clazz.getSimpleName();
    }
}