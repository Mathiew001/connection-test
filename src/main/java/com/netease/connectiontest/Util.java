package com.netease.connectiontest;

import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxThreadFactory;

import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

class Util {
    static class LongPtr {
        long v;
    }

    static class IntPtr {
        int v;
    }

    private static DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static String ts() {
        return ts(LocalDateTime.now());
    }

    static String ts(LocalDateTime date) {
        return fmt.format(date);
    }

    static void logErr(String str) {
        System.err.println("[" + Util.ts() + "] " + str);
    }

    public static void main(String[] args) {
        String a = "abcd";
        String b = "123".intern();
        String c = "123";
        System.out.println(c == b);
        List<String> list = new ArrayList<>();
    }
}
