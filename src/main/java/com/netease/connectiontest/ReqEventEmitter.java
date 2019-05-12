package com.netease.connectiontest;

public interface ReqEventEmitter {
    void init();

    void onReqDone(long startNanoTime);

    void onConnEstablish(LocalAddress local);

    void onConnClose(LocalAddress local);

    void onConnException(Throwable t, LocalAddress local);

    void onInterval(long timestamp);
}
