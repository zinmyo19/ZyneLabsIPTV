package com.zynelabs.iptv.ui;

import com.zynelabs.iptv.data.Channel;

import java.util.ArrayList;
import java.util.List;

/** Static zap list shared with PlayerActivity (process-local). */
public class PlayerQueue {
    private static List<Channel> list = new ArrayList<>();
    private static int pos = 0;
    private static String accountId = "";
    // Full browsable list for the in-player channel drawer (falls back to
    // the zap list when the caller didn't supply one).
    private static List<Channel> full = new ArrayList<>();

    public static void setAccount(String id) { accountId = id == null ? "" : id; }
    public static String accountId() { return accountId; }

    public static void set(List<Channel> l, int p) {
        list = new ArrayList<>(l);
        pos = Math.max(0, Math.min(p, list.size() - 1));
        full = new ArrayList<>(l);
    }

    public static void setFull(List<Channel> l) {
        full = l == null ? new ArrayList<Channel>() : new ArrayList<>(l);
    }

    public static List<Channel> full() { return full.isEmpty() ? list : full; }

    public static Channel current() {
        return list.isEmpty() ? null : list.get(pos);
    }

    public static boolean hasMultiple() { return list.size() > 1; }

    public static Channel next() {
        if (list.isEmpty()) return null;
        pos = (pos + 1) % list.size();
        return list.get(pos);
    }

    public static Channel prev() {
        if (list.isEmpty()) return null;
        pos = (pos - 1 + list.size()) % list.size();
        return list.get(pos);
    }

    public static String position() {
        return list.isEmpty() ? "" : ((pos + 1) + " / " + list.size());
    }
}
