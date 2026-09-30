package com.zynelabs.iptv.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.zynelabs.iptv.R;
import com.zynelabs.iptv.data.Channel;
import com.zynelabs.iptv.data.ChannelRepo;
import com.zynelabs.iptv.data.Cats;
import com.zynelabs.iptv.data.ImageLoader;
import com.zynelabs.iptv.data.PlAccount;
import com.zynelabs.iptv.data.Store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** OTT-style home: vertical sections, each with a horizontal rail of channel tiles. */
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

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        String id = getIntent().getStringExtra("accountId");
        acc = store.account(id);
        if (acc == null) { finish(); return; }
        build();
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!all.isEmpty()) buildRails(); // refresh favorite stars
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
        Button epgBtn = Ui.barBtn(this, "📅", 18);
        epgBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(SectionsActivity.this, EpgActivity.class);
                i.putExtra("accountId", acc.id);
                startActivity(i);
            }
        });
        top.addView(epgBtn);
        Button setBtn = Ui.barBtn(this, "⚙", 20);
        setBtn.setTextColor(Ui.MUTED);
        setBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SectionsActivity.this, SettingsActivity.class));
            }
        });
        top.addView(setBtn);
        Button libBtn = Ui.barBtn(this, "🎞", 18);
        libBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(SectionsActivity.this, MediaLibraryActivity.class);
                i.putExtra("accountId", acc.id);
                startActivity(i);
            }
        });
        top.addView(libBtn);
        root.addView(top);

        loading = new ProgressBar(this);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                Ui.dp(this, 48), Ui.dp(this, 48));
        llp.gravity = Gravity.CENTER;
        loading.setLayoutParams(llp);
        root.addView(loading);
        statusText = Ui.label(this, "Loading…", 13, Ui.MUTED, false);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText);

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
    }

    // ---------------- data ----------------
    private void load() {
        loading.setVisibility(View.VISIBLE);
        ChannelRepo.load(this, acc, new ChannelRepo.Callback() {
            @Override public void onResult(List<Channel> l, List<Channel> v,
                                           List<Channel> s, String err) {
                loading.setVisibility(View.GONE);
                if (err != null) {
                    statusText.setText("Failed to load: " + err);
                    return;
                }
                live = l; vod = v; series = s;
                all = new ArrayList<>();
                all.addAll(live); all.addAll(vod); all.addAll(series);
                statusText.setText("");
                buildRails();
            }
        });
    }

    private void buildRails() {
        sections.removeAllViews();
        Map<String, Channel> byKey = new HashMap<>();
        for (Channel c : all) byKey.put(c.key, c);

        List<Channel> favs = new ArrayList<>();
        for (String k : store.favorites(acc.id)) {
            Channel c = byKey.get(k);
            if (c != null) favs.add(c);
        }
        if (!favs.isEmpty()) addRail("★ Favorites", favs, "fav", false);

        List<Channel> recent = new ArrayList<>();
        for (String k : store.recent(acc.id)) {
            Channel c = byKey.get(k);
            if (c != null) recent.add(c);
        }
        if (!recent.isEmpty()) addRail("🕘 Recently Watched", recent, "recent", true);

        if (!live.isEmpty()) addLiveRail();
        if (!vod.isEmpty()) addRail("🎬 Movies", vod, null, false);
        if (!series.isEmpty()) addRail("📼 Series", series, null, false);

        if (sections.getChildCount() == 0) {
            sections.addView(Ui.emptyView(this, "No channels found."));
        }
    }

    // ---------------- rails ----------------
    /** Live TV section: header, smart-category chips, then the channel rail. */
    private void addLiveRail() {
        addHeader("📺 Live TV", live, null);
        // category chips: jump straight into a category, no › needed
        HorizontalScrollView csv = new HorizontalScrollView(this);
        csv.setHorizontalScrollBarEnabled(false);
        LinearLayout cats = new LinearLayout(this);
        cats.setOrientation(LinearLayout.HORIZONTAL);
        int p = Ui.dp(this, 4);
        cats.setPadding(p, 0, p, Ui.dp(this, 4));
        Map<String, Integer> counts = Cats.counts(live);
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            final String cat = e.getKey();
            Button b = Ui.chip(this, cat + " (" + e.getValue() + ")", false);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openBrowseCat(cat); }
            });
            cats.addView(b);
        }
        csv.addView(cats);
        sections.addView(csv);
        addTileRow(live, false);
    }

    private void addRail(String title, final List<Channel> list, final String filter,
                         boolean compact) {
        addHeader(title, list, filter);
        addTileRow(list, compact);
    }

    private void addHeader(String title, final List<Channel> list, final String filter) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        int hp = Ui.dp(this, 4);
        head.setPadding(hp, Ui.dp(this, 14), hp, Ui.dp(this, 6));
        TextView t = Ui.label(this, title, 15, Ui.GOLD, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        t.setLayoutParams(tlp);
        head.addView(t);
        TextView allBtn = Ui.label(this, list.size() + " ›", 13, Ui.TEAL, false);
        allBtn.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        allBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openBrowse(list, filter); }
        });
        head.addView(allBtn);
        sections.addView(head);
    }

    private void addTileRow(final List<Channel> list, boolean compact) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int n = Math.min(list.size(), 15);
        for (int i = 0; i < n; i++) {
            final Channel c = list.get(i);
            final int pos = i;
            row.addView(tile(c, compact, new View.OnClickListener() {
                @Override public void onClick(View v) { play(c, list, pos); }
            }));
        }
        hsv.addView(row);
        sections.addView(hsv);
    }

    private LinearLayout tile(final Channel c, boolean compact, View.OnClickListener click) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setBackground(Ui.cardBgGrad(this));
        int p = Ui.dp(this, compact ? 4 : 6);
        t.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Ui.dp(this, compact ? 76 : 104), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, Ui.dp(this, 8), 0);
        t.setLayoutParams(lp);
        t.setFocusable(true);

        ImageView iv = new ImageView(this);
        iv.setLayoutParams(new LinearLayout.LayoutParams(
                Ui.dp(this, compact ? 64 : 92), Ui.dp(this, compact ? 42 : 60)));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ImageLoader.load(c.logo, iv, R.drawable.ic_launcher);
        t.addView(iv);

        TextView tv = Ui.label(this, c.name, compact ? 10 : 11, Ui.INK, false);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(compact ? 1 : 2);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(0, Ui.dp(this, 4), 0, 0);
        tv.setLayoutParams(tlp);
        t.addView(tv);

        TextView star = Ui.label(this,
                store.isFav(acc.id, c.key) ? "★" : "", 11, Ui.GOLD, false);
        star.setGravity(Gravity.CENTER);
        t.addView(star);

        t.setOnClickListener(click);
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                store.toggleFav(acc.id, c.key);
                toast(store.isFav(acc.id, c.key)
                        ? "★ Added to favorites" : "☆ Removed from favorites");
                buildRails();
                return true;
            }
        });
        return t;
    }

    // ---------------- navigation ----------------
    private void play(Channel c, List<Channel> list, int pos) {
        if (c.kind == Channel.SERIES) {
            SeriesDialog.show(this, acc, c);
            return;
        }
        PlayerQueue.setAccount(acc.id);
        PlayerQueue.set(list, pos);
        startActivity(new Intent(this, PlayerActivity.class));
    }

    private void openBrowse(List<Channel> list, String filter) {
        Intent i = new Intent(this, HomeActivity.class);
        i.putExtra("accountId", acc.id);
        if (!list.isEmpty()) i.putExtra("tab", list.get(0).kind);
        if (filter != null) i.putExtra("filter", filter);
        startActivity(i);
    }

    private void openBrowseCat(String cat) {
        Intent i = new Intent(this, HomeActivity.class);
        i.putExtra("accountId", acc.id);
        i.putExtra("tab", Channel.LIVE);
        i.putExtra("cat", cat);
        startActivity(i);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
