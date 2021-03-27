package io.github.marcocipriani01.spider;

import android.app.Application;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;

public class SpiderApp extends Application {

    public static final String APP_NAME = "Spider";
    public static final String DOWNLOAD_FOLDER = "SpiderSFTP";
    public static final String IP_PREF = "sftp_ip";
    public static final String PORT_PREF = "sftp_port";
    public static final String USERNAME_PREF = "sftp_username";
    public static final String PASSWORD_PREF = "sftp_password";
    public static final String USE_PEM_PREF = "sftp_use_pem_key";
    public static final String USE_PEM_PASSWORD_PREF = "sftp_use_pem_password";
    public static final String PEM_PASSWORD_PREF = "sftp_pem_password";
    public static Session session;
    public static ChannelSftp channel;

    /**
     * Returns the Tag for a class to be used in Android logging statements
     */
    public static String getTag(Class<?> clazz) {
        return APP_NAME + "." + clazz.getSimpleName();
    }
}