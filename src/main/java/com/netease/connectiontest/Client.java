package com.netease.connectiontest;

import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.net.*;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

class Client implements Runnable {
    private final ReqEventEmitter emitter;
    private final Args args;
    private final Vertx vertx;
    private final ClientOptionsBase opts;

    static class Args {
        String host;
        int port;
        int conn;
        int prtInt;
        int thread;
        boolean tls;
        Protocol protocol;
        boolean sslCache;
        int reqPerConn;
        int idleTimeout;
        int connTimeout;
        boolean fin;
        boolean verbose;
    }

    Client(Args args, ReqEventEmitter eventEmitter) {
        this.emitter = eventEmitter;
        this.args = args;

        String host = args.host;
        int port = args.port;
        int conn = args.conn;
        int prtInt = args.prtInt;
        int thread = args.thread;
        boolean tls = args.tls;
        boolean sslCache = args.sslCache;
        int reqPerConn = args.reqPerConn;
        int idleTimeout = args.idleTimeout;
        int connTimeout = args.connTimeout;
        boolean fin = args.fin;
        boolean verbose = args.verbose;

        if (verbose) {
            Util.logErr("host=" + host);
            Util.logErr("port=" + port);
            Util.logErr("conn=" + conn);
            Util.logErr("prtInt=" + prtInt);
            Util.logErr("thread=" + thread);
            Util.logErr("tls=" + tls);
            Util.logErr("sslCache=" + sslCache);
            Util.logErr("reqPerConn=" + reqPerConn);
            Util.logErr("connTimeout=" + connTimeout);
            Util.logErr("idleTimeout=" + idleTimeout);
            Util.logErr("fin=" + fin);
        }

        vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(conn).setEventLoopPoolSize(thread));

        emitter.init();

        // the print thread is out of event loop threads
        new Thread(() -> {
            //noinspection InfiniteLoopStatement
            while (true) {
                try {
                    Thread.sleep(prtInt);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                long cur = System.currentTimeMillis();
                emitter.onInterval(cur);
            }
        }).start();

        switch (args.protocol) {
            case redis:
                opts = new NetClientOptions();
                break;
            case h1:
                opts = new HttpClientOptions().setProtocolVersion(args.reqPerConn == 1 ? HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1);
                break;
            case h2:
                opts = new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2).setUseAlpn(true);
                break;
            default:
                throw new IllegalArgumentException("unknown protocol " + args.protocol);
        }
        opts.setConnectTimeout(args.connTimeout)
            .setIdleTimeout(args.idleTimeout)
            .setReuseAddress(true); // we use reuse address instead of reuse port for platform compatibility
        if (!fin) {
            opts.setSoLinger(0); // reset connection, no time_wait
        }
        if (tls) {
            opts.setSsl(true);
            opts.setTrustAll(true); // skip cert chain validation
            opts.setSslEngineOptions(new OpenSSLEngineOptions().setSessionCacheEnabled(args.sslCache));
        }
    }

    private class GenericVerticle<CTX, OPTIONS, CLIENT, CONN, REPLY> extends AbstractVerticle {
        private final RequestHandler<CTX, OPTIONS, CLIENT, CONN, REPLY> handler;
        private final OPTIONS opts;
        private CLIENT cli;

        private GenericVerticle(RequestHandler<CTX, OPTIONS, CLIENT, CONN, REPLY> handler, OPTIONS opts) {
            this.handler = handler;
            this.opts = opts;
        }

        @Override
        public void init(Vertx vertx, Context context) {
            super.init(vertx, context);
            handler.init(vertx, args);
            cli = handler.getClient(opts);
        }

        @Override
        public void start() {
            connect();
        }

        private void connect() {
            String host = args.host;
            int port = args.port;
            handler.connect(cli, host, port, (ctx, conn) -> {
                handler.establishHandler(ctx, conn, () ->
                    emitter.onConnEstablish(handler.localAddress(ctx, conn))
                );
                handler.closeHandler(ctx, conn, v -> {
                    emitter.onConnClose(handler.localAddress(ctx, conn));
                    if (!handler.autoReconnected(ctx, conn))
                        connect();
                });
                handler.exceptionHandler(ctx, conn, t -> {
                    emitter.onConnException(t, handler.localAddress(ctx, conn));
                    handler.close(ctx, conn);
                });

                handleConn(ctx, conn);
            });
        }

        private void handleConn(CTX ctx, CONN conn) {
            Util.LongPtr startTime = new Util.LongPtr();
            Util.IntPtr reqCount = new Util.IntPtr();
            handler.replyHandler(ctx, conn, reply -> {
                if (handler.testReply(ctx, reply)) {
                    emitter.onReqDone(startTime.v);

                    if (args.reqPerConn != 0) {
                        ++reqCount.v;
                        if (reqCount.v >= args.reqPerConn) {
                            handler.close(ctx, conn);
                            if (handler.autoReconnected(ctx, conn))
                                reqCount.v = 0;
                            else
                                return;
                        }
                    }

                    write(ctx, conn, startTime);
                } else {
                    Util.logErr("unexpected reply: " + handler.formatReply(ctx, reply));
                    System.exit(2);
                }
            });
            write(ctx, conn, startTime);
        }

        private void write(CTX ctx, CONN conn, Util.LongPtr startTime) {
            startTime.v = System.nanoTime();
            handler.write(ctx, conn);
        }
    }

    @Override
    public void run() {
        RequestHandler handler;
        switch (args.protocol) {
            case redis:
                handler = new RedisPingRequestHandler();
                break;
            case h1:
            case h2:
                handler = new HttpRequestHandler();
                break;
            default:
                throw new IllegalArgumentException("unknown protocol " + args.protocol);
        }
        //noinspection unchecked
        vertx.deployVerticle(() -> new GenericVerticle(handler, opts), new DeploymentOptions().setInstances(args.conn));
    }
}
