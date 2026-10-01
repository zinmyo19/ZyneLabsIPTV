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

import com.zynelabs.iptv.data.Channel;
import com.zynelabs.iptv.data.Cats;
import com.zynelabs.iptv.data.PlAccount;
import com.zynelabs.iptv.data.PlaylistCache;
import com.zynelabs.iptv.data.PlaylistLoader;
import com.zynelabs.iptv.data.Store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider home: a flat section chooser. Plain text rows open the
 * OTT-style category browser, favorites, recent, radio, media library
 * and TV guide.
 *
 * <p>Data is cache-first: renders from disk instantly, then refreshes
 * quietly in the background. A 90s watchdog + Retry button replace the
 * old infinite spinner.</p>
 */
public class SectionsActivity extends Activity {

    private Store store;
    private PlAccount acc;
    private List<Channel> live = new ArrayList<>();
    private List<Channel> vod = new ArrayList<>();
    private List<Channel> series = new ArrayList<>();
    private List<Channel> all = new ArrayList<>();
    private LinearLayout sections;
    private ProgressBar loading;
    private TextView statusText;
    private TextView updatedText;
    private Button retryBtn;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable watchdog;
    private boolean dataReady = false;
    /** True while a refresh is running after the old lists were dropped. */
    private boolean refreshing = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.applyTheme(this);
        store = new Store(this);
        String id = getIntent().getStringExtra("accountId");
        acc = store.account(id);
        if (acc == null) { finish(); return; }
        build();
        startLoad(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (dataReady && !all.isEmpty()) buildChooser(); // refresh favorite stars
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (watchdog != null) handler.removeCallbacks(watchdog);
    }

    // ---------------- UI ----------------
    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.PAPER);

        // top bar
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 12);
        top.setPadding(p, p, p, p);
        Button back = Ui.barBtn(this, "‹", 22);
        back.setTextColor(Ui.TEAL);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        top.addView(back);
        TextView title = Ui.label(this, acc.name, 17, Ui.INK, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tlp);
        top.addView(title);
        Button refBtn = Ui.barBtn(this, "↻", 18);
        refBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startLoad(false); }
        });
        top.addView(refBtn);
        Button setBtn = Ui.barBtn(this, "⚙", 20);
        setBtn.setTextColor(Ui.MUTED);
        setBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SectionsActivity.this, SettingsActivity.class));
            }
        });
        top.addView(setBtn);
        root.addView(top);

        updatedText = Ui.label(this, "", 11, Ui.MUTED, false);
        updatedText.setPadding(p, 0, p, Ui.dp(this, 4));
        root.addView(updatedText);

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
            @Override public void onClick(View v) { startLoad(true); }
        });
        root.addView(retryBtn);

        ScrollView sv = new ScrollView(this);
        sections = new LinearLayout(this);
        sections.setOrientation(LinearLayout.VERTICAL);
        sections.setPadding(p, 0, p, p);
        sv.addView(sections);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sv.setLayoutParams(slp);
        root.addView(sv);

        setContentView(root);
        Ui.enableTvFocus(root);
    }

    // ---------------- data ----------------
    /**
     * Cache-first load. With showSpinner=false this is a quiet refresh that
     * keeps the current chooser on screen.
     */
    private void startLoad(final boolean showSpinner) {
        if (watchdog != null) handler.removeCallbacks(watchdog);
        if (dataReady) {
            // Drop the OLD channel lists BEFORE the refresh parses new
            // data, so old + new are never both fully resident (OOM on
            // 100k-entry playlists). The chooser stays on screen; taps
            // during the refresh get a "Refreshing…" hint.
            live = new ArrayList<>();
            vod = new ArrayList<>();
            series = new ArrayList<>();
            all = new ArrayList<>();
            refreshing = true;
            System.gc();
        }
        if (showSpinner) {
            dataReady = false;
            loading.setVisibility(View.VISIBLE);
            retryBtn.setVisibility(View.GONE);
            statusText.setText("Loading…");
        } else if (dataReady) {
            updatedText.setText("Refreshing…");
        }
        watchdog = new Runnable() {
            @Override public void run() {
                if (!dataReady && !isFinishing()) {
                    showError("Timed out loading playlist.");
                }
            }
        };
        handler.postDelayed(watchdog, 90000);
        PlaylistLoader.start(this, acc, new PlaylistLoader.Listener() {
            @Override public void onCached(List<Channel> l, List<Channel> v,
                                           List<Channel> s, long savedAt) {
                if (isFinishing()) return;
                setLists(l, v, s);
                updatedText.setText("Updated " + PlaylistCache.ago(savedAt)
                        + " · refreshing…");
            }
            @Override public void onProgress(String phase) {
                if (showSpinner && !dataReady && !isFinishing())
                    statusText.setText(phase);
            }
            @Override public void onFresh(List<Channel> l, List<Channel> v,
                                          List<Channel> s) {
                if (isFinishing()) return;
                setLists(l, v, s);
                updatedText.setText("Updated just now");
                // Persist the subscription expiry refreshed by ChannelRepo
                // during the background load (Xtream only).
                if (acc.isXtream()) store.updateAccount(acc);
            }
            @Override public void onError(String err) {
                if (isFinishing()) return;
                refreshing = false;
                if (dataReady) {
                    updatedText.setText("Refresh failed — showing saved data");
                    Toast.makeText(SectionsActivity.this,
                            "Refresh failed: " + err, Toast.LENGTH_SHORT).show();
                    // The in-memory lists were dropped before the refresh;
                    // re-read the disk cache so navigation keeps working.
                    new Thread(new Runnable() {
                        @Override public void run() {
                            final PlaylistCache.Data d =
                                    PlaylistCache.load(getFilesDir(), acc.id);
                            if (d == null || isFinishing()) return;
                            handler.post(new Runnable() {
                                @Override public void run() {
                                    setLists(d.live, d.vod, d.series);
                                }
                            });
                        }
                    }).start();
                } else {
                    showError("Couldn't load playlist: " + err);
                }
            }
        });
    }

    private void setLists(List<Channel> l, List<Channel> v, List<Channel> s) {
        live = l; vod = v; series = s;
        all = new ArrayList<>();
        all.addAll(live); all.addAll(vod); all.addAll(series);
        PlayerQueue.setAccount(acc.id);
        dataReady = true;
        refreshing = false;
        if (watchdog != null) handler.removeCallbacks(watchdog);
        loading.setVisibility(View.GONE);
        retryBtn.setVisibility(View.GONE);
        statusText.setText("");
        buildChooser();
    }

    private void showError(String msg) {
        if (watchdog != null) handler.removeCallbacks(watchdog);
        refreshing = false;
        loading.setVisibility(View.GONE);
        statusText.setText(msg);
        retryBtn.setVisibility(View.VISIBLE);
    }

    /** True when a tap should wait: a refresh is running on dropped lists. */
    private boolean tapGuard() {
        if (refreshing) {
            Toast.makeText(this, "Refreshing playlist…", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    // ---------------- section chooser ----------------
    private void buildChooser() {
        sections.removeAllViews();
        Map<String, Channel> byKey = new HashMap<>();
        for (Channel c : all) byKey.put(c.key, c);

        List<Channel> tvLive = new ArrayList<>();
        List<Channel> radio = new ArrayList<>();
        for (Channel c : live) {
            if (Cats.RADIO.equals(c.getSmartCat())) radio.add(c);
            else tvLive.add(c);
        }
        boolean showRadioInLive = store.showRadioInLive();

        // 📺 Live TV
        if (!tvLive.isEmpty() || !radio.isEmpty()) {
            int n = showRadioInLive ? live.size() : tvLive.size();
            addRow("📺", "Live TV", String.valueOf(n), new View.OnClickListener() {
                @Override public void onClick(View v) { if (tapGuard()) return; openCats(Channel.LIVE); }
            });
        }

        // 🎬 Movies
        if (!vod.isEmpty()) {
            addRow("🎬", "Movies", String.valueOf(vod.size()), new View.OnClickListener() {
                @Override public void onClick(View v) { if (tapGuard()) return; openCats(Channel.VOD); }
            });
        }

        // 📼 Series
        if (!series.isEmpty()) {
            addRow("📼", "Series", String.valueOf(series.size()), new View.OnClickListener() {
                @Override public void onClick(View v) { if (tapGuard()) return; openCats(Channel.SERIES); }
            });
        }

        // 📻 Radio — own row only when non-empty and hidden from Live TV
        if (!radio.isEmpty() && !showRadioInLive) {
            addRow("📻", "Radio", String.valueOf(radio.size()), new View.OnClickListener() {
                @Override public void onClick(View v) { if (tapGuard()) return;
                    Intent i = new Intent(SectionsActivity.this, ChannelListActivity.class);
                    i.putExtra("accountId", acc.id);
                    i.putExtra("tab", Channel.LIVE);
                    i.putExtra("filter", "radio");
                    startActivity(i);
                }
            });
        }

        // ★ Favorites
        List<Channel> favs = new ArrayList<>();
        for (String k : store.favorites(acc.id)) {
            Channel c = byKey.get(k);
            if (c != null) favs.add(c);
        }
        if (!favs.isEmpty()) {
            addRow("★", "Favorites", String.valueOf(favs.size()), new View.OnClickListener() {
                @Override public void onClick(View v) { if (tapGuard()) return;
                    Intent i = new Intent(SectionsActivity.this, ChannelListActivity.class);
                    i.putExtra("accountId", acc.id);
                    i.putExtra("tab", Channel.LIVE);
                    i.putExtra("filter", "fav");
                    startActivity(i);
                }
            });
        }

        // 🕘 Recently Watched
        List<Channel> recent = new ArrayList<>();
        for (String k : store.recent(acc.id)) {
            Channel c = byKey.get(k);
            if (c != null) recent.add(c);
        }
        if (!recent.isEmpty()) {
            addRow("🕘", "Recently Watched", String.valueOf(recent.size()), new View.OnClickListener() {
                @Override public void onClick(View v) { if (tapGuard()) return;
                    Intent i = new Intent(SectionsActivity.this, ChannelListActivity.class);
                    i.putExtra("accountId", acc.id);
                    i.putExtra("tab", Channel.LIVE);
                    i.putExtra("filter", "recent");
                    startActivity(i);
                }
            });
        }

        // 🎞 Media Library
        addRow("🎞", "Media Library", null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(SectionsActivity.this, MediaLibraryActivity.class);
                i.putExtra("accountId", acc.id);
                startActivity(i);
            }
        });

        // 📅 TV Guide
        addRow("📅", "TV Guide", null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(SectionsActivity.this, EpgActivity.class);
                i.putExtra("accountId", acc.id);
                startActivity(i);
            }
        });

        if (sections.getChildCount() == 0) {
            sections.addView(Ui.emptyView(this, "No channels found."));
        }
    }

    /** Plain text row + divider. */
    private void addRow(String icon, String title, String count,
                        View.OnClickListener click) {
        if (sections.getChildCount() > 0) sections.addView(Ui.divider(this));
        LinearLayout row = Ui.textRow(this, icon, title, count);
        row.setOnClickListener(click);
        sections.addView(row);
    }

    // ---------------- navigation ----------------
    /** OTT-style vertical category browser for a tab. */
    private void openCats(int kind) {
        Intent i = new Intent(this, CatsActivity.class);
        i.putExtra("accountId", acc.id);
        i.putExtra("tab", kind);
        startActivity(i);
    }
}
