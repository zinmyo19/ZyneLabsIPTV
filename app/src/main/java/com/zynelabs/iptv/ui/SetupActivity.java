package com.zynelabs.iptv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.zynelabs.iptv.R;
import com.zynelabs.iptv.data.PlAccount;
import com.zynelabs.iptv.data.PlaylistCache;
import com.zynelabs.iptv.data.Store;
import com.zynelabs.iptv.data.XtreamClient;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** First screen: manage playlist accounts (M3U link / M3U file / Xtream Codes). */
public class SetupActivity extends Activity {

    private static final int PICK_M3U = 1001;
    private Store store;
    private LinearLayout listBox;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.applyTheme(this);
        store = new Store(this);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.PAPER);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        root.setPadding(p, Ui.dp(this, 32), p, p);
        scroll.addView(root);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        int ls = Ui.dp(this, 84);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ls, ls);
        llp.gravity = Gravity.CENTER;
        llp.setMargins(0, 0, 0, Ui.dp(this, 8));
        logo.setLayoutParams(llp);
        root.addView(logo);

        TextView title = Ui.label(this, "ZYNE LABS", 26, Ui.GOLD, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = Ui.label(this, "IPTV PLAYER", 14, Ui.TEAL, true);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);
        root.addView(Ui.spacer(this, 18));

        LinearLayout addRow = Ui.textRow(this, "➕", "Add Playlist", "");
        addRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlgAddPlaylist(); }
        });
        root.addView(addRow);
        root.addView(Ui.divider(this));
        root.addView(Ui.spacer(this, 10));

        TextView sec2 = Ui.label(this, "PROVIDERS", 13, Ui.MUTED, true);
        root.addView(sec2);
        root.addView(Ui.spacer(this, 8));

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox);

        root.addView(Ui.spacer(this, 12));
        LinearLayout settings = Ui.textRow(this, "⚙", "Settings & About", "");
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SetupActivity.this, SettingsActivity.class));
            }
        });
        root.addView(settings);

        setContentView(scroll);
        Ui.enableTvFocus(scroll);
    }

    private void dlgAddPlaylist() {
        new AlertDialog.Builder(this)
                .setTitle("Add Playlist")
                .setItems(new String[]{"M3U Link", "M3U File", "Xtream",
                        "Add playlist from QR", "Stalker"},
                        new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        switch (w) {
                            case 0: dlgM3uUrl(); break;
                            case 1: pickFile(); break;
                            case 2: dlgXtream(); break;
                            case 3: startActivity(new Intent(SetupActivity.this,
                                    WebSetupActivity.class)); break;
                            case 4: dlgStalker(); break;
                        }
                    }
                })
                .show();
    }

    private void refreshList() {
        listBox.removeAllViews();
        final List<PlAccount> accs = store.accounts();
        if (accs.isEmpty()) {
            listBox.addView(Ui.emptyView(this, "No playlists yet.\nAdd one above to start watching."));
            return;
        }
        // OTT-style provider rows: plain text (icon + name + live count).
        // Counts come from the playlist disk cache — no re-scan needed.
        final Map<String, TextView> countViews = new HashMap<>();
        for (final PlAccount a : accs) {
            String icon = "m3u_url".equals(a.type) ? "🔗"
                    : "m3u_file".equals(a.type) ? "📁"
                    : "stalker".equals(a.type) ? "📡" : "⚡";
            LinearLayout row = Ui.textRow(this, icon, a.name, "…");
            // textRow children: icon, title, count, chevron
            final TextView countTv = (TextView) row.getChildAt(2);
            countViews.put(a.id, countTv);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { open(a); }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    providerOptions(a);
                    return true;
                }
            });
            listBox.addView(row);
            listBox.addView(Ui.divider(this));
        }
        TextView hint = Ui.label(this, "Tap to open · Long-press for options", 11, Ui.MUTED, false);
        hint.setGravity(Gravity.CENTER);
        listBox.addView(hint);
        new Thread(new Runnable() {
            @Override public void run() {
                final Map<String, String> labels = new HashMap<>();
                final Map<String, Integer> colors = new HashMap<>();
                for (PlAccount a : accs) {
                    PlaylistCache.Counts c =
                            PlaylistCache.readCounts(getFilesDir(), a.id);
                    String label = c == null ? "—" : fmtCount(c.live) + " live";
                    int color = Ui.MUTED;
                    // Subscription expiry (Xtream only): "⏳ 45d left · 12 Jan 2027".
                    if (a.expDate > 0) {
                        long days = (a.expDate - System.currentTimeMillis() / 1000) / 86400;
                        String date = fmtDate(a.expDate);
                        if (days < 0) {
                            label += "\n⚠️ Expired · " + date;
                            color = Ui.RED;
                        } else {
                            label += "\n⏳ " + days + "d left · " + date;
                            if (days < 7) color = Ui.GOLD; // expires soon: amber warning
                        }
                    }
                    labels.put(a.id, label);
                    colors.put(a.id, color);
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        for (Map.Entry<String, String> e : labels.entrySet()) {
                            TextView tv = countViews.get(e.getKey());
                            if (tv != null) {
                                tv.setText(e.getValue());
                                Integer col = colors.get(e.getKey());
                                if (col != null) tv.setTextColor(col);
                            }
                        }
                    }
                });
            }
        }).start();
    }

    /** "12 Jan 2027" from epoch seconds. */
    private static String fmtDate(long epochSec) {
        try {
            return new java.text.SimpleDateFormat("dd MMM yyyy",
                    java.util.Locale.ENGLISH)
                    .format(new java.util.Date(epochSec * 1000));
        } catch (Exception e) { return ""; }
    }

    private static String fmtCount(int n) {
        String s = String.valueOf(n);
        StringBuilder r = new StringBuilder();
        int c = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            r.append(s.charAt(i));
            if (++c % 3 == 0 && i > 0) r.append(',');
        }
        return r.reverse().toString();
    }

    private void open(PlAccount a) {
        Intent i = new Intent(this, SectionsActivity.class);
        i.putExtra("accountId", a.id);
        startActivity(i);
    }

    private void providerOptions(final PlAccount a) {
        new AlertDialog.Builder(this)
                .setTitle(a.name)
                .setItems(new String[]{"Details", "Rename", "Delete"},
                        new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        if (w == 0) dlgProviderDetails(a);
                        else if (w == 1) dlgRenameProvider(a);
                        else confirmDelete(a);
                    }
                })
                .show();
    }

    /** Subscription/provider details dialog: name, type, expiry, status. */
    private void dlgProviderDetails(final PlAccount a) {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 8);
        f.setPadding(p, p, p, p);
        addDetail(f, "Name", a.name);
        addDetail(f, "Type", typeLabel(a.type));
        if (a.isXtream() && a.user != null && !a.user.isEmpty())
            addDetail(f, "Username", a.user);
        if (a.expDate > 0) {
            long days = (a.expDate - System.currentTimeMillis() / 1000) / 86400;
            addDetail(f, "Expires", fmtDate(a.expDate));
            TextView rem = addDetail(f, "Remaining",
                    days < 0 ? "Expired" : days + (days == 1 ? " day" : " days"));
            TextView st;
            if (days < 0) {
                st = addDetail(f, "Status", "Expired");
                st.setTextColor(Ui.RED);
                rem.setTextColor(Ui.RED);
            } else if (days < 7) {
                st = addDetail(f, "Status", "Expires soon");
                st.setTextColor(Ui.GOLD);
                rem.setTextColor(Ui.GOLD);
            } else {
                st = addDetail(f, "Status", "Active");
                st.setTextColor(Ui.TEAL);
            }
        } else {
            addDetail(f, "Subscription",
                    a.isXtream() ? "No expiry info" : "No expiry (M3U playlist)");
        }
        new AlertDialog.Builder(this)
                .setTitle("Provider details")
                .setView(f)
                .setPositiveButton("OK", null)
                .show();
    }

    /** One "label : value" row for the details dialog; returns the value view. */
    private TextView addDetail(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int hp = Ui.dp(this, 4);
        row.setPadding(hp, Ui.dp(this, 6), hp, Ui.dp(this, 6));
        TextView l = Ui.label(this, label, 14, Ui.MUTED, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Ui.dp(this, 110),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        l.setLayoutParams(lp);
        row.addView(l);
        TextView v = Ui.label(this, value, 14, Ui.INK, false);
        row.addView(v);
        parent.addView(row);
        parent.addView(Ui.divider(this));
        return v;
    }

    private static String typeLabel(String type) {
        if ("m3u_url".equals(type)) return "M3U Link";
        if ("m3u_file".equals(type)) return "M3U File";
        if ("xtream".equals(type)) return "Xtream Codes";
        if ("stalker".equals(type)) return "Stalker Portal";
        return type == null ? "" : type;
    }

    private void dlgRenameProvider(final PlAccount a) {
        final EditText name = Ui.field(this, "Playlist name");
        name.setText(a.name);
        new AlertDialog.Builder(this)
                .setTitle("Rename playlist")
                .setView(name)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        String nm = name.getText().toString().trim();
                        if (!nm.isEmpty()) {
                            a.name = nm;
                            store.updateAccount(a);
                        }
                        refreshList();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(final PlAccount a) {
        new AlertDialog.Builder(this)
                .setTitle("Delete playlist?")
                .setMessage(a.name)
                .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        if ("m3u_file".equals(a.type) && a.file != null && !a.file.isEmpty())
                            deleteFile(a.file);
                        PlaylistCache.clear(getFilesDir(), a.id);
                        store.removeAccount(a.id);
                        refreshList();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- M3U link ----------
    private void dlgM3uUrl() { dlgM3uUrl(null); }

    private void dlgM3uUrl(String prefill) {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 8);
        f.setPadding(p, p, p, p);
        final EditText name = Ui.field(this, "Playlist name (e.g. My IPTV)");
        final EditText url = Ui.field(this, "http://example.com/list.m3u8");
        if (prefill != null && !prefill.isEmpty()) url.setText(prefill);
        f.addView(name);
        f.addView(Ui.spacer(this, 8));
        f.addView(url);
        new AlertDialog.Builder(this)
                .setTitle("Add M3U Link")
                .setView(f)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        String u = url.getText().toString().trim();
                        if (u.isEmpty()) { toast("Enter a link"); return; }
                        PlAccount a = new PlAccount();
                        a.name = name.getText().toString().trim();
                        if (a.name.isEmpty()) a.name = "M3U Playlist";
                        a.type = "m3u_url";
                        a.url = u;
                        store.addAccount(a);
                        refreshList();
                        open(a);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- M3U file ----------
    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(i, "Choose M3U file"), PICK_M3U);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_M3U && res == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            final String fname = "pl_" + System.currentTimeMillis() + ".m3u";
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                FileOutputStream out = openFileOutput(fname, MODE_PRIVATE);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close(); out.close();
                dlgNameM3uFile(fname);
            } catch (Exception e) {
                toast("Could not read file");
            }
        }
    }

    private void dlgNameM3uFile(final String fname) {
        final EditText name = Ui.field(this, "Playlist name");
        name.setText("M3U File");
        new AlertDialog.Builder(this)
                .setTitle("Name this playlist")
                .setView(name)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        String nm = name.getText().toString().trim();
                        PlAccount a = new PlAccount();
                        a.name = nm.isEmpty() ? "M3U File" : nm;
                        a.type = "m3u_file";
                        a.file = fname;
                        store.addAccount(a);
                        refreshList();
                        open(a);
                    }
                })
                .setNegativeButton("Cancel", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        PlAccount a = new PlAccount();
                        a.name = "M3U File";
                        a.type = "m3u_file";
                        a.file = fname;
                        store.addAccount(a);
                        refreshList();
                        open(a);
                    }
                })
                .show();
    }

    // ---------- Xtream ----------
    private void dlgXtream() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 8);
        f.setPadding(p, p, p, p);
        final EditText plName = Ui.field(this, "Playlist name (optional)");
        final EditText server = Ui.field(this, "Server URL (http://host:port)");
        final EditText user = Ui.field(this, "Username");
        final EditText pass = Ui.field(this, "Password");
        pass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        f.addView(plName);
        f.addView(Ui.spacer(this, 8));
        f.addView(server);
        f.addView(Ui.spacer(this, 8));
        f.addView(user);
        f.addView(Ui.spacer(this, 8));
        f.addView(pass);
        new AlertDialog.Builder(this)
                .setTitle("Xtream Codes Login")
                .setView(f)
                .setPositiveButton("Login", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        final String s = server.getText().toString().trim();
                        final String u = user.getText().toString().trim();
                        final String pw = pass.getText().toString();
                        final String plNameStr = plName.getText().toString().trim();
                        if (s.isEmpty() || u.isEmpty()) { toast("Fill server + username"); return; }
                        toast("Connecting…");
                        new Thread(new Runnable() {
                            @Override public void run() {
                                try {
                                    JSONObject ui = XtreamClient.login(s, u, pw);
                                    if (ui != null) {
                                        final PlAccount a = new PlAccount();
                                        a.name = plNameStr.isEmpty() ? "Xtream — " + u : plNameStr;
                                        a.type = "xtream";
                                        a.server = XtreamClient.normServer(s);
                                        a.user = u;
                                        a.pass = pw;
                                        a.expDate = XtreamClient.parseExpDate(ui);
                                        runOnUiThread(new Runnable() {
                                            @Override public void run() {
                                                store.addAccount(a);
                                                refreshList();
                                                open(a);
                                            }
                                        });
                                    } else {
                                        runOnUiThread(new Runnable() {
                                            @Override public void run() { toast("Login failed — check details"); }
                                        });
                                    }
                                } catch (final Exception e) {
                                    runOnUiThread(new Runnable() {
                                        @Override public void run() { toast("Connection failed"); }
                                    });
                                }
                            }
                        }).start();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- Stalker portal ----------
    private void dlgStalker() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 8);
        f.setPadding(p, p, p, p);
        final EditText name = Ui.field(this, "Name (e.g. My Portal)");
        final EditText portal = Ui.field(this, "Portal URL (http://host:port/c)");
        final EditText mac = Ui.field(this, "MAC (00:1A:79:XX:XX:XX)");
        f.addView(name);
        f.addView(Ui.spacer(this, 8));
        f.addView(portal);
        f.addView(Ui.spacer(this, 8));
        f.addView(mac);
        new AlertDialog.Builder(this)
                .setTitle("Stalker Portal Login")
                .setView(f)
                .setPositiveButton("Connect", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        final String pu = portal.getText().toString().trim();
                        final String mc = mac.getText().toString().trim().toUpperCase();
                        if (pu.isEmpty() || mc.isEmpty()) { toast("Fill portal + MAC"); return; }
                        if (!com.zynelabs.iptv.data.StalkerClient.validMac(mc)) {
                            toast("MAC looks wrong (00:1A:79:…)");
                            return;
                        }
                        toast("Connecting…");
                        new Thread(new Runnable() {
                            @Override public void run() {
                                try {
                                    com.zynelabs.iptv.data.StalkerClient.session(pu, mc);
                                    final PlAccount a = new PlAccount();
                                    String nm = name.getText().toString().trim();
                                    a.name = nm.isEmpty() ? "Stalker Portal" : nm;
                                    a.type = "stalker";
                                    a.url = com.zynelabs.iptv.data.StalkerClient.normPortal(pu);
                                    a.mac = mc;
                                    runOnUiThread(new Runnable() {
                                        @Override public void run() {
                                            store.addAccount(a);
                                            refreshList();
                                            open(a);
                                        }
                                    });
                                } catch (final Exception e) {
                                    runOnUiThread(new Runnable() {
                                        @Override public void run() { toast("Connection failed"); }
                                    });
                                }
                            }
                        }).start();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
