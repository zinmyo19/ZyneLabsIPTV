package com.zynelabs.iptv.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.zynelabs.iptv.R;
import com.zynelabs.iptv.data.Cats;
import com.zynelabs.iptv.data.Channel;
import com.zynelabs.iptv.data.ChannelRepo;
import com.zynelabs.iptv.data.ImageLoader;
import com.zynelabs.iptv.data.PlAccount;
import com.zynelabs.iptv.data.Store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Browse channels: tabs (Live/Movies/Series), groups, search, favorites. */
public class HomeActivity extends Activity {

    private Store store;
    private PlAccount acc;
    private List<Channel> live = new ArrayList<>();
    private List<Channel> vod = new ArrayList<>();
    private List<Channel> series = new ArrayList<>();
    private List<Channel> shown = new ArrayList<>();

    private int tab = Channel.LIVE;
    private String groupFilter = null;   // null = all (sub-group within a smart category)
    private String catFilter = null;     // null = all (smart top-level category)
    private String countryFilter = null; // null = all countries
    private boolean favOnly = false;
    private String query = "";

    private GridView grid;
    private ChannelAdapter adapter;
    private LinearLayout chipRow;
    private LinearLayout catRow;
    private LinearLayout countryRow;
    private HorizontalScrollView chipScroll;
    private LinearLayout tabRow;
    private ProgressBar loading;
    private TextView statusText;
    private EditText searchBox;
    private Map<String, List<String>> catIndex = new java.util.LinkedHashMap<>();
    private Map<String, Integer> catCounts = new java.util.LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        String id = getIntent().getStringExtra("accountId");
        acc = store.account(id);
        if (acc == null) { finish(); return; }
        int tabExtra = getIntent().getIntExtra("tab", -1);
        if (tabExtra >= 0) tab = tabExtra;
        String filter = getIntent().getStringExtra("filter");
        if ("fav".equals(filter)) favOnly = true;
        if ("recent".equals(filter)) catFilter = RECENT_FILTER;
        String cat = getIntent().getStringExtra("cat");
        if (cat != null && !cat.isEmpty()) catFilter = cat;
        String co = getIntent().getStringExtra("country");
        if (co != null && !co.isEmpty()) countryFilter = co;
        build();
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.setListMode("list".equals(store.viewMode()));
            applyFilter(); // re-applies sort + view prefs
        }
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
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tlp);
        top.addView(title);
        Button setBtn = Ui.barBtn(this, "⚙", 20);
        setBtn.setTextColor(Ui.MUTED);
        setBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
            }
        });
        top.addView(setBtn);
        Button libBtn = Ui.barBtn(this, "🎞", 18);
        libBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(HomeActivity.this, MediaLibraryActivity.class);
                i.putExtra("accountId", acc.id);
                startActivity(i);
            }
        });
        top.addView(libBtn);
        final Button favBtn = Ui.barBtn(this, favOnly ? "★" : "☆", 20);
        favBtn.setTextColor(favOnly ? Ui.GOLD : Ui.MUTED);
        favBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                favOnly = !favOnly;
                favBtn.setText(favOnly ? "★" : "☆");
                favBtn.setTextColor(favOnly ? Ui.GOLD : Ui.MUTED);
                applyFilter();
            }
        });
        top.addView(favBtn);
        Button searchBtn = Ui.barBtn(this, "🔍", 18);
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean show = searchBox.getVisibility() != View.VISIBLE;
                searchBox.setVisibility(show ? View.VISIBLE : View.GONE);
                if (!show) { searchBox.setText(""); }
            }
        });
        top.addView(searchBtn);
        root.addView(top);

        // search box (hidden)
        searchBox = Ui.field(this, "Search channels…");
        searchBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(p, 0, p, Ui.dp(this, 8));
        searchBox.setLayoutParams(slp);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                query = s.toString().toLowerCase().trim();
                applyFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        root.addView(searchBox);

        // tabs
        tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(p, 0, p, 0);
        root.addView(tabRow);
        root.addView(Ui.spacer(this, 8));

        // smart-category chips (auto buckets: Sports, Movies, News…)
        HorizontalScrollView csv = new HorizontalScrollView(this);
        csv.setHorizontalScrollBarEnabled(false);
        catRow = new LinearLayout(this);
        catRow.setOrientation(LinearLayout.HORIZONTAL);
        catRow.setPadding(p, 0, p, 0);
        csv.addView(catRow);
        root.addView(csv);
        root.addView(Ui.spacer(this, 6));

        // country chips (independent dimension: UK, USA, …)
        HorizontalScrollView ctyv = new HorizontalScrollView(this);
        ctyv.setHorizontalScrollBarEnabled(false);
        countryRow = new LinearLayout(this);
        countryRow.setOrientation(LinearLayout.HORIZONTAL);
        countryRow.setPadding(p, 0, p, 0);
        ctyv.addView(countryRow);
        root.addView(ctyv);
        root.addView(Ui.spacer(this, 6));

        // sub-group chips (normalized group-titles inside the chosen category)
        chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(p, 0, p, 0);
        chipScroll.addView(chipRow);
        root.addView(chipScroll);
        root.addView(Ui.spacer(this, 8));

        // loading + status
        loading = new ProgressBar(this);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                Ui.dp(this, 48), Ui.dp(this, 48));
        llp.gravity = Gravity.CENTER;
        loading.setLayoutParams(llp);
        root.addView(loading);
        statusText = Ui.label(this, "", 13, Ui.MUTED, false);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText);

        // grid
        grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(Ui.dp(this, 108));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(Ui.dp(this, 10));
        grid.setHorizontalSpacing(Ui.dp(this, 10));
        grid.setPadding(p, 0, p, p);
        grid.setClipToPadding(false);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        grid.setLayoutParams(glp);
        adapter = new ChannelAdapter();
        adapter.setListMode("list".equals(store.viewMode()));
        grid.setAdapter(adapter);
        // NOTE: item clicks are handled by a direct OnClickListener on each
        // item root in getView(). GridView's OnItemClickListener does NOT fire
        // here because the item root is focusable (needed for TV remote
        // D-pad navigation) — the focusable item swallows the tap.
        root.addView(grid);

        setContentView(root);
        buildTabs();
    }

    private void buildTabs() {
        tabRow.removeAllViews();
        addTab("📺 LIVE", Channel.LIVE);
        if (acc.isXtream()) {
            addTab("🎬 MOVIES", Channel.VOD);
            addTab("📚 SERIES", Channel.SERIES);
        }
    }

    private void addTab(String name, final int kind) {
        Button b = Ui.chip(this, name, tab == kind);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                tab = kind;
                catFilter = null;
                countryFilter = null;
                groupFilter = null;
                buildTabs();
                buildCats();
                buildCountries();
                buildSubChips();
                applyFilter();
            }
        });
        tabRow.addView(b);
    }

    // ---------------- data ----------------
    private void loadData() {
        loading.setVisibility(View.VISIBLE);
        statusText.setText("Loading playlist…");
        ChannelRepo.load(this, acc, new ChannelRepo.Callback() {
            @Override public void onResult(List<Channel> l, List<Channel> v,
                                           List<Channel> s, String err) {
                loading.setVisibility(View.GONE);
                if (err != null) {
                    statusText.setText("Failed to load: " + err);
                } else {
                    live = l; vod = v; series = s;
                    buildCats();
                    buildCountries();
                    buildSubChips();
                    applyFilter();
                }
            }
        });
    }

    private List<Channel> currentList() {
        if (tab == Channel.VOD) return vod;
        if (tab == Channel.SERIES) return series;
        return live;
    }

    private static final String RECENT_FILTER = "\uD83D\uDD58 Recent";

    /** Row 1: smart auto-categories (Live tab only). */
    private void buildCats() {
        catRow.removeAllViews();
        if (tab != Channel.LIVE) {
            catRow.setVisibility(View.GONE);
            return;
        }
        catRow.setVisibility(View.VISIBLE);
        catIndex = Cats.index(currentList());
        catCounts = Cats.counts(currentList());
        addCatChip("All", currentList().size(), catFilter == null && !favOnly);
        if (!store.recent(acc.id).isEmpty())
            addCatChip(RECENT_FILTER, -1, RECENT_FILTER.equals(catFilter));
        for (String cat : catIndex.keySet()) {
            Integer n = catCounts.get(cat);
            addCatChip(cat, n == null ? 0 : n, cat.equals(catFilter));
        }
    }

    private void addCatChip(final String name, int count, boolean selected) {
        String label = count >= 0 ? name + " (" + count + ")" : name;
        Button b = Ui.chip(this, label, selected);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if ("All".equals(name)) catFilter = null;
                else catFilter = name;
                groupFilter = null;
                buildCats();
                buildCountries();
                buildSubChips();
                applyFilter();
            }
        });
        catRow.addView(b);
    }

    /** Country row: independent filter dimension (tap again to clear). */
    private void buildCountries() {
        countryRow.removeAllViews();
        if (tab != Channel.LIVE) {
            countryRow.setVisibility(View.GONE);
            return;
        }
        countryRow.setVisibility(View.VISIBLE);
        Map<String, Integer> counts = Cats.countryCounts(currentList());
        // most channels first
        List<Map.Entry<String, Integer>> es = new ArrayList<>(counts.entrySet());
        Collections.sort(es, new Comparator<Map.Entry<String, Integer>>() {
            @Override public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });
        for (final Map.Entry<String, Integer> e : es) {
            final String name = e.getKey();
            Button b = Ui.chip(this, name + " (" + e.getValue() + ")",
                    name.equals(countryFilter));
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    countryFilter = name.equals(countryFilter) ? null : name;
                    groupFilter = null;
                    buildCountries();
                    buildSubChips();
                    applyFilter();
                }
            });
            countryRow.addView(b);
        }
    }

    /** Row 2: normalized sub-groups inside the chosen smart category. */
    private void buildSubChips() {
        chipRow.removeAllViews();
        if (tab != Channel.LIVE) {
            // VOD / Series: plain normalized group chips
            chipScroll.setVisibility(View.VISIBLE);
            Set<String> set = new LinkedHashSet<>();
            for (Channel c : currentList()) {
                String g = Cats.normGroup(c.displayGroup());
                if (g.isEmpty()) g = "Ungrouped";
                set.add(g);
            }
            List<String> gs = new ArrayList<>(set);
            Collections.sort(gs, String.CASE_INSENSITIVE_ORDER);
            addSubChip("All", groupFilter == null && !favOnly);
            for (String g : gs) addSubChip(g, g.equals(groupFilter));
            return;
        }
        if (catFilter == null || RECENT_FILTER.equals(catFilter)) {
            chipScroll.setVisibility(View.GONE);
            return;
        }
        chipScroll.setVisibility(View.VISIBLE);
        // sub-groups within the selected category (+ country, if one is picked)
        Set<String> set = new LinkedHashSet<>();
        for (Channel c : currentList()) {
            if (!c.getSmartCat().equals(catFilter)) continue;
            if (countryFilter != null && !Cats.countryKey(c).equals(countryFilter)) continue;
            String disp = Cats.normGroup(c.displayGroup());
            if (disp.isEmpty()) disp = "Ungrouped";
            set.add(disp);
        }
        List<String> subs = new ArrayList<>(set);
        addSubChip("All", groupFilter == null);
        List<String> sorted = new ArrayList<>(subs);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        for (final String g : sorted) addSubChip(g, g.equals(groupFilter));
    }

    private void addSubChip(final String name, boolean selected) {
        Button b = Ui.chip(this, name, selected);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if ("All".equals(name)) groupFilter = null;
                else groupFilter = name;
                buildSubChips();
                applyFilter();
            }
        });
        chipRow.addView(b);
    }

    private void applyFilter() {
        shown = new ArrayList<>();
        Set<String> favs = store.favorites(acc.id);
        boolean recentMode = RECENT_FILTER.equals(catFilter);
        if (recentMode) {
            // keep recency order: newest first
            List<String> keys = store.recent(acc.id);
            java.util.Map<String, Channel> byKey = new java.util.HashMap<>();
            for (Channel c : currentList()) byKey.put(c.key, c);
            for (String k : keys) {
                Channel c = byKey.get(k);
                if (c == null) continue;
                if (favOnly && !favs.contains(c.key)) continue;
                if (!query.isEmpty() && !c.name.toLowerCase().contains(query)) continue;
                shown.add(c);
            }
        } else {
            for (Channel c : currentList()) {
                if (favOnly && !favs.contains(c.key)) continue;
                if (catFilter != null && !c.getSmartCat().equals(catFilter)) continue;
                if (countryFilter != null && !Cats.countryKey(c).equals(countryFilter)) continue;
                if (groupFilter != null && !Cats.normGroup(c.displayGroup()).equals(groupFilter)) continue;
                if (!query.isEmpty() && !c.name.toLowerCase().contains(query)) continue;
                shown.add(c);
            }
            final boolean az = "a_z".equals(store.sortMode());
            Collections.sort(shown, new Comparator<Channel>() {
                @Override public int compare(Channel a, Channel b) {
                    if (!az) return 0; // keep playlist order
                    return a.name.compareToIgnoreCase(b.name);
                }
            });
        }
        int total = currentList().size();
        statusText.setText(shown.size() + " / " + total + (total == 1 ? " item" : " items"));
        boolean lm = "list".equals(store.viewMode());
        grid.setNumColumns(lm ? 1 : GridView.AUTO_FIT);
        if (!lm) grid.setColumnWidth(Ui.dp(this, 108));
        adapter.setListMode(lm);
        adapter.notifyDataSetChanged();
    }

    // ---------------- playback ----------------
    private void play(Channel c, int pos) {
        if (c.kind == Channel.SERIES) {
            SeriesDialog.show(this, acc, c);
            return;
        }
        PlayerQueue.setAccount(acc.id);
        PlayerQueue.set(shown, pos);
        PlayerQueue.setFull(currentList());
        startActivity(new Intent(this, PlayerActivity.class));
    }

    // ---------------- adapter ----------------
    private class ChannelAdapter extends BaseAdapter {
        private boolean listMode = false;
        void setListMode(boolean m) { listMode = m; }

        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int p) { return listMode ? 1 : 0; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            final Channel c = shown.get(pos);
            ImageView iv;
            TextView tv;
            TextView sub;
            TextView star;
            LinearLayout root;
            if (cv == null) {
                root = new LinearLayout(HomeActivity.this);
                root.setFocusable(true);
                int dp8 = Ui.dp(HomeActivity.this, 8);
                if (listMode) {
                    root.setOrientation(LinearLayout.HORIZONTAL);
                    root.setGravity(Gravity.CENTER_VERTICAL);
                    root.setPadding(dp8, dp8, dp8, dp8);
                    iv = new ImageView(HomeActivity.this);
                    iv.setLayoutParams(new LinearLayout.LayoutParams(
                            Ui.dp(HomeActivity.this, 64), Ui.dp(HomeActivity.this, 40)));
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    root.addView(iv);
                    LinearLayout mid = new LinearLayout(HomeActivity.this);
                    mid.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    mlp.setMargins(Ui.dp(HomeActivity.this, 12), 0, 0, 0);
                    mid.setLayoutParams(mlp);
                    tv = new TextView(HomeActivity.this);
                    tv.setTextSize(15);
                    tv.setTextColor(Ui.INK);
                    mid.addView(tv);
                    sub = new TextView(HomeActivity.this);
                    sub.setTextSize(12);
                    sub.setTextColor(Ui.MUTED);
                    mid.addView(sub);
                    root.addView(mid);
                    star = new TextView(HomeActivity.this);
                    star.setTextSize(16);
                    root.addView(star);
                } else {
                    root.setOrientation(LinearLayout.VERTICAL);
                    root.setGravity(Gravity.CENTER);
                    root.setPadding(dp8, dp8, dp8, dp8);
                    iv = new ImageView(HomeActivity.this);
                    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                            Ui.dp(HomeActivity.this, 72), Ui.dp(HomeActivity.this, 48));
                    iv.setLayoutParams(ilp);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    root.addView(iv);
                    tv = new TextView(HomeActivity.this);
                    tv.setTextSize(12);
                    tv.setTextColor(Ui.INK);
                    tv.setGravity(Gravity.CENTER);
                    tv.setMaxLines(2);
                    root.addView(tv);
                    sub = null;
                    star = new TextView(HomeActivity.this);
                    star.setTextSize(11);
                    star.setGravity(Gravity.CENTER);
                    root.addView(star);
                }
                root.setTag(new Object[]{iv, tv, sub, star});
                root.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override public void onFocusChange(View v, boolean hasFocus) {
                        v.setBackground(hasFocus ? Ui.pillBg(Ui.TEAL_DARK) : Ui.cardBgGrad(HomeActivity.this));
                    }
                });
            } else {
                root = (LinearLayout) cv;
                Object[] t = (Object[]) root.getTag();
                iv = (ImageView) t[0]; tv = (TextView) t[1]; sub = (TextView) t[2]; star = (TextView) t[3];
            }
            root.setBackground(Ui.cardBgGrad(HomeActivity.this));
            root.setElevation(Ui.dp(HomeActivity.this, 2));
            tv.setText(c.name);
            if (sub != null) sub.setText(c.displayGroup());
            boolean fav = store.isFav(acc.id, c.key);
            star.setText(fav ? "★" : "");
            star.setTextColor(Ui.GOLD);
            ImageLoader.load(c.logo, iv, R.drawable.ic_launcher);
            // Direct listeners: GridView's item-click callbacks don't fire for
            // focusable item roots, so each tile handles its own tap/long-press.
            // (DPAD_CENTER on a focused tile also triggers OnClickListener,
            // so TV-remote OK still works.)
            final Channel fc = c;
            root.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    int p = shown.indexOf(fc);
                    if (p >= 0) play(fc, p);
                }
            });
            root.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    store.toggleFav(acc.id, fc.key);
                    toast(store.isFav(acc.id, fc.key) ? "★ Added to favorites" : "☆ Removed from favorites");
                    adapter.notifyDataSetChanged();
                    return true;
                }
            });
            return root;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
