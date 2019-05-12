package com.netease.connectiontest;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import java.util.function.BiConsumer;

public class RedisPingRequestHandler implements RequestHandler<Void, NetClientOptions, NetClient, NetSocket, Buffer> {
    private static final Buffer DATA = Buffer.buffer("PING\r\n");
    private static final Buffer PONG_RESP = Buffer.buffer("+PONG\r\n");
    private static final Buffer NOAU_RESP = Buffer.buffer("-NOAUTH");
    private Vertx vertx;

    @Override
    public void init(Vertx vertx, Client.Args args) {
        this.vertx = vertx;
    }

    @Override
    public NetClient getClient(NetClientOptions netClientOptions) {
        return vertx.createNetClient(netClientOptions);
    }

    @Override
    public void connect(NetClient netClient, String host, int port, BiConsumer<Void, NetSocket> cb) {
        netClient.connect(port, host, r -> {
            if (r.failed()) {
                Util.logErr("connect failed");
                r.cause().printStackTrace();
                System.exit(2);
            }
            NetSocket sock = r.result();
            cb.accept(null, sock);
        });
    }

    @Override
    public void establishHandler(Void aVoid, NetSocket netSocket, Runnable cb) {
        cb.run();
    }

    @Override
    public void exceptionHandler(Void ctx, NetSocket netSocket, Handler<Throwable> cb) {
        netSocket.exceptionHandler(cb);
    }

    @Override
    public void closeHandler(Void ctx, NetSocket netSocket, Handler<Void> cb) {
        netSocket.closeHandler(cb);
    }

    @Override
    public void close(Void ctx, NetSocket netSocket) {
        netSocket.close();
    }

    @Override
    public void replyHandler(Void ctx, NetSocket netSocket, Handler<Buffer> cb) {
        netSocket.handler(cb);
    }

    @Override
    public String formatReply(Void ctx, Buffer buffer) {
        return buffer.toString();
    }

    @Override
    public void write(Void ctx, NetSocket netSocket) {
        netSocket.write(DATA);
    }

    @Override
    public boolean testReply(Void ctx, Buffer buf) {
        if (buf.length() < 7) {
            return false;
        }
        Buffer b = buf.slice(0, 7);
        return b.equals(PONG_RESP) || b.equals(NOAU_RESP);
    }

    @Override
    public LocalAddress localAddress(Void aVoid, NetSocket netSocket) {
        SocketAddress a = netSocket.localAddress();
        if (a == null) return new LocalAddress(null, 0);
        return new LocalAddress(a.host(), a.port());
    }

    @Override
    public boolean autoReconnected(Void aVoid, NetSocket netSocket) {
        return false;
    }
}
