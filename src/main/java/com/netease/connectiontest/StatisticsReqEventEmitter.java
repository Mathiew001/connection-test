package com.netease.connectiontest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class StatisticsReqEventEmitter implements ReqEventEmitter {
    private static class Statistics {
        private AtomicLong timeUse = new AtomicLong();
        private AtomicLong reqCount = new AtomicLong();

        private void setZero() {
            timeUse.set(0);
            reqCount.set(0);
        }
    }

    private volatile Statistics statistics = new Statistics();
    private AtomicInteger connCount = new AtomicInteger();
    private Statistics nextStatistics = new Statistics(); // reuse object
    private long lastPrint;

    private long maxRps = 0;

    private boolean longConnection;
    private final boolean verbose;
    private final Client.Args args;

    public StatisticsReqEventEmitter(Client.Args args) {
        this.args = args;
        this.verbose = args.verbose;
        setLongConnection(args.reqPerConn == 0, true);
    }

    private void setLongConnection(boolean l) {
        setLongConnection(l, longConnection != l);
    }

    private void setLongConnection(boolean l, boolean print) {
        if (verbose && print) {
            Util.logErr("It is considered to be a " + (l ? "long" : "short") + " connection test");
        }
        longConnection = l;
    }

    @Override
    public void init() {
        lastPrint = System.currentTimeMillis();
    }

    @Override
    public void onReqDone(long start) {
        long end = System.nanoTime();
        statistics.timeUse.addAndGet(end - start);
        statistics.reqCount.addAndGet(1);
    }

    @Override
    public void onConnEstablish(LocalAddress local) {
        int aConnCount = connCount.addAndGet(1);
        if (verbose || longConnection) {
            Util.logErr("connected " + aConnCount + " " + local);
        }
    }

    @Override
    public void onConnClose(LocalAddress local) {
        int count = connCount.addAndGet(-1);
        if (verbose || longConnection) {
            Util.logErr("connection closed, current connections " + count + " " + local);
        }
    }

    @Override
    public void onConnException(Throwable t, LocalAddress local) {
        Util.logErr("connection " + local + " got exception: " + t.getMessage());
        if (verbose) {
            t.printStackTrace();
        }
    }

    @Override
    public void onInterval(long cur) {
        String curStr = Util.ts(LocalDateTime.ofInstant(Instant.ofEpochMilli(cur), ZoneId.systemDefault()));
        if (statistics.reqCount.get() == 0) {
            if (verbose) {
                System.out.println("[" + curStr + "] req count is zero");
            }
            lastPrint = System.currentTimeMillis();
            return;
        }
        long delta = cur - lastPrint;
        if (delta == 0) return;
        lastPrint = cur;

        Statistics s = statistics;
        // reset values
        statistics = nextStatistics;
        nextStatistics = s;

        long reqCount = s.reqCount.get();
        long timeUse = s.timeUse.get();

        nextStatistics.setZero();

        System.out.print("[");
        System.out.print(curStr);
        System.out.print("] ");
        System.out.print("rps: ");
        long rps = reqCount * 1000 / delta;
        System.out.print(rps);
        System.out.print("\t\t");
        System.out.print("cost: ");
        System.out.print(timeUse / 1000 / reqCount);
        System.out.print(" us");
        System.out.println();

        if (rps > maxRps) {
            maxRps = rps;
            setLongConnection((args.reqPerConn == 0) || ((maxRps * 30) < args.reqPerConn));
        }
    }
}
