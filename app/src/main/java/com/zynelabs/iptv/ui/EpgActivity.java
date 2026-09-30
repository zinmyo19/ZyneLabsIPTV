package com.zynelabs.iptv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Xml;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
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
import com.zynelabs.iptv.data.ImageLoader;
import com.zynelabs.iptv.data.PlAccount;
import com.zynelabs.iptv.data.Store;
import com.zynelabs.iptv.data.XtreamClient;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** OTT-style TV guide: timeline grid of programmes for live channels. */
public class EpgActivity extends Activity {

    private static final int ROW_H_DP = 64;
    private static final int CHAN_W_DP = 150;
    private static final int PX_PER_MIN = 5;
    private static final int HEADER_H_DP = 30;

    private PlAccount acc;
    private List<Channel> live = new ArrayList<>();
    private Map<String, List<Prog>> guide = new HashMap<>(); // streamId -> programmes
    private long winStart, winEnd; // visible window (ms)

    private ProgressBar loading;
    private TextView statusText, dateText, clockText;
    private android.os.Handler clockHandler = new android.os.Handler();
    private Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateGuideClock();
            clockHandler.postDelayed(this, 30000);
        }
    };
    private LinearLayout chanCol;      // left fixed channel column
    private LinearLayout hourRow;      // top hour labels (inside headerHsv)
    private FrameLayout gridFrame;     // rows + now-line
    private LinearLayout rowsBox;
    private HorizontalScrollView gridHsv, headerHsv;
    private ScrollView leftScroll, rightScroll;
    private boolean syncing = false;

    public static class Prog {
        long start, stop;
        String title = "";
        String desc = "";
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        acc = new Store(this).account(getIntent().getStringExtra("accountId"));
        if (acc == null) {
            Toast.makeText(this, "No playlist selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        long now = System.currentTimeMillis();
        winStart = now - 30L * 60 * 1000;
        winEnd = now + 4L * 60 * 60 * 1000;
        build();
        updateGuideClock();
        clockHandler.postDelayed(clockTick, 30000);
        load();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockHandler.removeCallbacks(clockTick);
    }

    private void updateGuideClock() {
        if (clockText == null) return;
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("HH:mm", Locale.US);
        clockText.setText("\uD83D\uDD52 " + f.format(new Date()));
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
        int p = Ui.dp(this, 10);
        top.setPadding(p, p, p, p);
        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(22);
        back.setTextColor(Ui.TEAL);
        back.setBackgroundColor(0x00000000);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        top.addView(back);
        TextView title = Ui.label(this, "📅 TV Guide", 17, Ui.INK, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tlp);
        top.addView(title);
        dateText = Ui.label(this, "", 12, Ui.MUTED, false);
        top.addView(dateText);
        clockText = Ui.label(this, "", 13, Ui.TEAL, true);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 4), 0);
        clockText.setLayoutParams(clp);
        top.addView(clockText);
        Button prev = Ui.circleBtn(this, "‹", 15);
        prev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { shift(-3); }
        });
        top.addView(prev);
        Button next = Ui.circleBtn(this, "›", 15);
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { shift(3); }
        });
        top.addView(next);
        root.addView(top);

        loading = new ProgressBar(this);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                Ui.dp(this, 48), Ui.dp(this, 48));
        llp.gravity = Gravity.CENTER;
        loading.setLayoutParams(llp);
        root.addView(loading);
        statusText = Ui.label(this, "Loading guide…", 13, Ui.MUTED, false);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText);

        // body: fixed channel column + scrollable grid
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        body.setLayoutParams(blp);
        body.setVisibility(View.GONE);

        leftScroll = new ScrollView(this);
        leftScroll.setLayoutParams(new LinearLayout.LayoutParams(
                Ui.dp(this, CHAN_W_DP), ViewGroup.LayoutParams.MATCH_PARENT));
        leftScroll.setVerticalScrollBarEnabled(false);
        // spacer for the hour header height
        LinearLayout leftWrap = new LinearLayout(this);
        leftWrap.setOrientation(LinearLayout.VERTICAL);
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, HEADER_H_DP)));
        leftWrap.addView(spacer);
        chanCol = new LinearLayout(this);
        chanCol.setOrientation(LinearLayout.VERTICAL);
        leftWrap.addView(chanCol);
        leftScroll.addView(leftWrap);
        body.addView(leftScroll);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        headerHsv = new HorizontalScrollView(this);
        headerHsv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, HEADER_H_DP)));
        headerHsv.setHorizontalScrollBarEnabled(false);
        hourRow = new LinearLayout(this);
        hourRow.setOrientation(LinearLayout.HORIZONTAL);
        headerHsv.addView(hourRow);
        right.addView(headerHsv);

        gridHsv = new HorizontalScrollView(this);
        gridHsv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rightScroll = new ScrollView(this);
        rightScroll.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rightScroll.setVerticalScrollBarEnabled(false);
        gridFrame = new FrameLayout(this);
        rowsBox = new LinearLayout(this);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        gridFrame.addView(rowsBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rightScroll.addView(gridFrame);
        gridHsv.addView(rightScroll);
        right.addView(gridHsv);
        body.addView(right);
        root.addView(body);

        // scroll sync: grid <-> header (horizontal), left <-> right (vertical)
        gridHsv.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int x, int y, int ox, int oy) {
                headerHsv.scrollTo(x, 0);
            }
        });
        leftScroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int x, int y, int ox, int oy) {
                if (syncing) return;
                syncing = true;
                rightScroll.scrollTo(0, y);
                syncing = false;
            }
        });
        rightScroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int x, int y, int ox, int oy) {
                if (syncing) return;
                syncing = true;
                leftScroll.scrollTo(0, y);
                syncing = false;
            }
        });

        setContentView(root);
        body.setTag("body");
    }

    private View getBodyByTag() {
        LinearLayout root = (LinearLayout) ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        for (int i = 0; i < root.getChildCount(); i++) {
            if ("body".equals(root.getChildAt(i).getTag())) return root.getChildAt(i);
        }
        return null;
    }

    private void shift(int hours) {
        long d = hours * 3600L * 1000L;
        winStart += d;
        winEnd += d;
        render();
    }

    // ---------------- data ----------------
    private void load() {
        loading.setVisibility(View.VISIBLE);
        ChannelRepo.load(this, acc, new ChannelRepo.Callback() {
            @Override public void onResult(List<Channel> l, List<Channel> v,
                                           List<Channel> s, String err) {
                if (err != null || l.isEmpty()) {
                    loading.setVisibility(View.GONE);
                    statusText.setText(err != null ? "Failed: " + err : "No live channels");
                    return;
                }
                live = l;
                new Thread(new Runnable() {
                    @Override public void run() {
                        // Programme data is only available for Xtream providers (xmltv.php).
                        // Other sources still get the channel list with name + logo.
                        final boolean ok = acc.isXtream() && fetchGuide();
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                loading.setVisibility(View.GONE);
                                statusText.setText(ok ? ""
                                        : "No programme data — showing channels only.");
                                View bd = getBodyByTag();
                                if (bd != null) bd.setVisibility(View.VISIBLE);
                                render();
                            }
                        });
                    }
                }).start();
            }
        });
    }

    private String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    /** Stream-parse xmltv.php, keep programmes for our live channels only. */
    private boolean fetchGuide() {
        Map<String, Boolean> want = new HashMap<>();
        for (Channel c : live) want.put(String.valueOf(c.streamId), true);
        HttpURLConnection con = null;
        try {
            String urlStr = XtreamClient.normServer(acc.server) + "/xmltv.php?username="
                    + enc(acc.user) + "&password=" + enc(acc.pass);
            con = (HttpURLConnection) new URL(urlStr).openConnection();
            con.setConnectTimeout(20000);
            con.setReadTimeout(120000);
            con.setRequestProperty("User-Agent", "ZyneLabsIPTV/3.0");
            InputStream in = con.getInputStream();
            XmlPullParser x = Xml.newPullParser();
            x.setInput(in, "UTF-8");
            Prog cur = null;
            String curChan = null;
            String curTag = null;
            int ev = x.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String name = x.getName();
                    if ("programme".equals(name)) {
                        curChan = x.getAttributeValue(null, "channel");
                        if (curChan != null && want.containsKey(curChan)) {
                            cur = new Prog();
                            cur.start = parseTime(x.getAttributeValue(null, "start"));
                            cur.stop = parseTime(x.getAttributeValue(null, "stop"));
                        } else {
                            cur = null;
                        }
                    } else if (cur != null && ("title".equals(name) || "desc".equals(name))) {
                        curTag = name;
                    }
                } else if (ev == XmlPullParser.TEXT) {
                    if (cur != null && curTag != null) {
                        String t = x.getText();
                        if ("title".equals(curTag)) cur.title += t;
                        else cur.desc += t;
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    String name = x.getName();
                    if ("programme".equals(name)) {
                        if (cur != null && cur.start > 0 && !cur.title.isEmpty()) {
                            List<Prog> list = guide.get(curChan);
                            if (list == null) {
                                list = new ArrayList<>();
                                guide.put(curChan, list);
                            }
                            list.add(cur);
                        }
                        cur = null;
                        curChan = null;
                    }
                    curTag = null;
                }
                ev = x.next();
            }
            in.close();
        } catch (Exception e) {
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
        return !guide.isEmpty();
    }

    private long parseTime(String s) {
        if (s == null || s.length() < 14) return 0;
        try {
            String core = s.substring(0, 14);
            String zone = s.length() > 15 ? s.substring(15).trim() : "+0000";
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
            return f.parse(core + " " + zone).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    // ---------------- render ----------------
    private void render() {
        chanCol.removeAllViews();
        rowsBox.removeAllViews();
        hourRow.removeAllViews();
        // remove old now-line if present
        for (int i = gridFrame.getChildCount() - 1; i >= 0; i--) {
            Object tag = gridFrame.getChildAt(i).getTag();
            if ("nowline".equals(tag)) gridFrame.removeViewAt(i);
        }

        int totalMin = (int) ((winEnd - winStart) / 60000);
        int totalW = totalMin * Ui.dp(this, PX_PER_MIN);

        // hour header
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(winStart);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        SimpleDateFormat hf = new SimpleDateFormat("HH:mm", Locale.US);
        FrameLayout hh = new FrameLayout(this);
        hh.setLayoutParams(new LinearLayout.LayoutParams(totalW, Ui.dp(this, HEADER_H_DP)));
        while (cal.getTimeInMillis() < winEnd) {
            TextView tv = Ui.label(this, hf.format(cal.getTime()), 10, Ui.MUTED, false);
            int x = (int) ((cal.getTimeInMillis() - winStart) / 60000) * Ui.dp(this, PX_PER_MIN);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Math.max(0, x + Ui.dp(this, 4));
            lp.topMargin = Ui.dp(this, 8);
            tv.setLayoutParams(lp);
            hh.addView(tv);
            // tick
            View tick = new View(this);
            tick.setBackgroundColor(0x33000000);
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                    Ui.dp(this, 1), Ui.dp(this, HEADER_H_DP));
            tlp.leftMargin = Math.max(0, x);
            tick.setLayoutParams(tlp);
            hh.addView(tick);
            cal.add(Calendar.HOUR_OF_DAY, 1);
        }
        hourRow.addView(hh);

        // rows
        int rowH = Ui.dp(this, ROW_H_DP);
        for (int ci = 0; ci < live.size(); ci++) {
            final Channel c = live.get(ci);
            final int cidx = ci;

            // left channel cell
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.HORIZONTAL);
            cell.setGravity(Gravity.CENTER_VERTICAL);
            cell.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, rowH));
            cell.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
            ImageView iv = new ImageView(this);
            iv.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 30)));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ImageLoader.load(c.logo, iv, R.drawable.ic_launcher);
            cell.addView(iv);
            TextView nm = Ui.label(this, c.name, 11, Ui.INK, false);
            nm.setMaxLines(2);
            nm.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nlp.setMargins(Ui.dp(this, 6), 0, 0, 0);
            nm.setLayoutParams(nlp);
            cell.addView(nm);
            final String key = String.valueOf(c.streamId);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { playChannel(cidx); }
            });
            chanCol.addView(cell);
            // divider
            View div = new View(this);
            div.setBackgroundColor(0x22000000);
            div.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));
            chanCol.addView(div);

            // programme strip
            FrameLayout strip = new FrameLayout(this);
            strip.setLayoutParams(new LinearLayout.LayoutParams(
                    totalW, rowH));
            List<Prog> progs = guide.get(key);
            boolean drew = false;
            if (progs != null) {
                for (final Prog pg : progs) {
                    if (pg.stop <= winStart || pg.start >= winEnd) continue;
                    drew = true;
                    long s = Math.max(pg.start, winStart);
                    long e = Math.min(pg.stop, winEnd);
                    int x = (int) ((s - winStart) / 60000) * Ui.dp(this, PX_PER_MIN);
                    int w = Math.max(Ui.dp(this, 44),
                            (int) ((e - s) / 60000) * Ui.dp(this, PX_PER_MIN));
                    TextView blk = new TextView(this);
                    blk.setText(pg.title);
                    blk.setTextSize(11);
                    blk.setTextColor(Ui.INK);
                    blk.setMaxLines(2);
                    blk.setEllipsize(TextUtils.TruncateAt.END);
                    blk.setBackground(Ui.cardBgGrad(this));
                    blk.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
                    FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                            w - Ui.dp(this, 3), rowH - Ui.dp(this, 8));
                    blp.leftMargin = x + Ui.dp(this, 1);
                    blp.topMargin = Ui.dp(this, 4);
                    blk.setLayoutParams(blp);
                    blk.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { progDialog(c, cidx, pg); }
                    });
                    strip.addView(blk);
                }
            }
            if (!drew) {
                TextView ph = new TextView(this);
                ph.setText("No programme info");
                ph.setTextSize(11);
                ph.setTextColor(Ui.MUTED);
                ph.setGravity(Gravity.CENTER_VERTICAL);
                ph.setPadding(Ui.dp(this, 8), 0, 0, 0);
                strip.addView(ph, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, rowH - Ui.dp(this, 8)));
            }
            rowsBox.addView(strip);
            View div2 = new View(this);
            div2.setBackgroundColor(0x22000000);
            div2.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));
            rowsBox.addView(div2);
        }

        // now-line + time label
        long now = System.currentTimeMillis();
        if (now >= winStart && now <= winEnd) {
            int x = (int) ((now - winStart) / 60000) * Ui.dp(this, PX_PER_MIN);
            View line = new View(this);
            line.setTag("nowline");
            line.setBackgroundColor(Ui.RED);
            FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
                    Ui.dp(this, 3), ViewGroup.LayoutParams.MATCH_PARENT);
            llp.leftMargin = x;
            line.setLayoutParams(llp);
            gridFrame.addView(line);
            TextView nowTv = new TextView(this);
            nowTv.setTag("nowline");
            java.text.SimpleDateFormat nf = new java.text.SimpleDateFormat("HH:mm", Locale.US);
            nowTv.setText("NOW " + nf.format(new Date(now)));
            nowTv.setTextSize(10);
            nowTv.setTextColor(0xFFFFFFFF);
            nowTv.setBackgroundColor(Ui.RED);
            nowTv.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2));
            FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.leftMargin = x + Ui.dp(this, 5);
            nowTv.setLayoutParams(nlp);
            gridFrame.addView(nowTv);
        }

        // date label + scroll to now
        SimpleDateFormat df = new SimpleDateFormat("EEE d MMM", Locale.US);
        dateText.setText(df.format(new Date(winStart)));
        if (now >= winStart && now <= winEnd) {
            final int sx = Math.max(0, (int) ((now - winStart) / 60000)
                    * Ui.dp(this, PX_PER_MIN) - getResources().getDisplayMetrics().widthPixels / 3);
            gridHsv.post(new Runnable() {
                @Override public void run() { gridHsv.scrollTo(sx, 0); }
            });
        }
    }

    private void progDialog(final Channel c, final int cidx, Prog pg) {
        SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.US);
        String when = tf.format(new Date(pg.start)) + " – " + tf.format(new Date(pg.stop));
        String msg = when + (pg.desc.isEmpty() ? "" : "\n\n" + pg.desc);
        new AlertDialog.Builder(this)
                .setTitle(pg.title)
                .setMessage(msg)
                .setPositiveButton("▶ Play", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { playChannel(cidx); }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void playChannel(int idx) {
        PlayerQueue.setAccount(acc.id);
        PlayerQueue.set(live, idx);
        startActivity(new Intent(this, PlayerActivity.class));
    }
}
