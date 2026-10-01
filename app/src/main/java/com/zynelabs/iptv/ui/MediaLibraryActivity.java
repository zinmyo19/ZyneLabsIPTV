package com.zynelabs.iptv.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.zynelabs.iptv.data.Cats;
import com.zynelabs.iptv.data.Channel;
import com.zynelabs.iptv.data.ChannelRepo;
import com.zynelabs.iptv.data.PlAccount;
import com.zynelabs.iptv.data.Store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OTT-style Media Library: scans the account's VOD catalog — including movie
 * files detected inside M3U playlists — and browses them as genre folders
 * (folder icon + name + count), like OTT Navigator's Media library.
 */
public class MediaLibraryActivity extends Activity {

    private PlAccount acc;
    private LinearLayout content;
    private ProgressBar loading;
    private TextView statusText;
    private List<Channel> vod = new ArrayList<>();
    private Map<String, List<Channel>> folders = new LinkedHashMap<>();
    private List<String> folderNames = new ArrayList<>();

    private String openFolder = null; // null = folder grid
    private String query = "";
    private EditText searchBox;
    private List<Channel> shown = new ArrayList<>();
    private BaseAdapter listAdapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.applyTheme(this);
        acc = new Store(this).account(getIntent().getStringExtra("accountId"));
        if (acc == null) { finish(); return; }
        build();
        load();
    }

    // ---------------- UI ----------------
    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.PAPER);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 12);
        top.setPadding(p, p, p, p);
        Button back = Ui.barBtn(this, "‹", 22);
        back.setTextColor(Ui.TEAL);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (openFolder != null) { openFolder = null; showFolders(); }
                else finish();
            }
        });
        top.addView(back);
        TextView title = Ui.label(this, "Media Library", 17, Ui.INK, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tlp);
        top.addView(title);
        root.addView(top);

        searchBox = Ui.field(this, "Search movies…");
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
        statusText = Ui.label(this, "Scanning media…", 13, Ui.MUTED, false);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        content.setLayoutParams(clp);
        root.addView(content);

        setContentView(root);
        Ui.enableTvFocus(root);
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
                vod = v;
                folders.clear();
                for (Channel c : vod) {
                    String f = Cats.normGroup(c.displayGroup());
                    List<Channel> list = folders.get(f);
                    if (list == null) { list = new ArrayList<>(); folders.put(f, list); }
                    list.add(c);
                }
                folderNames = new ArrayList<>(folders.keySet());
                Collections.sort(folderNames, String.CASE_INSENSITIVE_ORDER);
                statusText.setText(vod.isEmpty() ? "No movies found in this playlist." : "");
                showFolders();
            }
        });
    }

    // ---------------- folders grid ----------------
    private void showFolders() {
        searchBox.setVisibility(View.GONE);
        searchBox.setText("");
        query = "";
        content.removeAllViews();
        if (folderNames.isEmpty()) {
            content.addView(Ui.emptyView(this, "No movies found."));
            return;
        }
        GridView grid = new GridView(this);
        int cols = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? 5 : 3;
        grid.setNumColumns(cols);
        grid.setVerticalSpacing(Ui.dp(this, 10));
        grid.setHorizontalSpacing(Ui.dp(this, 10));
        int p = Ui.dp(this, 12);
        grid.setPadding(p, p, p, p);
        grid.setSelector(Ui.listSelector(this));
        grid.setDrawSelectorOnTop(true);
        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return folderNames.size(); }
            @Override public Object getItem(int i) { return folderNames.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup parent) {
                String name = folderNames.get(i);
                int count = folders.get(name).size();
                LinearLayout cell = new LinearLayout(MediaLibraryActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setBackgroundColor(Ui.CARD);
                int cp = Ui.dp(MediaLibraryActivity.this, 14);
                cell.setPadding(cp, cp, cp, cp);
                TextView nm = Ui.label(MediaLibraryActivity.this, name, 13,
                        Ui.INK, true);
                nm.setGravity(Gravity.CENTER);
                nm.setMaxLines(2);
                cell.addView(nm);
                TextView ct = Ui.label(MediaLibraryActivity.this,
                        count + (count == 1 ? " movie" : " movies"), 11,
                        Ui.TEAL, false);
                ct.setGravity(Gravity.CENTER);
                cell.addView(ct);
                return cell;
            }
        });
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
                openFolder = folderNames.get(pos);
                showMovies();
            }
        });
        content.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ---------------- movies list ----------------
    private void showMovies() {
        searchBox.setVisibility(View.VISIBLE);
        content.removeAllViews();
        ListView list = new ListView(this);
        list.setSelector(Ui.listSelector(this));
        list.setDrawSelectorOnTop(true);
        list.setDividerHeight(0);
        listAdapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup parent) {
                Channel c = shown.get(i);
                LinearLayout row = new LinearLayout(MediaLibraryActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int rp = Ui.dp(MediaLibraryActivity.this, 12);
                row.setPadding(rp, Ui.dp(MediaLibraryActivity.this, 10), rp,
                        Ui.dp(MediaLibraryActivity.this, 10));
                LinearLayout tx = new LinearLayout(MediaLibraryActivity.this);
                tx.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tlp.setMargins(Ui.dp(MediaLibraryActivity.this, 10), 0, 0, 0);
                tx.setLayoutParams(tlp);
                TextView nm = Ui.label(MediaLibraryActivity.this, c.name, 14,
                        Ui.INK, false);
                tx.addView(nm);
                row.addView(tx);
                TextView go = Ui.label(MediaLibraryActivity.this, "›", 20,
                        Ui.TEAL, true);
                row.addView(go);
                return row;
            }
        };
        list.setAdapter(listAdapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
                Channel c = shown.get(pos);
                List<Channel> folder = folders.get(openFolder);
                int idx = folder.indexOf(c);
                PlayerQueue.setAccount(acc.id);
                PlayerQueue.set(folder, Math.max(0, idx));
                startActivity(new Intent(MediaLibraryActivity.this, PlayerActivity.class));
            }
        });
        content.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        applyFilter();
    }

    private void applyFilter() {
        shown.clear();
        List<Channel> folder = openFolder == null
                ? new ArrayList<Channel>() : folders.get(openFolder);
        if (folder != null) {
            for (Channel c : folder) {
                if (!query.isEmpty()
                        && !c.name.toLowerCase().contains(query)) continue;
                shown.add(c);
            }
        }
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        if (openFolder != null) { openFolder = null; showFolders(); return; }
        super.onBackPressed();
    }
}
