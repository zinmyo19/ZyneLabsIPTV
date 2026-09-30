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
import com.zynelabs.iptv.data.Store;
import com.zynelabs.iptv.data.XtreamClient;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.List;

/** First screen: manage playlist accounts (M3U link / M3U file / Xtream Codes). */
public class SetupActivity extends Activity {

    private static final int PICK_M3U = 1001;
    private Store store;
    private LinearLayout listBox;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
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

        TextView sec = Ui.label(this, "ADD PLAYLIST", 13, Ui.MUTED, true);
        root.addView(sec);
        root.addView(Ui.spacer(this, 8));

        LinearLayout tileRow = new LinearLayout(this);
        tileRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout bUrl = Ui.tileButton(this, "🔗", "M3U Link");
        bUrl.setLayoutParams(tileLp());
        bUrl.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlgM3uUrl(); }
        });
        tileRow.addView(bUrl);

        LinearLayout bFile = Ui.tileButton(this, "📁", "M3U File");
        bFile.setLayoutParams(tileLp());
        bFile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickFile(); }
        });
        tileRow.addView(bFile);

        LinearLayout bXtream = Ui.tileButton(this, "⚡", "Xtream");
        bXtream.setLayoutParams(tileLp());
        bXtream.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlgXtream(); }
        });
        tileRow.addView(bXtream);

        LinearLayout bStalker = Ui.tileButton(this, "📡", "Stalker");
        bStalker.setLayoutParams(tileLp());
        bStalker.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlgStalker(); }
        });
        tileRow.addView(bStalker);
        root.addView(tileRow);
        root.addView(Ui.spacer(this, 10));

        TextView sec2 = Ui.label(this, "MY PLAYLISTS", 13, Ui.MUTED, true);
        root.addView(sec2);
        root.addView(Ui.spacer(this, 8));

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox);

        root.addView(Ui.spacer(this, 12));
        LinearLayout settings = Ui.settingRowS(this, "⚙", "Settings & About", "");
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SetupActivity.this, SettingsActivity.class));
            }
        });
        root.addView(settings);

        setContentView(scroll);
    }

    private LinearLayout.LayoutParams tileLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        int m = Ui.dp(this, 4);
        lp.setMargins(m, 0, m, 0);
        return lp;
    }

    private void refreshList() {
        listBox.removeAllViews();
        List<PlAccount> accs = store.accounts();
        if (accs.isEmpty()) {
            listBox.addView(Ui.emptyView(this, "No playlists yet.\nAdd one above to start watching."));
            return;
        }
        for (final PlAccount a : accs) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(Ui.cardBgGrad(this));
            row.setElevation(Ui.dp(this, 2));
            int p = Ui.dp(this, 14);
            row.setPadding(p, p, p, p);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, Ui.dp(this, 8));
            row.setLayoutParams(lp);

            row.addView(Ui.label(this, a.name, 16, Ui.INK, true));
            String kind = "m3u_url".equals(a.type) ? "M3U Link"
                    : "m3u_file".equals(a.type) ? "M3U File"
                    : "stalker".equals(a.type) ? "Stalker Portal" : "Xtream Codes";
            row.addView(Ui.label(this, kind, 12, Ui.MUTED, false));
            row.setFocusable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { open(a); }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    confirmDelete(a);
                    return true;
                }
            });
            listBox.addView(row);
        }
        TextView hint = Ui.label(this, "Tap to open · Long-press to delete", 11, Ui.MUTED, false);
        hint.setGravity(Gravity.CENTER);
        listBox.addView(hint);
    }

    private void open(PlAccount a) {
        Intent i = new Intent(this, SectionsActivity.class);
        i.putExtra("accountId", a.id);
        startActivity(i);
    }

    private void confirmDelete(final PlAccount a) {
        new AlertDialog.Builder(this)
                .setTitle("Delete playlist?")
                .setMessage(a.name)
                .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        if ("m3u_file".equals(a.type) && a.file != null && !a.file.isEmpty())
                            deleteFile(a.file);
                        store.removeAccount(a.id);
                        refreshList();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- M3U link ----------
    private void dlgM3uUrl() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 8);
        f.setPadding(p, p, p, p);
        final EditText name = Ui.field(this, "Playlist name (e.g. My IPTV)");
        final EditText url = Ui.field(this, "http://example.com/list.m3u8");
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
            String fname = "pl_" + System.currentTimeMillis() + ".m3u";
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                FileOutputStream out = openFileOutput(fname, MODE_PRIVATE);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close(); out.close();
                PlAccount a = new PlAccount();
                a.name = "M3U File";
                a.type = "m3u_file";
                a.file = fname;
                store.addAccount(a);
                refreshList();
                open(a);
            } catch (Exception e) {
                toast("Could not read file");
            }
        }
    }

    // ---------- Xtream ----------
    private void dlgXtream() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 8);
        f.setPadding(p, p, p, p);
        final EditText server = Ui.field(this, "Server URL (http://host:port)");
        final EditText user = Ui.field(this, "Username");
        final EditText pass = Ui.field(this, "Password");
        pass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
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
                        if (s.isEmpty() || u.isEmpty()) { toast("Fill server + username"); return; }
                        toast("Connecting…");
                        new Thread(new Runnable() {
                            @Override public void run() {
                                try {
                                    JSONObject ui = XtreamClient.login(s, u, pw);
                                    if (ui != null) {
                                        final PlAccount a = new PlAccount();
                                        a.name = "Xtream — " + u;
                                        a.type = "xtream";
                                        a.server = XtreamClient.normServer(s);
                                        a.user = u;
                                        a.pass = pw;
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
