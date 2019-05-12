package com.netease.connectiontest;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import java.util.function.BiConsumer;

public interface RequestHandler<CTX, OPTIONS, CLIENT, CONN, REPLY> {
    void init(Vertx vertx, Client.Args args);

    CLIENT getClient(OPTIONS options);

    void connect(CLIENT client, String host, int port, BiConsumer<CTX, CONN> cb);

    void establishHandler(CTX ctx, CONN conn, Runnable cb);

    void exceptionHandler(CTX ctx, CONN conn, Handler<Throwable> cb);

    void closeHandler(CTX ctx, CONN conn, Handler<Void> cb);

    void close(CTX ctx, CONN conn);

    void replyHandler(CTX ctx, CONN conn, Handler<REPLY> cb);

    String formatReply(CTX ctx, REPLY reply);

    void write(CTX ctx, CONN conn);

    boolean testReply(CTX ctx, REPLY reply);

    LocalAddress localAddress(CTX ctx, CONN conn);

    boolean autoReconnected(CTX ctx, CONN conn);
}
