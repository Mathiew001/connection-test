package com.netease.connectiontest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.net.NetServer;

class Server {
    static class Args {
        int port;
        int thread;
        Protocol protocol;
        boolean verbose;
    }

    static void main0(Args args) {
        if (args.verbose) {
            Util.logErr("port=" + args.port);
            Util.logErr("thread=" + args.thread);
            Util.logErr("protocol=" + args.protocol);
        }

        Vertx vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(args.thread).setEventLoopPoolSize(args.thread));
        if (args.protocol == Protocol.redis) {
            vertx.deployVerticle(() -> new AbstractVerticle() {
                @Override
                public void start() {
                    NetServer server = vertx.createNetServer();
                    server.connectHandler(sock -> {
                        String remote = sock.remoteAddress().host() + ":" + sock.remoteAddress().port();
                        if (args.verbose) {
                            Util.logErr("new connection from " + remote);
                        }
                        sock.handler(buf -> {
                            String str = buf.toString();
                            if (!str.equals("PING\r\n")) {
                                if (args.verbose) {
                                    Util.logErr("connection got unexpected request " + str);
                                }
                            }
                            sock.write("+PONG\r\n");
                        });
                        sock.exceptionHandler(t -> {
                            Util.logErr("connection " + remote + " got exception: " + t.getMessage());
                            if (args.verbose) {
                                t.printStackTrace();
                            }
                        });
                    });
                    //noinspection Duplicates
                    server.listen(args.port, r -> {
                        if (r.failed()) {
                            Util.logErr("listen failed");
                            System.exit(2);
                        }
                        if (args.verbose) {
                            Util.logErr("listen on " + args.port);
                        }
                    });
                }
            }, new DeploymentOptions().setInstances(args.thread));
        } else {
            vertx.deployVerticle(() -> new AbstractVerticle() {
                @Override
                public void start() {
                    HttpServer server = vertx.createHttpServer();
                    server.requestHandler(req -> {
                        String remote = req.remoteAddress().host() + ":" + req.remoteAddress().port();
                        if (args.verbose) {
                            Util.logErr("new request from " + remote);
                        }
                        req.response().setStatusCode(204).putHeader("Connection", "Keep-Alive").end();
                    });
                    //noinspection Duplicates
                    server.listen(args.port, r -> {
                        if (r.failed()) {
                            Util.logErr("listen failed");
                            System.exit(2);
                        }
                        if (args.verbose) {
                            Util.logErr("listen on " + args.port);
                        }
                    });
                }
            }, new DeploymentOptions().setInstances(args.thread));
        }
    }
}
