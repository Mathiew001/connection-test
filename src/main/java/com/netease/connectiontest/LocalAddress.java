package com.netease.connectiontest;

public class LocalAddress {
    public final String host;
    public final int port;

    public LocalAddress(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public String toString() {
        return "LocalAddress(host=" + host + ", port=" + port + ")";
    }
}
