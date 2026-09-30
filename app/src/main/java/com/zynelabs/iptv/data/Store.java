package com.zynelabs.iptv.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** App-local storage: playlist accounts + favorites. Lives on the device only. */
public class Store {
    private static final String PREF = "zyne_iptv";
    private final SharedPreferences sp;

    public Store(Context c) {
        sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ---- accounts ----
    public List<PlAccount> accounts() {
        List<PlAccount> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString("accounts", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(PlAccount.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }

    public void saveAccounts(List<PlAccount> list) {
        JSONArray arr = new JSONArray();
        for (PlAccount a : list) arr.put(a.toJson());
        sp.edit().putString("accounts", arr.toString()).apply();
    }

    public void addAccount(PlAccount a) {
        if (a.id == null || a.id.isEmpty()) a.id = UUID.randomUUID().toString();
        List<PlAccount> l = accounts();
        l.add(a);
        saveAccounts(l);
    }

    public void removeAccount(String id) {
        List<PlAccount> l = accounts();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id.equals(id)) l.remove(i);
        saveAccounts(l);
        sp.edit().remove("fav_" + id).apply();
    }

    public PlAccount account(String id) {
        for (PlAccount a : accounts()) if (a.id.equals(id)) return a;
        return null;
    }

    // ---- favorites (per account) ----
    public Set<String> favorites(String accountId) {
        return new HashSet<>(sp.getStringSet("fav_" + accountId, new HashSet<String>()));
    }

    public boolean isFav(String accountId, String key) {
        return favorites(accountId).contains(key);
    }

    public void toggleFav(String accountId, String key) {
        Set<String> s = favorites(accountId);
        if (s.contains(key)) s.remove(key); else s.add(key);
        sp.edit().putStringSet("fav_" + accountId, s).apply();
    }

    public void clearFavorites(String accountId) {
        sp.edit().remove("fav_" + accountId).apply();
    }

    // ---- recent watched (per account, newest first, max 20) ----
    public List<String> recent(String accountId) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString("recent_" + accountId, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (Exception ignored) {}
        return out;
    }

    public void addRecent(String accountId, String key) {
        List<String> r = recent(accountId);
        r.remove(key);
        r.add(0, key);
        while (r.size() > 20) r.remove(r.size() - 1);
        JSONArray arr = new JSONArray();
        for (String k : r) arr.put(k);
        sp.edit().putString("recent_" + accountId, arr.toString()).apply();
    }

    // ---- prefs ----
    public String viewMode() { return sp.getString("view_mode", "list"); }
    public void setViewMode(String m) { sp.edit().putString("view_mode", m).apply(); }

    public String sortMode() { return sp.getString("sort_mode", "default"); }
    public void setSortMode(String m) { sp.edit().putString("sort_mode", m).apply(); }

    public static final String DEFAULT_GROUP_LINK = "https://t.me/+7I8oPYJp-fE3ZTY1";

    public String groupLink() { return sp.getString("group_link", DEFAULT_GROUP_LINK); }
    public void setGroupLink(String u) { sp.edit().putString("group_link", u).apply(); }

    // player buffer size in seconds (user-adjustable, like OTT Navigator)
    public int bufferSecs() { return sp.getInt("buffer_secs", 90); }
    public void setBufferSecs(int s) { sp.edit().putInt("buffer_secs", s).apply(); }
}
