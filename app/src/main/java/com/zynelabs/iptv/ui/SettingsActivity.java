package com.zynelabs.iptv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.zynelabs.iptv.data.ImageLoader;
import com.zynelabs.iptv.data.PlAccount;
import com.zynelabs.iptv.data.Store;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

/** Settings: appearance, data backup, contact links, about. */
public class SettingsActivity extends Activity {

    private static final int EXPORT_REQ = 2001;
    private static final int IMPORT_REQ = 2002;
    private Store store;
    private LinearLayout root;

    public static final String BOT_URL = "https://t.me/Dominic_aiBot";
    public static final String GITHUB_URL = "https://github.com/zinmyo19";
    public static final String SITE_URL = "https://dzinlabs-site.zynelabs.workers.dev/";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.applyTheme(this);
        store = new Store(this);
        // parental gate: settings are PIN-locked when parental control is on
        if (!store.parentalPin().isEmpty()) {
            askPin(new Runnable() {
                @Override public void run() { build(); }
            });
        } else {
            build();
        }
    }

    /** PIN entry dialog; runs ok() only on correct PIN, finishes otherwise. */
    private void askPin(final Runnable ok) {
        final EditText et = new EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setHint("4-digit PIN");
        int p = Ui.dp(this, 16);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(p, p / 2, p, p / 2);
        box.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Parental control")
                .setMessage("Enter PIN to open Settings")
                .setView(box)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (store.checkParentalPin(et.getText().toString())) {
                            ok.run();
                        } else {
                            toast("Wrong PIN");
                            finish();
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { finish(); }
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build(); // refresh values
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.PAPER);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        root.setPadding(p, p, p, p);
        scroll.addView(root);

        // header
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(22);
        back.setTextColor(Ui.TEAL);
        back.setBackgroundColor(0x00000000);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        head.addView(back);
        TextView title = Ui.label(this, "Settings", 20, Ui.INK, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMargins(Ui.dp(this, 8), 0, 0, 0);
        title.setLayoutParams(tlp);
        head.addView(title);
        root.addView(head);

        // ---- appearance ----
        root.addView(Ui.sectionHeader(this, "APPEARANCE"));
        LinearLayout themeRow = Ui.textRow(this, "🎨", "Theme",
                Ui.themeName(this));
        themeRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String[] names = Ui.THEME_NAMES;
                final String[] ids = Ui.THEME_IDS;
                int cur = 0;
                for (int i = 0; i < ids.length; i++)
                    if (ids[i].equals(store.theme())) cur = i;
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Theme")
                        .setSingleChoiceItems(names, cur,
                                new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                store.setTheme(ids[w]);
                                Ui.applyTheme(SettingsActivity.this);
                                d.dismiss();
                                build(); // redraw with new palette
                                toast("Theme: " + Ui.THEME_NAMES[w]);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        root.addView(themeRow);
        LinearLayout vmRow = Ui.textRow(this, "🔳", "Channel view",
                "grid".equals(store.viewMode()) ? "Grid" : "List");
        vmRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String[] opts = {"Grid", "List"};
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Channel view")
                        .setItems(opts, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                store.setViewMode(w == 0 ? "grid" : "list");
                                build();
                            }
                        }).show();
            }
        });
        root.addView(vmRow);

        String sortLabel = "default".equals(store.sortMode()) ? "Playlist order"
                : "a_z".equals(store.sortMode()) ? "A–Z" : "Channel number";
        LinearLayout sortRow = Ui.textRow(this, "🔤", "Sort channels", sortLabel);
        sortRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String[] opts = {"Playlist order", "A–Z", "Channel number"};
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Sort channels")
                        .setItems(opts, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                store.setSortMode(w == 0 ? "default"
                                        : w == 1 ? "a_z" : "number");
                                build();
                            }
                        }).show();
            }
        });
        root.addView(sortRow);

        // ---- player ----
        root.addView(Ui.sectionHeader(this, "PLAYER"));
        final int[] bufSecs = {15, 30, 60, 90, 120};
        LinearLayout bufRow = Ui.textRow(this, "📦", "Buffer size",
                store.bufferSecs() + " seconds");
        bufRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String[] opts = {"15 seconds", "30 seconds", "60 seconds",
                        "90 seconds", "120 seconds"};
                int cur = store.bufferSecs(), checked = 3;
                for (int i = 0; i < bufSecs.length; i++)
                    if (bufSecs[i] == cur) checked = i;
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Buffer size")
                        .setSingleChoiceItems(opts, checked,
                                new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                store.setBufferSecs(bufSecs[w]);
                                d.dismiss();
                                build();
                            }
                        }).show();
            }
        });
        root.addView(bufRow);

        // ---- data ----
        root.addView(Ui.sectionHeader(this, "DATA"));
        LinearLayout expRow = Ui.textRow(this, "💾", "Backup playlists", "");
        expRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doExport(); }
        });
        root.addView(expRow);

        LinearLayout impRow = Ui.textRow(this, "📥", "Restore playlists", "");
        impRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doImport(); }
        });
        root.addView(impRow);

        LinearLayout cacheRow = Ui.textRow(this, "🧹", "Clear image cache", "");
        cacheRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ImageLoader.clear();
                toast("Image cache cleared");
            }
        });
        root.addView(cacheRow);

        LinearLayout favRow = Ui.textRow(this, "⭐", "Clear all favorites", "");
        favRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Clear all favorites?")
                        .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                for (PlAccount a : store.accounts()) store.clearFavorites(a.id);
                                toast("Favorites cleared");
                            }
                        })
                        .setNegativeButton("Cancel", null).show();
            }
        });
        root.addView(favRow);

        // ---- channel manager (hidden channels, OTT-style) ----
        root.addView(Ui.sectionHeader(this, "CHANNELS"));
        LinearLayout hidRow = Ui.textRow(this, "🙈", "Hidden channels",
                countHidden() + " hidden");
        hidRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlgHiddenManager(); }
        });
        root.addView(hidRow);

        // ---- radio ----
        LinearLayout radioRow = Ui.textRow(this, "📻",
                "Show radio in Live TV", store.showRadioInLive() ? "On" : "Off");
        final TextView radioBadge = (TextView) radioRow.getChildAt(2);
        radioRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean on = !store.showRadioInLive();
                store.setShowRadioInLive(on);
                radioBadge.setText(on ? "On" : "Off");
                toast(on ? "Radio shows inside Live TV"
                         : "Radio has its own home section");
            }
        });
        root.addView(radioRow);

        // ---- parental control (OTT-style) ----
        root.addView(Ui.sectionHeader(this, "PARENTAL"));
        final boolean hasPin = !store.parentalPin().isEmpty();
        LinearLayout pinRow = Ui.textRow(this, "🔐", "Parental control",
                !hasPin ? "Off" : (store.hideAdult() ? "On — adult hidden" : "On"));
        pinRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlgParental(); }
        });
        root.addView(pinRow);

        // ---- contact ----
        root.addView(Ui.sectionHeader(this, "CONTACT"));
        addContactRow("🤖", "Telegram Bot", BOT_URL, false);
        addContactRow("👥", "Telegram Group", store.groupLink(), true);
        addContactRow("💻", "GitHub", GITHUB_URL, false);
        addContactRow("🌐", "Website", SITE_URL, false);

        // ---- about ----
        root.addView(Ui.sectionHeader(this, "ABOUT"));
        TextView about = Ui.label(this,
                "ZyneLabs IPTV v3.21 · ExoPlayer engine + TV Guide · Made with ♥ by ZyneLabs", 12, Ui.MUTED, false);
        about.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 8));
        root.addView(about);

        setContentView(scroll);
        Ui.enableTvFocus(scroll);
    }

    private void addContactRow(String icon, String label,
                               final String url, final boolean isGroup) {
        LinearLayout row = Ui.textRow(this, icon, label, "");
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (url == null || url.isEmpty()) {
                    if (isGroup) dlgGroupLink();
                    return;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    toast("Cannot open link");
                }
            }
        });
        if (isGroup) {
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    dlgGroupLink();
                    return true;
                }
            });
        }
        root.addView(row);
    }

    private void dlgGroupLink() {
        final EditText e = Ui.field(this, "https://t.me/…");
        String cur = store.groupLink();
        e.setText(cur.isEmpty() ? Store.DEFAULT_GROUP_LINK : cur);
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 8);
        f.setPadding(p, p, p, p);
        f.addView(e);
        new AlertDialog.Builder(this)
                .setTitle("Telegram Group Link")
                .setView(f)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        store.setGroupLink(e.getText().toString().trim());
                        build();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- backup / restore ----
    /** Full backup (v2): playlists + settings + favorites + hidden + renames.
     *  The parental PIN is never exported. Playlist logins ARE included —
     *  the user is warned to keep the file private. */
    private void doExport() {
        new AlertDialog.Builder(this)
                .setTitle("Full backup")
                .setMessage("Backs up playlists, settings, favorites, hidden channels " +
                        "and custom names.\n\n⚠️ The file contains your playlist " +
                        "login details — keep it private.")
                .setPositiveButton("Back up", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { startExport(); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startExport() {
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "zynelabs-iptv-backup.json");
            startActivityForResult(i, EXPORT_REQ);
        } catch (Exception e) {
            toast("Not supported on this device");
        }
    }

    private void doImport() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(i, "Choose backup file"), IMPORT_REQ);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == EXPORT_REQ) {
                JSONObject root = new JSONObject();
                root.put("format", "zynelabs-iptv-backup");
                root.put("version", 2);
                JSONArray arr = new JSONArray();
                for (PlAccount a : store.accounts()) arr.put(a.toJson());
                root.put("accounts", arr);
                JSONObject st = new JSONObject();
                st.put("theme", store.theme());
                st.put("view_mode", store.viewMode());
                st.put("sort_mode", store.sortMode());
                st.put("buffer_secs", store.bufferSecs());
                st.put("video_scale", (double) store.videoScale());
                st.put("hide_adult", store.hideAdult());
                st.put("show_radio_live", store.showRadioInLive());
                // NOTE: parental PIN is deliberately never exported
                root.put("settings", st);
                JSONObject favs = new JSONObject();
                JSONObject hids = new JSONObject();
                JSONObject hnms = new JSONObject();
                JSONObject rnms = new JSONObject();
                for (PlAccount a : store.accounts()) {
                    JSONArray fa = new JSONArray();
                    for (String k : store.favorites(a.id)) fa.put(k);
                    favs.put(a.id, fa);
                    JSONArray ha = new JSONArray();
                    for (String k : store.hiddenChannels(a.id)) ha.put(k);
                    hids.put(a.id, ha);
                    JSONObject hm = new JSONObject();
                    for (java.util.Map.Entry<String, String> e
                            : store.hiddenNames(a.id).entrySet()) hm.put(e.getKey(), e.getValue());
                    hnms.put(a.id, hm);
                    JSONObject rm = new JSONObject();
                    for (java.util.Map.Entry<String, String> e
                            : store.renames(a.id).entrySet()) rm.put(e.getKey(), e.getValue());
                    rnms.put(a.id, rm);
                }
                root.put("favorites", favs);
                root.put("hidden", hids);
                root.put("hidden_names", hnms);
                root.put("renames", rnms);
                OutputStream out = getContentResolver().openOutputStream(uri);
                out.write(root.toString().getBytes("UTF-8"));
                out.close();
                toast("Full backup saved");
            } else if (req == IMPORT_REQ) {
                InputStream in = getContentResolver().openInputStream(uri);
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                String txt = new String(bos.toByteArray(), "UTF-8").trim();
                List<PlAccount> cur = store.accounts();
                int added = 0;
                if (txt.startsWith("[")) {
                    // legacy v1: bare array of playlists
                    JSONArray arr = new JSONArray(txt);
                    for (int i = 0; i < arr.length(); i++) {
                        PlAccount a = PlAccount.fromJson(arr.getJSONObject(i));
                        a.id = UUID.randomUUID().toString();
                        cur.add(a);
                        added++;
                    }
                    store.saveAccounts(cur);
                    toast(added + " playlist(s) restored");
                } else {
                    // v2: full backup object — remap old account ids to new ones
                    JSONObject root = new JSONObject(txt);
                    if (!"zynelabs-iptv-backup".equals(root.optString("format")))
                        throw new Exception("not a ZyneLabs backup");
                    java.util.Map<String, String> idMap = new java.util.HashMap<>();
                    JSONArray arr = root.optJSONArray("accounts");
                    if (arr != null) for (int i = 0; i < arr.length(); i++) {
                        PlAccount a = PlAccount.fromJson(arr.getJSONObject(i));
                        String oldId = a.id;
                        a.id = UUID.randomUUID().toString();
                        idMap.put(oldId, a.id);
                        cur.add(a);
                        added++;
                    }
                    store.saveAccounts(cur);
                    JSONObject st = root.optJSONObject("settings");
                    if (st != null) {
                        store.setTheme(st.optString("theme", store.theme()));
                        store.setViewMode(st.optString("view_mode", store.viewMode()));
                        store.setSortMode(st.optString("sort_mode", store.sortMode()));
                        store.setBufferSecs(st.optInt("buffer_secs", store.bufferSecs()));
                        store.setVideoScale((float) st.optDouble("video_scale",
                                store.videoScale()));
                        store.setHideAdult(st.optBoolean("hide_adult", store.hideAdult()));
                        store.setShowRadioInLive(st.optBoolean("show_radio_live",
                                store.showRadioInLive()));
                    }
                    restoreStrSet(root.optJSONObject("favorites"), idMap, "fav");
                    restoreStrSet(root.optJSONObject("hidden"), idMap, "hid");
                    restoreStrMap(root.optJSONObject("hidden_names"), idMap, "hnm");
                    restoreStrMap(root.optJSONObject("renames"), idMap, "rnm");
                    Ui.applyTheme(this);
                    build();
                    toast(added + " playlist(s) + settings restored");
                }
            }
        } catch (Exception e) {
            toast("Failed: invalid file");
        }
    }

    private void restoreStrSet(JSONObject obj, java.util.Map<String, String> idMap,
                               String kind) {
        if (obj == null) return;
        java.util.Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            String oldId = it.next();
            String nid = idMap.get(oldId);
            if (nid == null) continue;
            JSONArray arr = obj.optJSONArray(oldId);
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                String k = arr.optString(i, null);
                if (k == null) continue;
                if ("fav".equals(kind)) store.toggleFav(nid, k);
                else store.setChannelHidden(nid, k, true);
            }
        }
    }

    private void restoreStrMap(JSONObject obj, java.util.Map<String, String> idMap,
                               String kind) {
        if (obj == null) return;
        java.util.Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            String oldId = it.next();
            String nid = idMap.get(oldId);
            if (nid == null) continue;
            JSONObject m = obj.optJSONObject(oldId);
            if (m == null) continue;
            java.util.Iterator<String> kit = m.keys();
            while (kit.hasNext()) {
                String k = kit.next();
                String v = m.optString(k, "");
                if ("rnm".equals(kind)) store.setCustomName(nid, k, v);
                else if (!v.isEmpty()) {
                    // hidden display name: re-hide keeps the cached name
                    java.util.Set<String> h = store.hiddenChannels(nid);
                    if (h.contains(k)) store.setChannelHidden(nid, k, true, v);
                }
            }
        }
    }

    private void dlgParental() {
        if (store.parentalPin().isEmpty()) {
            setNewPin();
            return;
        }
        final String[] opts = {
                (store.hideAdult() ? "✓ " : "") + "Hide adult channels",
                "Change PIN",
                "Remove parental control",
        };
        new AlertDialog.Builder(this)
                .setTitle("Parental control")
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (w == 0) {
                            store.setHideAdult(!store.hideAdult());
                            toast(store.hideAdult() ? "Adult channels hidden"
                                    : "Adult channels visible");
                            build();
                        } else if (w == 1) {
                            setNewPin();
                        } else {
                            store.setParentalPin("");
                            store.setHideAdult(false);
                            toast("Parental control off");
                            build();
                        }
                    }
                }).show();
    }

    private void setNewPin() {
        final EditText et = new EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setHint("New 4-digit PIN");
        LinearLayout box = pinBox(et);
        new AlertDialog.Builder(this)
                .setTitle("Set parental PIN")
                .setView(box)
                .setPositiveButton("Next", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        final String p1 = et.getText().toString().trim();
                        if (p1.length() < 4) {
                            toast("PIN must be at least 4 digits");
                            return;
                        }
                        final EditText et2 = new EditText(SettingsActivity.this);
                        et2.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                        et2.setHint("Confirm PIN");
                        new AlertDialog.Builder(SettingsActivity.this)
                                .setTitle("Confirm PIN")
                                .setView(pinBox(et2))
                                .setPositiveButton("Save",
                                        new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d2, int w2) {
                                        if (p1.equals(et2.getText().toString().trim())) {
                                            store.setParentalPin(p1);
                                            store.setHideAdult(true);
                                            toast("Parental control on — adult hidden");
                                            build();
                                        } else {
                                            toast("PINs do not match");
                                        }
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private LinearLayout pinBox(EditText et) {
        int p = Ui.dp(this, 16);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(p, p / 2, p, p / 2);
        box.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private int countHidden() {
        int n = 0;
        for (PlAccount a : store.accounts()) n += store.hiddenChannels(a.id).size();
        return n;
    }

    /** Channel manager: list hidden channels per account, unhide individually or all. */
    private void dlgHiddenManager() {
        final java.util.List<PlAccount> accs = new java.util.ArrayList<>();
        final java.util.List<String> labels = new java.util.ArrayList<>();
        final java.util.List<String> keys = new java.util.ArrayList<>();
        for (PlAccount a : store.accounts()) {
            for (String k : store.hiddenChannels(a.id)) {
                String nm = store.hiddenName(a.id, k);
                if (nm.isEmpty()) nm = "(unknown channel)";
                accs.add(a); keys.add(k);
                labels.add(nm + "  —  " + a.name);
            }
        }
        if (labels.isEmpty()) {
            toast("No hidden channels");
            return;
        }
        final String[] items = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Hidden channels — tap to unhide")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        PlAccount a = accs.get(w);
                        store.setChannelHidden(a.id, keys.get(w), false);
                        toast("Channel restored");
                        build();
                    }
                })
                .setNeutralButton("Unhide all", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        for (int i = 0; i < accs.size(); i++)
                            store.setChannelHidden(accs.get(i).id, keys.get(i), false);
                        toast("All channels restored");
                        build();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
