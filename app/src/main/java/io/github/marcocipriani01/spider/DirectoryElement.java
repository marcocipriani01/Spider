package io.github.marcocipriani01.spider;

import com.jcraft.jsch.ChannelSftp;

public class DirectoryElement implements Comparable<DirectoryElement> {

    public final String name;
    public final boolean isDirectory;
    public final String shortName;
    public final long size;
    public final ChannelSftp.LsEntry sftpInfo;
    public final double sizeMB;

    public DirectoryElement(String name, boolean isDirectory, long size, ChannelSftp.LsEntry sftpInfo) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.sftpInfo = sftpInfo;
        this.shortName = name.substring(name.lastIndexOf("/") + 1);
        this.size = size;
        this.sizeMB = size / 2048.0;
    }

    public boolean isLink() {
        return sftpInfo.getAttrs().isLink();
    }

    @Override
    public int compareTo(DirectoryElement o) {
        return name.compareTo(o.name);
    }
}