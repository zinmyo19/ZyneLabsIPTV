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

    public void updateAccount(PlAccount a) {
        List<PlAccount> l = accounts();
        for (int i = 0; i < l.size(); i++) if (l.get(i).id.equals(a.id)) l.set(i, a);
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

    // app theme (unified palette shared across Dominic's apps)
    public String theme() { return sp.getString("theme", "calm"); }
    public void setTheme(String t) { sp.edit().putString("theme", t).apply(); }

    public static final String DEFAULT_GROUP_LINK = "https://t.me/+7I8oPYJp-fE3ZTY1";

    public String groupLink() { return sp.getString("group_link", DEFAULT_GROUP_LINK); }
    public void setGroupLink(String u) { sp.edit().putString("group_link", u).apply(); }

    // player buffer size in seconds (user-adjustable, like OTT Navigator)
    public int bufferSecs() { return sp.getInt("buffer_secs", 90); }
    public void setBufferSecs(int s) { sp.edit().putInt("buffer_secs", s).apply(); }

    // video scale mode (OTT-style zoom 70%–140%, user-adjustable)
    public float videoScale() { return sp.getFloat("video_scale", 1.0f); }
    public void setVideoScale(float s) { sp.edit().putFloat("video_scale", s).apply(); }

    // VOD resume position ("continue watching", like OTT Navigator)
    private String resumeKey(String accId, String chanKey) {
        return "rz_" + (accId + "|" + chanKey).hashCode();
    }
    public long resumePos(String accId, String chanKey) {
        return sp.getLong(resumeKey(accId, chanKey), 0);
    }
    public void setResumePos(String accId, String chanKey, long ms) {
        sp.edit().putLong(resumeKey(accId, chanKey), ms).apply();
    }
    public void clearResumePos(String accId, String chanKey) {
        sp.edit().remove(resumeKey(accId, chanKey)).apply();
    }

    // ---- parental control (OTT-style) ----
    // The PIN is stored as SHA-256(salt + pin) — never plaintext.
    private String pinSalt() {
        String s = sp.getString("pin_salt", "");
        if (s.isEmpty()) {
            s = UUID.randomUUID().toString();
            sp.edit().putString("pin_salt", s).apply();
        }
        return s;
    }
    private static String sha256(String s) {
        try {
            java.security.MessageDigest md =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "!" + s;
        }
    }
    /** Stored PIN hash (empty = no PIN). Never the plaintext PIN. */
    public String parentalPin() { return sp.getString("parental_pin_hash", ""); }
    public boolean hasParentalPin() { return !parentalPin().isEmpty(); }
    public void setParentalPin(String pin) {
        if (pin == null || pin.isEmpty()) {
            sp.edit().remove("parental_pin_hash").apply();
            return;
        }
        sp.edit().putString("parental_pin_hash",
                sha256(pinSalt() + "|" + pin)).apply();
    }
    public boolean checkParentalPin(String pin) {
        if (pin == null) return false;
        String stored = parentalPin();
        if (stored.isEmpty()) return false;
        if (stored.equals(sha256(pinSalt() + "|" + pin))) return true;
        // one-time migration: v3.15 stored the PIN as plaintext
        String legacy = sp.getString("parental_pin", "");
        if (!legacy.isEmpty() && legacy.equals(pin)) {
            setParentalPin(pin);
            sp.edit().remove("parental_pin").apply();
            return true;
        }
        return false;
    }
    public boolean hideAdult() { return sp.getBoolean("hide_adult", false); }
    public void setHideAdult(boolean h) { sp.edit().putBoolean("hide_adult", h).apply(); }
    public boolean adultLocked() { return hasParentalPin() && hideAdult(); }

    // ---- radio handling ----
    /** Show radio stations inside the Live TV grid (default: separate section). */
    public boolean showRadioInLive() { return sp.getBoolean("show_radio_live", false); }
    public void setShowRadioInLive(boolean b) {
        sp.edit().putBoolean("show_radio_live", b).apply();
    }

    // ---- hidden / renamed channels (OTT-style channel manager) ----
    public java.util.Set<String> hiddenChannels(String accId) {
        return new java.util.HashSet<>(
                sp.getStringSet("hidden_" + accId, new java.util.HashSet<String>()));
    }
    public void setChannelHidden(String accId, String key, boolean hide) {
        setChannelHidden(accId, key, hide, null);
    }
    /** Hide/unhide; when hiding, remember the display name for the manager UI. */
    public void setChannelHidden(String accId, String key, boolean hide, String dispName) {
        java.util.Set<String> s = hiddenChannels(accId);
        if (hide) s.add(key); else s.remove(key);
        sp.edit().putStringSet("hidden_" + accId, s).apply();
        setHiddenName(accId, key, hide ? dispName : null);
    }
    // per-account JSON maps (enumerable, so backup/restore can include them)
    private java.util.Map<String, String> strMap(String prefKey) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        try {
            org.json.JSONObject o = new org.json.JSONObject(
                    sp.getString(prefKey, "{}"));
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                m.put(k, o.optString(k, ""));
            }
        } catch (Exception ignored) {}
        return m;
    }
    private void saveStrMap(String prefKey, java.util.Map<String, String> m) {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            for (java.util.Map.Entry<String, String> e : m.entrySet())
                o.put(e.getKey(), e.getValue());
            sp.edit().putString(prefKey, o.toString()).apply();
        } catch (Exception ignored) {}
    }
    public java.util.Map<String, String> hiddenNames(String accId) {
        return strMap("hnms_" + accId);
    }
    public String hiddenName(String accId, String key) {
        String n = hiddenNames(accId).get(key);
        return n == null ? "" : n;
    }
    public java.util.Map<String, String> renames(String accId) {
        return strMap("rnms_" + accId);
    }
    public String customName(String accId, String key) {
        String n = renames(accId).get(key);
        return n == null ? "" : n;
    }
    public void setCustomName(String accId, String key, String name) {
        java.util.Map<String, String> m = renames(accId);
        if (name == null || name.trim().isEmpty()) m.remove(key);
        else m.put(key, name.trim());
        saveStrMap("rnms_" + accId, m);
    }
    private void setHiddenName(String accId, String key, String name) {
        java.util.Map<String, String> m = hiddenNames(accId);
        if (name == null || name.isEmpty()) m.remove(key);
        else m.put(key, name);
        saveStrMap("hnms_" + accId, m);
    }
}
