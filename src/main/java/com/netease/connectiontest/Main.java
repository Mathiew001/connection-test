package com.netease.connectiontest;

import sun.misc.Signal;

public class Main {
    private enum Mode {
        server,
        client,
    }

    private static final String usage = "" +
        "usage:\n" +
        "       server port={listen-port} \\\n" +
        "              [thread={thread size}] \\\n" +
        "              [protocol={redis|h1}] \\\n" +
        "              [verbose]\n" +
        "       client host={server-ip-address} \\\n" +
        "              port={port} \\\n" +
        "              conn={connection size} \\\n" +
        "              prtInt={print interval} \\\n" +
        "              [thread={thread size}] \\\n" +
        "              [tls] \\\n" +
        "              [protocol={redis|h1|h2}] \\\n" +
        "              [sslCache] \\\n" +
        "              [reqPerConn=requests per connection then close] \\\n" +
        "              [connTimeout=connection timeout (ms)] \\\n" +
        "              [idleTimeout=idle timeout (seconds)] \\\n" +
        "              [fin] \\\n" +
        "              [verbose] \n" +
        "ctrl-c to terminate\n" +
        "details:" +
        "       conn: total of all threads\n" +
        "       thread: default 1\n" +
        "       prtInt: ms\n" +
        "       tls: default tcp not tls\n" +
        "       protocol: default is redis, client sends `PING` and expects `+PONG`(as this server replies) or `-NOAUTH`\n" +
        "                 set to h1/h2 then client sends `GET /` and expects 2xx(as this server replies) response\n" +
        "                 if set to h2, then tls is automatically enabled\n" +
        "                 if reqPerConn is 1 and protocol set to h1, then http 1.0 without keep-alive is used, otherwise 1.1 is used\n" +
        "       sslCache: default no ssl cache for client\n" +
        "                 only work if tls is set\n" +
        "       reqPerConn: default no limit\n" +
        "                   should be an integer and >= 0, 0 means no limit\n" +
        "                   when using h1, the reqPerConn must be specified\n" +
        "       connTimeout: default 3000 ms, 1 - 60000\n" +
        "       idleTimeout: default 3 seconds, 1 - 60\n" +
        "       fin: send FIN when connection closed (default: send RST)\n" +
        "       verbose: default not enabled";

    public static void main(String[] args) {
        Mode mode = null;
        String host = "";
        int port = 0;
        int conn = 0;
        int prtInt = 0;
        int thread = 1;
        boolean tls = false;
        Protocol protocol = Protocol.redis;
        boolean sslCache = false;
        int reqPerConn = 0;
        int connTimeout = 3000;
        int idleTimeout = 3;
        boolean fin = false;
        boolean verbose = false;

        for (String arg : args) {
            if (arg.startsWith("host=")) {
                host = arg.substring("host=".length());
            } else if (arg.startsWith("port=")) {
                port = Integer.parseInt(arg.substring("port=".length()));
            } else if (arg.startsWith("conn=")) {
                conn = Integer.parseInt(arg.substring("conn=".length()));
            } else if (arg.startsWith("prtInt=")) {
                prtInt = Integer.parseInt(arg.substring("prtInt=".length()));
            } else if (arg.equals("server")) {
                mode = Mode.server;
            } else if (arg.equals("client")) {
                mode = Mode.client;
            } else if (arg.startsWith("thread=")) {
                thread = Integer.parseInt(arg.substring("thread=".length()));
            } else if (arg.equals("tls")) {
                tls = true;
            } else if (arg.startsWith("protocol=")) {
                protocol = Protocol.valueOf(arg.substring("protocol=".length()));
            } else if (arg.equals("sslCache")) {
                sslCache = true;
            } else if (arg.startsWith("reqPerConn=")) {
                reqPerConn = Integer.parseInt(arg.substring("reqPerConn=".length()));
            } else if (arg.startsWith("connTimeout=")) {
                connTimeout = Integer.parseInt(arg.substring("connTimeout=".length()));
            } else if (arg.startsWith("idleTimeout=")) {
                idleTimeout = Integer.parseInt(arg.substring("idleTimeout=".length()));
            } else if (arg.equals("fin")) {
                fin = true;
            } else if (arg.equals("verbose")) {
                verbose = true;
            }
        }
        if (mode == null) {
            System.out.println(usage);
            return;
        }
        if (protocol == Protocol.h2) {
            tls = true;
        }
        if (mode == Mode.client) {
            if (host.isEmpty() || port <= 0 || conn <= 0 || prtInt <= 0 || thread < 1 || reqPerConn < 0) {
                System.out.println(usage);
                return;
            }
            if (protocol == Protocol.h1 && reqPerConn == 0) {
                System.out.println("reqPerConn must be specified when using protocol=h1");
                return;
            }
            if (idleTimeout < 0 || idleTimeout > 60) {
                System.out.println("idleTimeout must be from 1 to 60");
                return;
            }
            if (connTimeout < 0 || connTimeout > 60000) {
                System.out.println("connTimeout must be between 0 and 60000");
                return;
            }

            Client.Args a = new Client.Args();
            a.host = host;
            a.port = port;
            a.conn = conn;
            a.prtInt = prtInt;
            a.thread = thread;
            a.tls = tls;
            a.protocol = protocol;
            a.sslCache = sslCache;
            a.reqPerConn = reqPerConn;
            a.connTimeout = connTimeout;
            a.idleTimeout = idleTimeout;
            a.fin = fin;
            a.verbose = verbose;
            Client cli = new Client(a, new StatisticsReqEventEmitter(a));
            cli.run();
        } else { // server
            if (port <= 0 || thread < 1 || (protocol != Protocol.redis && protocol != Protocol.h1)) {
                System.out.println(usage);
                return;
            }

            Server.Args a = new Server.Args();
            a.port = port;
            a.thread = thread;
            a.protocol = protocol;
            a.verbose = verbose;
            Server.main0(a);
        }

        Signal.handle(new Signal("INT"), s -> {
            Util.logErr("ctrl-c: terminate");
            System.exit(0);
        });
    }
}
