package com.netease.connectiontest;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;

import java.util.function.BiConsumer;

class HttpClientWrapper {
    private final Vertx vertx;
    private final HttpClient client;
    private final Client.Args args;
    private HttpClientRequest currentReq;
    private Handler<Throwable> exceptionHandler;
    private Handler<Void> closeHandler;
    private Handler<HttpClientResponse> respHandler;
    private Runnable establishHandler;
    private HttpConnection lastConnection;
    private HttpConnection lastErrorConnection;

    LocalAddress local;

    HttpClientWrapper(Vertx vertx, HttpClient client, Client.Args args) {
        this.vertx = vertx;
        this.client = client;
        this.args = args;
    }

    public void setExceptionHandler(Handler<Throwable> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public void setCloseHandler(Handler<Void> closeHandler) {
        this.closeHandler = closeHandler;
    }

    public void close() {
        if (lastConnection != null && (lastErrorConnection == null || lastConnection == lastErrorConnection)) {
            lastErrorConnection = null;
            lastConnection.close();
        }
    }

    public void setRespHandler(Handler<HttpClientResponse> respHandler) {
        this.respHandler = respHandler;
    }

    public void request() {
        vertx.runOnContext(this::request);
    }

    private void request(@SuppressWarnings("unused") Void ignore) {
        if (currentReq != null) {
            throw new IllegalStateException("the currentReq is not finished yet");
        }

        currentReq = client.get(args.port, args.host, "/");
        if (args.protocol == Protocol.h1 && args.reqPerConn != 1) {
            currentReq.putHeader("Keep-Alive", "max=" + args.reqPerConn);
        }
        currentReq.connectionHandler(conn -> {
            local = new LocalAddress(conn.localAddress().host(), conn.localAddress().port());
            conn.closeHandler(v -> {
                if (closeHandler != null)
                    closeHandler.handle(null);
            });
            if (conn != lastConnection) {
                lastConnection = conn;
                if (establishHandler != null)
                    establishHandler.run();
            }
        });
        currentReq.exceptionHandler(t -> {
            HttpConnection conn = currentReq.connection();
            if (conn != null) {
                lastErrorConnection = conn;
                if (local == null) {
                    local = new LocalAddress(conn.localAddress().host(), conn.localAddress().port());
                }
            }
            currentReq = null;
            exceptionHandler.handle(t);
            request(); // only get here when has in-flight request, so the respHandler will not be called
        });
        currentReq.handler(resp -> {
            currentReq = null;
            respHandler.handle(resp);
        });
        currentReq.end();
    }

    public void setEstablishHandler(Runnable cb) {
        this.establishHandler = cb;
    }
}

public class HttpRequestHandler implements RequestHandler<Void, HttpClientOptions, Void, HttpClientWrapper, HttpClientResponse> {
    private Vertx vertx;
    private HttpClientOptions opts;
    private Client.Args args;

    @Override
    public void init(Vertx vertx, Client.Args args) {
        this.vertx = vertx;
        this.args = args;
    }

    @Override
    public Void getClient(HttpClientOptions opts) {
        this.opts = opts;
        opts.setMaxPoolSize(1);
        if (args.protocol == Protocol.h1 && args.reqPerConn == 1) {
            opts.setKeepAlive(false);
        } else {
            opts.setKeepAlive(true);
        }
        opts.setMaxWaitQueueSize(0);
        return null;
    }

    @Override
    public void connect(Void nothing, String host, int port, BiConsumer<Void, HttpClientWrapper> cb) {
        HttpClient client = vertx.createHttpClient(opts);
        HttpClientWrapper wrapper = new HttpClientWrapper(vertx, client, args);
        cb.accept(null, wrapper);
    }

    @Override
    public void establishHandler(Void aVoid, HttpClientWrapper httpClientWrapper, Runnable cb) {
        httpClientWrapper.setEstablishHandler(cb);
    }

    @Override
    public void exceptionHandler(Void ctx, HttpClientWrapper client, Handler<Throwable> cb) {
        client.setExceptionHandler(cb);
    }

    @Override
    public void closeHandler(Void ctx, HttpClientWrapper client, Handler<Void> cb) {
        client.setCloseHandler(cb);
    }

    @Override
    public void close(Void ctx, HttpClientWrapper req) {
        req.close();
    }

    @Override
    public void replyHandler(Void ctx, HttpClientWrapper client, Handler<HttpClientResponse> cb) {
        client.setRespHandler(cb);
    }

    @Override
    public String formatReply(Void ctx, HttpClientResponse resp) {
        return "" + resp.statusCode();
    }

    @Override
    public void write(Void ctx, HttpClientWrapper client) {
        client.request();
    }

    @Override
    public boolean testReply(Void ctx, HttpClientResponse resp) {
        int status = resp.statusCode();
        return status == 200 || status == 204;
    }

    @Override
    public LocalAddress localAddress(Void aVoid, HttpClientWrapper httpClientWrapper) {
        return httpClientWrapper.local == null ? new LocalAddress(null, 0) : httpClientWrapper.local;
    }

    @Override
    public boolean autoReconnected(Void aVoid, HttpClientWrapper httpClientWrapper) {
        return true;
    }
}
