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
        store = new Store(this);
        build();
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
        LinearLayout vmRow = Ui.settingRowS(this, "🔳", "Channel view",
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

        LinearLayout sortRow = Ui.settingRowS(this, "🔤", "Sort channels",
                "a_z".equals(store.sortMode()) ? "A–Z" : "Default");
        sortRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String[] opts = {"Playlist order", "A–Z"};
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Sort channels")
                        .setItems(opts, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                store.setSortMode(w == 0 ? "default" : "a_z");
                                build();
                            }
                        }).show();
            }
        });
        root.addView(sortRow);

        // ---- player ----
        root.addView(Ui.sectionHeader(this, "PLAYER"));
        final int[] bufSecs = {15, 30, 60, 90, 120};
        LinearLayout bufRow = Ui.settingRowS(this, "📦", "Buffer size",
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
        LinearLayout expRow = Ui.settingRowS(this, "💾", "Backup playlists", "");
        expRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doExport(); }
        });
        root.addView(expRow);

        LinearLayout impRow = Ui.settingRowS(this, "📥", "Restore playlists", "");
        impRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doImport(); }
        });
        root.addView(impRow);

        LinearLayout cacheRow = Ui.settingRowS(this, "🧹", "Clear image cache", "");
        cacheRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ImageLoader.clear();
                toast("Image cache cleared");
            }
        });
        root.addView(cacheRow);

        LinearLayout favRow = Ui.settingRowS(this, "⭐", "Clear all favorites", "");
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

        // ---- contact (horizontal) ----
        root.addView(Ui.sectionHeader(this, "CONTACT"));
        LinearLayout cRow = new LinearLayout(this);
        cRow.setOrientation(LinearLayout.HORIZONTAL);
        cRow.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
        addContact(cRow, "🤖", "Bot", BOT_URL, false);
        addContact(cRow, "👥", "Group", store.groupLink(), true);
        addContact(cRow, "💻", "GitHub", GITHUB_URL, false);
        addContact(cRow, "🌐", "Web", SITE_URL, false);
        root.addView(cRow);

        // ---- about ----
        root.addView(Ui.sectionHeader(this, "ABOUT"));
        TextView about = Ui.label(this,
                "ZyneLabs IPTV v3.0 · ExoPlayer engine + TV Guide · Made with ♥ by ZyneLabs", 12, Ui.MUTED, false);
        about.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 8));
        root.addView(about);

        setContentView(scroll);
    }

    private void addContact(LinearLayout row, String icon, String label,
                            final String url, final boolean isGroup) {
        LinearLayout b = Ui.contactBtn(this, icon, label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        b.setLayoutParams(lp);
        b.setOnClickListener(new View.OnClickListener() {
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
            b.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    dlgGroupLink();
                    return true;
                }
            });
        }
        row.addView(b);
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
    private void doExport() {
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
                JSONArray arr = new JSONArray();
                for (PlAccount a : store.accounts()) arr.put(a.toJson());
                OutputStream out = getContentResolver().openOutputStream(uri);
                out.write(arr.toString().getBytes("UTF-8"));
                out.close();
                toast("Backup saved");
            } else if (req == IMPORT_REQ) {
                InputStream in = getContentResolver().openInputStream(uri);
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                JSONArray arr = new JSONArray(new String(bos.toByteArray(), "UTF-8"));
                List<PlAccount> cur = store.accounts();
                int added = 0;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    PlAccount a = PlAccount.fromJson(o);
                    a.id = UUID.randomUUID().toString(); // avoid id collisions
                    cur.add(a);
                    added++;
                }
                store.saveAccounts(cur);
                toast(added + " playlist(s) restored");
            }
        } catch (Exception e) {
            toast("Failed: invalid file");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
