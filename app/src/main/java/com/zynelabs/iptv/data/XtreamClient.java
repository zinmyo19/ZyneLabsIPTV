package com.zynelabs.iptv.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Xtream Codes API client (player_api.php). */
public class XtreamClient {

    public static String normServer(String s) {
        s = s.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    public static String httpGet(String urlStr) throws Exception {
        HttpURLConnection con = null;
        try {
            URL url = new URL(urlStr);
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(30000);
            con.setRequestProperty("User-Agent", "ZyneLabsIPTV/1.0");
            InputStream in = con.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static String apiUrl(String server, String user, String pass, String action) {
        String b = normServer(server) + "/player_api.php?username=" + enc(user) + "&password=" + enc(pass);
        return action == null ? b : b + "&action=" + action;
    }

    /** Returns user_info JSONObject on success, null on auth failure. */
    public static JSONObject login(String server, String user, String pass) throws Exception {
        JSONObject o = new JSONObject(httpGet(apiUrl(server, user, pass, null)));
        JSONObject ui = o.optJSONObject("user_info");
        if (ui != null && "1".equals(ui.optString("auth"))) return ui;
        return null;
    }

    public static Map<Integer, String> categories(String server, String user, String pass, String action) {
        Map<Integer, String> map = new HashMap<>();
        try {
            JSONArray arr = new JSONArray(httpGet(apiUrl(server, user, pass, action)));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                map.put(o.optInt("category_id"), o.optString("category_name", "Other"));
            }
        } catch (Exception ignored) {}
        return map;
    }

    public static List<Channel> live(String server, String user, String pass) {
        List<Channel> out = new ArrayList<>();
        try {
            Map<Integer, String> cats = categories(server, user, pass, "get_live_categories");
            JSONArray arr = new JSONArray(httpGet(apiUrl(server, user, pass, "get_live_streams")));
            String base = normServer(server);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                int id = o.optInt("stream_id");
                Channel c = new Channel();
                c.kind = Channel.LIVE;
                c.key = "live_" + id;
                c.name = o.optString("name", "Channel " + id);
                c.logo = o.optString("stream_icon", "");
                c.group = cats.get(o.optInt("category_id"));
                if (c.group == null) c.group = "";
                c.url = base + "/live/" + enc(user) + "/" + enc(pass) + "/" + id + ".m3u8";
                c.server = base;
                c.user = user;
                c.pass = pass;
                c.streamId = id;
                out.add(c);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static List<Channel> vod(String server, String user, String pass) {
        List<Channel> out = new ArrayList<>();
        try {
            Map<Integer, String> cats = categories(server, user, pass, "get_vod_categories");
            JSONArray arr = new JSONArray(httpGet(apiUrl(server, user, pass, "get_vod_streams")));
            String base = normServer(server);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                int id = o.optInt("stream_id");
                String ext = o.optString("container_extension", "mp4");
                Channel c = new Channel();
                c.kind = Channel.VOD;
                c.key = "vod_" + id;
                c.name = o.optString("name", "Video " + id);
                c.logo = o.optString("stream_icon", "");
                c.group = cats.get(o.optInt("category_id"));
                if (c.group == null) c.group = "";
                c.url = base + "/movie/" + enc(user) + "/" + enc(pass) + "/" + id + "." + ext;
                out.add(c);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static List<Channel> series(String server, String user, String pass) {
        List<Channel> out = new ArrayList<>();
        try {
            Map<Integer, String> cats = categories(server, user, pass, "get_series_categories");
            JSONArray arr = new JSONArray(httpGet(apiUrl(server, user, pass, "get_series")));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                int id = o.optInt("series_id");
                Channel c = new Channel();
                c.kind = Channel.SERIES;
                c.key = "series_" + id;
                c.name = o.optString("name", "Series " + id);
                c.logo = o.optString("cover", "");
                c.group = cats.get(o.optInt("category_id"));
                if (c.group == null) c.group = "";
                c.server = normServer(server);
                c.user = user;
                c.pass = pass;
                c.streamId = id;
                out.add(c);
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** Flattened episode list for a series: each item "S1 E2 — title" with a play URL. */
    public static List<Episode> episodes(Channel series) {
        List<Episode> out = new ArrayList<>();
        try {
            String url = apiUrl(series.server, series.user, series.pass, "get_series_info") + "&series_id=" + series.streamId;
            JSONObject info = new JSONObject(httpGet(url));
            JSONObject eps = info.optJSONObject("episodes");
            if (eps == null) return out;
            JSONArray seasons = eps.names();
            if (seasons == null) return out;
            for (int s = 0; s < seasons.length(); s++) {
                String season = seasons.getString(s);
                JSONArray arr = eps.optJSONArray(season);
                if (arr == null) continue;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject e = arr.getJSONObject(i);
                    String eid = e.optString("id");
                    String ext = e.optString("container_extension", "mp4");
                    String title = e.optString("title", "Episode " + e.optString("episode_num", String.valueOf(i + 1)));
                    Episode ep = new Episode();
                    ep.label = "S" + season + " E" + e.optString("episode_num", String.valueOf(i + 1)) + " — " + title;
                    ep.url = series.server + "/series/" + enc(series.user) + "/" + enc(series.pass) + "/" + eid + "." + ext;
                    out.add(ep);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static class Episode {
        public String label = "";
        public String url = "";
    }

    /** One TV program for Now/Next display. */
    public static class Program {
        public String title = "";
        public String start = "";  // "2026-09-30 14:00:00"
        public String stop = "";
        public String desc = "";

        public String timeRange() {
            return shortTime(start) + "–" + shortTime(stop);
        }

        private static String shortTime(String s) {
            try {
                int sp = s.indexOf(' ');
                if (sp >= 0 && s.length() >= sp + 6) return s.substring(sp + 1, sp + 6);
            } catch (Exception ignored) {}
            return "";
        }
    }

    /** Short EPG (now + next) for a live stream. Empty list if unavailable. */
    public static List<Program> shortEpg(Channel c) {
        List<Program> out = new ArrayList<>();
        try {
            if (c.server == null || c.server.isEmpty() || c.streamId == 0) return out;
            String url = apiUrl(c.server, c.user, c.pass, "get_short_epg")
                    + "&stream_id=" + c.streamId + "&limit=4";
            JSONObject o = new JSONObject(httpGet(url));
            JSONArray arr = o.optJSONArray("epg_listings");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.getJSONObject(i);
                Program p = new Program();
                p.title = e.optString("title", "");
                p.start = e.optString("start", "");
                p.stop = e.optString("stop", "");
                if (p.start.isEmpty() && e.has("start_timestamp")) {
                    p.start = fmtTs(e.optLong("start_timestamp"));
                    p.stop = fmtTs(e.optLong("stop_timestamp"));
                }
                p.desc = e.optString("description", "");
                if (!p.title.isEmpty()) out.add(p);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static String fmtTs(long ts) {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return f.format(new java.util.Date(ts * 1000));
        } catch (Exception e) { return ""; }
    }
}
