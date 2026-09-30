package com.zynelabs.iptv.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** Loads all channels for an account off the UI thread. Shared by the home screens. */
public class ChannelRepo {

    public interface Callback {
        void onResult(List<Channel> live, List<Channel> vod, List<Channel> series, String err);
    }

    public static void load(final Context ctx, final PlAccount acc, final Callback cb) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                List<Channel> live = new ArrayList<>();
                List<Channel> vod = new ArrayList<>();
                List<Channel> series = new ArrayList<>();
                String err = null;
                try {
                    if ("m3u_url".equals(acc.type)) {
                        splitM3u(M3uParser.parse(XtreamClient.httpGet(acc.url)), live, vod);
                    } else if ("m3u_file".equals(acc.type)) {
                        splitM3u(M3uParser.parse(readFile(ctx, acc.file)), live, vod);
                    } else if ("stalker".equals(acc.type)) {
                        live = StalkerClient.channels(acc.url, acc.mac);
                    } else {
                        live = XtreamClient.live(acc.server, acc.user, acc.pass);
                        vod = XtreamClient.vod(acc.server, acc.user, acc.pass);
                        series = XtreamClient.series(acc.server, acc.user, acc.pass);
                    }
                } catch (Exception e) {
                    err = e.getMessage();
                }
                // Tag smart categories here, off the UI thread: 97k channels of
                // keyword matching on the main thread causes an ANR.
                if (err == null) {
                    Cats.tagAll(live);
                    Cats.tagAll(vod);
                    Cats.tagAll(series);
                }
                final List<Channel> fl = live, fv = vod, fs = series;
                final String ferr = err;
                main.post(new Runnable() {
                    @Override public void run() { cb.onResult(fl, fv, fs, ferr); }
                });
            }
        }).start();
    }

    /** M3U playlists mix live channels and movie files: split by detected kind. */
    private static void splitM3u(List<Channel> all, List<Channel> live, List<Channel> vod) {
        for (Channel c : all) {
            if (c.kind == Channel.VOD) vod.add(c);
            else live.add(c);
        }
    }

    private static String readFile(Context ctx, String name) {
        try {
            FileInputStream fis = ctx.openFileInput(name);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
