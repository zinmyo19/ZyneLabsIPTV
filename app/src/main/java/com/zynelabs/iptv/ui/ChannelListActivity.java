package com.zynelabs.iptv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.zynelabs.iptv.data.Cats;
import com.zynelabs.iptv.data.Channel;
import com.zynelabs.iptv.data.ImageLoader;
import com.zynelabs.iptv.data.PlAccount;
import com.zynelabs.iptv.data.PlaylistCache;
import com.zynelabs.iptv.data.PlaylistLoader;
import com.zynelabs.iptv.data.Store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OTT-style channel list: plain text rows (logo, number + name, group,
 * favorite star) in a vertical list — no chip stacks, no tiles.
 *
 * <p>Replaces the old chip-stack browser. Data comes cache-first via
 * {@link PlaylistLoader}: instant render from disk, quiet background
 * refresh.</p>
 */
public class ChannelListActivity extends Activity {

    private Store store;
    private PlAccount acc;
    private List<Channel> live = new ArrayList<>();
    private List<Channel> vod = new ArrayList<>();
    private List<Channel> series = new ArrayList<>();
    private List<Channel> shown = new ArrayList<>();

    private int tab = Channel.LIVE;
    private String catFilter = null;    // smart category (live tab)
    private String groupFilter = null;  // Cats.groupKey (vod/series tabs)
    private boolean favOnly = false;
    private boolean recentMode = false;
    private boolean radioOnly = false;
    private String query = "";

    private ListView list;
    private ChannelAdapter adapter;
    private ProgressBar loading;
    private TextView statusText;
    private Button retryBtn;
    private EditText searchBox;
    private Button favBtn;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable watchdog;
    private boolean dataReady = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.applyTheme(this);
        store = new Store(this);
        acc = store.account(getIntent().getStringExtra("accountId"));
        if (acc == null) { finish(); return; }
        tab = getIntent().getIntExtra("tab", Channel.LIVE);
        catFilter = getIntent().getStringExtra("cat");
        groupFilter = getIntent().getStringExtra("group");
        String filter = getIntent().getStringExtra("filter");
        if ("fav".equals(filter)) favOnly = true;
        if ("recent".equals(filter)) recentMode = true;
        if ("radio".equals(filter)) radioOnly = true;
        build();
        startLoad();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (dataReady) applyFilter(); // refresh fav stars / renames
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
        TextView title = Ui.label(this, screenTitle(), 17, Ui.INK, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tlp);
        top.addView(title);
        favBtn = Ui.barBtn(this, favOnly ? "★" : "☆", 20);
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
                if (!show) searchBox.setText("");
            }
        });
        top.addView(searchBtn);
        root.addView(top);

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

        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(Ui.rowSelector(this));
        list.setDrawSelectorOnTop(false);
        list.setPadding(p, 0, p, p);
        list.setClipToPadding(false);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        list.setLayoutParams(glp);
        adapter = new ChannelAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View v,
                                              int pos, long id) {
                play(shown.get(pos), pos);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View v,
                                                     int pos, long id) {
                dlgChannelOptions(shown.get(pos));
                return true;
            }
        });
        root.addView(list);

        setContentView(root);
        Ui.enableTvFocus(root);
    }

    private String screenTitle() {
        String t = getIntent().getStringExtra("title");
        if (t != null && !t.isEmpty()) return Ui.stripEmoji(t);
        if (favOnly) return "Favorites";
        if (recentMode) return "Recently Watched";
        if (radioOnly) return "Radio";
        if (catFilter != null) return Ui.stripEmoji(catFilter);
        if (groupFilter != null) return "Channels";
        return tab == Channel.VOD ? "Movies"
                : tab == Channel.SERIES ? "Series" : "Live TV";
    }

    // ---------------- data ----------------
    private void startLoad() {
        dataReady = false;
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
                setLists(l, v, s);
            }
            @Override public void onProgress(String phase) {
                if (!dataReady && !isFinishing()) statusText.setText(phase);
            }
            @Override public void onFresh(List<Channel> l, List<Channel> v,
                                          List<Channel> s) {
                if (isFinishing()) return;
                setLists(l, v, s);
            }
            @Override public void onError(String err) {
                if (isFinishing()) return;
                if (dataReady) {
                    Toast.makeText(ChannelListActivity.this,
                            "Refresh failed: " + err, Toast.LENGTH_SHORT).show();
                } else {
                    showError("Couldn't load playlist: " + err);
                }
            }
        });
    }

    private void setLists(List<Channel> l, List<Channel> v, List<Channel> s) {
        live = l; vod = v; series = s;
        PlayerQueue.setAccount(acc.id);
        dataReady = true;
        if (watchdog != null) handler.removeCallbacks(watchdog);
        loading.setVisibility(View.GONE);
        retryBtn.setVisibility(View.GONE);
        applyFilter();
    }

    private void showError(String msg) {
        if (watchdog != null) handler.removeCallbacks(watchdog);
        loading.setVisibility(View.GONE);
        statusText.setText(msg);
        retryBtn.setVisibility(View.VISIBLE);
    }

    private List<Channel> currentList() {
        if (tab == Channel.VOD) return vod;
        if (tab == Channel.SERIES) return series;
        return live;
    }

    /** Radio lives in its own section; keep it out of Live TV lists. */
    private boolean radioExcluded(Channel c) {
        if (tab != Channel.LIVE) return false;
        boolean isRadio = Cats.RADIO.equals(c.getSmartCat());
        if (radioOnly) return !isRadio;
        if (!isRadio) return false;
        if (Cats.RADIO.equals(catFilter)) return false;
        return !store.showRadioInLive();
    }

    private void applyFilter() {
        shown = new ArrayList<>();
        java.util.Set<String> favs = store.favorites(acc.id);
        java.util.Set<String> hidden = store.hiddenChannels(acc.id);
        boolean lockAdult = store.adultLocked();
        if (recentMode) {
            Map<String, Channel> byKey = new HashMap<>();
            for (Channel c : currentList()) byKey.put(c.key, c);
            for (String k : store.recent(acc.id)) {
                Channel c = byKey.get(k);
                if (c == null || hidden.contains(c.key)) continue;
                if (radioExcluded(c)) continue;
                if (lockAdult && Cats.ADULT.equals(c.getSmartCat())) continue;
                if (favOnly && !favs.contains(c.key)) continue;
                if (!matchesQuery(c)) continue;
                shown.add(c);
            }
        } else {
            for (Channel c : currentList()) {
                if (hidden.contains(c.key)) continue;
                if (radioExcluded(c)) continue;
                if (lockAdult && Cats.ADULT.equals(c.getSmartCat())) continue;
                if (favOnly && !favs.contains(c.key)) continue;
                if (catFilter != null && !c.getSmartCat().equals(catFilter)) continue;
                if (groupFilter != null
                        && !Cats.groupKey(c.displayGroup()).equals(groupFilter)) continue;
                if (!matchesQuery(c)) continue;
                shown.add(c);
            }
            final boolean az = "a_z".equals(store.sortMode());
            final boolean byNum = "number".equals(store.sortMode());
            Collections.sort(shown, new Comparator<Channel>() {
                @Override public int compare(Channel a, Channel b) {
                    if (byNum) return PlayerQueue.numberOf(a) - PlayerQueue.numberOf(b);
                    if (!az) return 0;
                    return dispName(a).compareToIgnoreCase(dispName(b));
                }
            });
        }
        int total = currentList().size();
        statusText.setText(shown.size() + " / " + total
                + (total == 1 ? " item" : " items"));
        adapter.notifyDataSetChanged();
    }

    private boolean matchesQuery(Channel c) {
        if (query.isEmpty()) return true;
        return c.name.toLowerCase().contains(query)
                || dispName(c).toLowerCase().contains(query);
    }

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

    private String dispName(Channel c) {
        String n = store.customName(acc.id, c.key);
        return n.isEmpty() ? c.name : n;
    }

    // ---------------- adapter: plain text rows ----------------
    private class ChannelAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            final Channel c = shown.get(pos);
            LinearLayout root;
            ImageView iv;
            TextView name;
            TextView sub;
            TextView star;
            if (cv == null) {
                root = new LinearLayout(ChannelListActivity.this);
                root.setOrientation(LinearLayout.HORIZONTAL);
                root.setGravity(Gravity.CENTER_VERTICAL);
                int hp = Ui.dp(ChannelListActivity.this, 14);
                root.setPadding(hp, Ui.dp(ChannelListActivity.this, 10),
                        hp, Ui.dp(ChannelListActivity.this, 10));
                iv = new ImageView(ChannelListActivity.this);
                iv.setLayoutParams(new LinearLayout.LayoutParams(
                        Ui.dp(ChannelListActivity.this, 64),
                        Ui.dp(ChannelListActivity.this, 40)));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                root.addView(iv);
                LinearLayout mid = new LinearLayout(ChannelListActivity.this);
                mid.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                mlp.setMargins(Ui.dp(ChannelListActivity.this, 12), 0, 0, 0);
                mid.setLayoutParams(mlp);
                name = new TextView(ChannelListActivity.this);
                name.setTextSize(15);
                name.setTextColor(Ui.INK);
                name.setMaxLines(1);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                mid.addView(name);
                sub = new TextView(ChannelListActivity.this);
                sub.setTextSize(12);
                sub.setTextColor(Ui.MUTED);
                sub.setMaxLines(1);
                sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
                mid.addView(sub);
                root.addView(mid);
                star = new TextView(ChannelListActivity.this);
                star.setTextSize(16);
                star.setTextColor(Ui.GOLD);
                root.addView(star);
                root.setTag(new Object[]{iv, name, sub, star});
            } else {
                root = (LinearLayout) cv;
                Object[] t = (Object[]) root.getTag();
                iv = (ImageView) t[0];
                name = (TextView) t[1];
                sub = (TextView) t[2];
                star = (TextView) t[3];
            }
            if (tab == Channel.LIVE) {
                int num = PlayerQueue.numberOf(c);
                if (num > 0) {
                    String pre = num + " · ";
                    android.text.SpannableString ss =
                            new android.text.SpannableString(pre + dispName(c));
                    ss.setSpan(new android.text.style.ForegroundColorSpan(Ui.MUTED),
                            0, pre.length(),
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    name.setText(ss);
                } else {
                    name.setText(dispName(c));
                }
            } else {
                name.setText(dispName(c));
            }
            sub.setText(c.displayGroup());
            star.setText(store.isFav(acc.id, c.key) ? "★" : "");
            ImageLoader.load(c.logo, iv, Ui.catArt(ChannelListActivity.this, c));
            return root;
        }
    }

    // ---------------- channel options ----------------
    private void dlgChannelOptions(final Channel c) {
        final boolean fav = store.isFav(acc.id, c.key);
        final String[] items = new String[]{
                fav ? "Remove from favorites" : "Add to favorites",
                "Rename channel",
                "Hide channel",
        };
        new AlertDialog.Builder(this)
                .setTitle(dispName(c))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (w == 0) {
                            store.toggleFav(acc.id, c.key);
                            toast(store.isFav(acc.id, c.key)
                                    ? "Added to favorites" : "Removed from favorites");
                            applyFilter();
                        } else if (w == 1) {
                            dlgRenameChannel(c);
                        } else {
                            dlgHideChannel(c);
                        }
                    }
                })
                .show();
    }

    private void dlgRenameChannel(final Channel c) {
        final EditText et = Ui.field(this, c.name);
        et.setText(store.customName(acc.id, c.key));
        int p = Ui.dp(this, 16);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(p, Ui.dp(this, 8), p, 0);
        wrap.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Rename channel")
                .setView(wrap)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        store.setCustomName(acc.id, c.key, et.getText().toString());
                        toast("Renamed");
                        applyFilter();
                    }
                })
                .setNeutralButton("Reset", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        store.setCustomName(acc.id, c.key, "");
                        toast("Name reset");
                        applyFilter();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void dlgHideChannel(final Channel c) {
        new AlertDialog.Builder(this)
                .setTitle("Hide channel")
                .setMessage("Hide \"" + dispName(c) + "\" from the list?\n\n"
                        + "You can bring it back any time in\nSettings → Channel manager.")
                .setPositiveButton("Hide", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        store.setChannelHidden(acc.id, c.key, true, dispName(c));
                        toast("Channel hidden");
                        applyFilter();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
