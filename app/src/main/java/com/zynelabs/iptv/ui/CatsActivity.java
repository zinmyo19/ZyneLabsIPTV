package com.zynelabs.iptv.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.zynelabs.iptv.data.Cats;
import com.zynelabs.iptv.data.Channel;
import com.zynelabs.iptv.data.PlAccount;
import com.zynelabs.iptv.data.PlaylistLoader;
import com.zynelabs.iptv.data.Store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * OTT-style category browser: one clean list of plain text rows
 * (icon + name + count). Tapping a row opens the channel list.
 * Data is cache-first: instant from disk, refreshed in the background.
 */
public class CatsActivity extends Activity {
    private Store store;
    private PlAccount acc;
    private int tab = Channel.LIVE;

    private LinearLayout box;
    private ProgressBar loading;
    private TextView statusText;
    private Button retryBtn;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable watchdog;
    private boolean dataReady = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.applyTheme(this);
        store = new Store(this);
        acc = store.account(getIntent().getStringExtra("accountId"));
        if (acc == null) {
            finish();
            return;
        }
        tab = getIntent().getIntExtra("tab", Channel.LIVE);
        final String title = tab == Channel.VOD ? "🎬 Movie Categories"
                : tab == Channel.SERIES ? "📼 Series Categories"
                : "📺 Live TV Categories";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.PAPER);
        int p = Ui.dp(this, 12);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(p, p, p, p);
        Button back = Ui.barBtn(this, "‹", 22);
        back.setTextColor(Ui.TEAL);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        top.addView(back);
        TextView tv = Ui.label(this, title, 17, Ui.INK, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(tlp);
        top.addView(tv);
        root.addView(top);

        loading = new ProgressBar(this);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                Ui.dp(this, 48), Ui.dp(this, 48));
        llp.gravity = Gravity.CENTER;
        loading.setLayoutParams(llp);
        root.addView(loading);
        statusText = Ui.label(this, "Loading…", 13, Ui.MUTED, false);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(p, Ui.dp(this, 8), p, Ui.dp(this, 8));
        root.addView(statusText);
        retryBtn = Ui.flatBtn(this, "↻ Retry", 15);
        retryBtn.setTextColor(Ui.TEAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                Ui.dp(this, 160), ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.gravity = Gravity.CENTER;
        retryBtn.setLayoutParams(rlp);
        retryBtn.setVisibility(View.GONE);
        retryBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startLoad(); }
        });
        root.addView(retryBtn);

        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(p, 0, p, p);
        ScrollView sv = new ScrollView(this);
        sv.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        startLoad();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (watchdog != null) handler.removeCallbacks(watchdog);
    }

    private void startLoad() {
        dataReady = false;
        box.removeAllViews();
        loading.setVisibility(View.VISIBLE);
        retryBtn.setVisibility(View.GONE);
        statusText.setText("Loading…");
        if (watchdog != null) handler.removeCallbacks(watchdog);
        watchdog = new Runnable() {
            @Override public void run() {
                if (!dataReady && !isFinishing()) showError("Timed out loading playlist.");
            }
        };
        handler.postDelayed(watchdog, 90000);
        PlaylistLoader.start(this, acc, new PlaylistLoader.Listener() {
            @Override public void onCached(List<Channel> l, List<Channel> v,
                                           List<Channel> s, long savedAt) {
                if (isFinishing()) return;
                render(tab == Channel.VOD ? v : tab == Channel.SERIES ? s : l);
            }
            @Override public void onProgress(String phase) {
                if (!dataReady && !isFinishing()) statusText.setText(phase);
            }
            @Override public void onFresh(List<Channel> l, List<Channel> v,
                                          List<Channel> s) {
                if (isFinishing()) return;
                render(tab == Channel.VOD ? v : tab == Channel.SERIES ? s : l);
            }
            @Override public void onError(String err) {
                if (isFinishing()) return;
                if (dataReady) {
                    Toast.makeText(CatsActivity.this,
                            "Refresh failed: " + err, Toast.LENGTH_SHORT).show();
                } else {
                    showError("Couldn't load playlist: " + err);
                }
            }
        });
    }

    private void showError(String msg) {
        if (watchdog != null) handler.removeCallbacks(watchdog);
        loading.setVisibility(View.GONE);
        statusText.setText(msg);
        retryBtn.setVisibility(View.VISIBLE);
    }

    private void render(List<Channel> list) {
        dataReady = true;
        if (watchdog != null) handler.removeCallbacks(watchdog);
        loading.setVisibility(View.GONE);
        retryBtn.setVisibility(View.GONE);
        statusText.setText("");
        box.removeAllViews();
        if (tab == Channel.LIVE) {
            Map<String, Integer> counts = Cats.counts(list);
            for (String cat : Cats.ORDER) {
                Integer n = counts.get(cat);
                if (n == null || n == 0) continue;
                if (store.adultLocked() && Cats.ADULT.equals(cat)) continue;
                final String c2 = cat;
                addRow(catIcon(cat), cat, String.valueOf(n),
                        new Runnable() {
                            @Override public void run() { openCat(c2); }
                        });
            }
        } else {
            // VOD / Series: provider groups merged by normalized key, A–Z
            Map<String, String> disp = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            Map<String, Integer> n = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (Channel c : list) {
                String key = Cats.groupKey(c.displayGroup());
                if (!disp.containsKey(key)) {
                    String d = Cats.normGroup(c.displayGroup());
                    disp.put(key, d.isEmpty() ? "Ungrouped" : d);
                }
                n.put(key, (n.containsKey(key) ? n.get(key) : 0) + 1);
            }
            List<String> keys = new ArrayList<>(disp.keySet());
            Collections.sort(keys, new Comparator<String>() {
                @Override public int compare(String a, String b) {
                    return disp.get(a).compareToIgnoreCase(disp.get(b));
                }
            });
            for (final String key : keys) {
                final int count = n.get(key);
                final String name = disp.get(key);
                addRow("📁", name, String.valueOf(count),
                        new Runnable() {
                            @Override public void run() { openGroup(key, name); }
                        });
            }
        }
        if (box.getChildCount() == 0) {
            TextView t = Ui.label(this, "No categories", 14, Ui.MUTED, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 40), 0, 0);
            box.addView(t);
        }
    }

    /** Split the leading emoji off a smart-category label for the row icon. */
    private String catIcon(String cat) {
        int sp = cat.indexOf(' ');
        return sp > 0 ? cat.substring(0, sp) : "📺";
    }

    private void addRow(String icon, String name, String count,
                        final Runnable open) {
        LinearLayout row = Ui.textRow(this, icon, name, count);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open.run(); }
        });
        box.addView(row);
        box.addView(Ui.divider(this));
    }

    private void openCat(String cat) {
        Intent i = new Intent(this, ChannelListActivity.class);
        i.putExtra("accountId", acc.id);
        i.putExtra("tab", Channel.LIVE);
        i.putExtra("title", cat);
        if (Cats.RADIO.equals(cat)) i.putExtra("filter", "radio");
        else i.putExtra("cat", cat);
        startActivity(i);
    }

    private void openGroup(String groupKey, String name) {
        Intent i = new Intent(this, ChannelListActivity.class);
        i.putExtra("accountId", acc.id);
        i.putExtra("tab", tab);
        i.putExtra("group", groupKey);
        i.putExtra("title", name);
        startActivity(i);
    }
}
