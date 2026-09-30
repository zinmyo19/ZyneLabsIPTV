package com.zynelabs.iptv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.exoplayer.ExoPlayer;

import com.zynelabs.iptv.data.Channel;
import com.zynelabs.iptv.data.ImageLoader;
import com.zynelabs.iptv.data.Store;
import com.zynelabs.iptv.data.XtreamClient;

import java.util.ArrayList;
import java.util.List;

/** Fullscreen player on ExoPlayer: live zapping + VOD seek. HLS/DASH/RTSP/progressive. */
public class PlayerActivity extends Activity {

    private ExoPlayer player;
    private SurfaceView surface;
    private WifiBarsView spinner;
    private LinearLayout topBar, bottomBar;
    private TextView titleText, posText, errText, nowNextText, clockText;
    private ImageView logoView;
    private Button playBtn;
    private SeekBar seek;
    private Handler handler = new Handler();
    private boolean isVod = false;
    private boolean controlsVisible = true;
    private Runnable sleepTask = null;
    private Runnable hideTask = new Runnable() {
        @Override public void run() { setControls(false); }
    };
    private Runnable seekTask = new Runnable() {
        @Override public void run() {
            if (player != null && isVod && player.isPlaying()) {
                long d = player.getDuration();
                if (d > 0 && d != C.TIME_UNSET) {
                    seek.setProgress((int) (player.getCurrentPosition() * 100 / d));
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    // aspect / gestures / subs
    private FrameLayout root;
    private TextView hintView, subView, qBadge;
    private Button aspectBtn;
    private AudioManager audioManager;
    private int aspectMode = 0; // 0=Fit 1=Fill 2=Zoom
    private static final String[] ASPECT_NAMES = {"Fit", "Fill", "Zoom"};
    private int videoW = 0, videoH = 0;
    private float downX, downY;
    private boolean swiping = false, swipeBright = false;
    private int swipeStartVol = 0, swipeMaxVol = 15;
    private float swipeStartBright = 0.5f;
    private Runnable hintHideTask = new Runnable() {
        @Override public void run() { if (hintView != null) hintView.setVisibility(View.GONE); }
    };

    // picture-in-picture + in-player channel drawer
    private boolean inPip = false;
    private LinearLayout chanDrawer;
    private EditText chanSearch;
    private ListView chanListView;
    private BaseAdapter chanAdapter;
    private List<Channel> drawerShown = new ArrayList<>();
    private String drawerQuery = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // draw into the display cutout area (no black letterbox bar in landscape).
        // NOTE: getAttributes() returns a copy — must call setAttributes()
        // for the change to take effect, and use ALWAYS (strongest request).
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            getWindow().setAttributes(lp);
        }
        Channel c = PlayerQueue.current();
        if (c == null || c.url == null || c.url.isEmpty()) { finish(); return; }
        isVod = c.kind != Channel.LIVE;
        buildPlayer();
        build();
        enterFullscreen();
        playCurrent();
    }

    private void buildPlayer() {
        // User-adjustable buffer (Settings → Player → Buffer size): bigger
        // buffers ride through short server-side stalls without rebuffering.
        int bufSecs = new com.zynelabs.iptv.data.Store(this).bufferSecs();
        int maxMs = Math.max(15_000, bufSecs * 1000);
        androidx.media3.exoplayer.DefaultLoadControl loadControl =
                new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                                Math.min(15_000, maxMs),  // minBufferMs
                                maxMs,                    // maxBufferMs
                                2_500,  // bufferForPlaybackMs
                                5_000)  // bufferForPlaybackAfterRebufferMs
                        .build();
        player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    spinner.setVisibility(View.VISIBLE);
                } else if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                    spinner.setVisibility(View.GONE);
                    playBtn.setText(player.isPlaying() ? "⏸" : "▶");
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                playBtn.setText(isPlaying ? "⏸" : "▶");
                if (isPlaying) spinner.setVisibility(View.GONE);
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                spinner.setVisibility(View.GONE);
                String msg = error.getMessage();
                errText.setText("Cannot play this stream."
                        + (msg != null && !msg.isEmpty() ? "\n" + msg : "")
                        + "\nTap ✕ or press back.");
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                videoW = videoSize.width;
                videoH = videoSize.height;
                layoutSurface();
                updateQualityBadge(videoH);
            }

            @Override
            public void onCues(List<Cue> cues) {
                if (subView == null) return;
                StringBuilder sb = new StringBuilder();
                for (Cue cue : cues) {
                    if (cue.text != null && cue.text.length() > 0) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(cue.text);
                    }
                }
                subView.setText(sb.toString());
            }
        });
    }

    private void build() {
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        player.setVideoSurfaceView(surface);

        spinner = new WifiBarsView(this);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                Ui.dp(this, 112), Ui.dp(this, 84), Gravity.CENTER);
        root.addView(spinner, slp);

        errText = Ui.label(this, "", 14, Ui.RED, false);
        errText.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(errText, elp);

        // subtitle overlay (bottom-center, above controls)
        subView = Ui.label(this, "", 15, 0xFFFFFFFF, true);
        subView.setGravity(Gravity.CENTER);
        subView.setShadowLayer(Ui.dp(this, 3), 0, 0, 0xFF000000);
        int sp = Ui.dp(this, 24);
        subView.setPadding(sp, Ui.dp(this, 4), sp, Ui.dp(this, 4));
        FrameLayout.LayoutParams sublp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        sublp.bottomMargin = Ui.dp(this, 84);
        root.addView(subView, sublp);

        // gesture hint overlay (center)
        hintView = Ui.label(this, "", 16, Ui.INK, true);
        hintView.setGravity(Gravity.CENTER);
        hintView.setBackgroundColor(0xAA0C0906);
        hintView.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
        hintView.setVisibility(View.GONE);
        root.addView(hintView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // top bar (vertical: row + now/next subtitle)
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setBackgroundColor(0xAA0C0906);
        int p = Ui.dp(this, 10);
        topBar.setPadding(p, p, p, p);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        logoView = new ImageView(this);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 36));
        logoView.setLayoutParams(llp);
        logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(logoView);
        titleText = Ui.label(this, "", 16, Ui.INK, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMargins(Ui.dp(this, 8), 0, 0, 0);
        titleText.setLayoutParams(tlp);
        row.addView(titleText);
        clockText = Ui.label(this, "", 12, Ui.MUTED, false);
        row.addView(clockText);
        posText = Ui.label(this, "", 12, Ui.MUTED, false);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.setMargins(Ui.dp(this, 8), 0, 0, 0);
        posText.setLayoutParams(plp);
        row.addView(posText);
        Button close = Ui.circleBtn(this, "✕", 16);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        // in-player channel browser: search + switch without leaving playback
        Button drawerBtn = Ui.circleBtn(this, "📺", 15);
        drawerBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleDrawer(); }
        });
        row.addView(drawerBtn);
        // picture-in-picture: keep playing in a screen corner
        Button pipBtn = Ui.circleBtn(this, "PiP", 10);
        pipBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { enterPip(); }
        });
        if (android.os.Build.VERSION.SDK_INT >= 26) row.addView(pipBtn);
        row.addView(close);
        topBar.addView(row);

        nowNextText = Ui.label(this, "", 12, Ui.TEAL, false);
        nowNextText.setPadding(Ui.dp(this, 64), Ui.dp(this, 2), 0, 0);
        nowNextText.setVisibility(View.GONE);
        topBar.addView(nowNextText);

        FrameLayout.LayoutParams tblp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        root.addView(topBar, tblp);

        // bottom controls
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setBackgroundColor(0xAA0C0906);
        bottomBar.setPadding(p, p, p, p);
        playBtn = Ui.circleBtn(this, "⏸", 18);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { togglePlay(); }
        });
        bottomBar.addView(playBtn);
        Button prevCh = Ui.circleBtn(this, "⏮", 15);
        prevCh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { zap(false); }
        });
        bottomBar.addView(prevCh);
        Button nextCh = Ui.circleBtn(this, "⏭", 15);
        nextCh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { zap(true); }
        });
        bottomBar.addView(nextCh);
        seek = new SeekBar(this);
        LinearLayout.LayoutParams sklp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        seek.setLayoutParams(sklp);
        seek.setVisibility(isVod ? View.VISIBLE : View.GONE);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int pr, boolean fromUser) {
                if (fromUser && player != null) {
                    long d = player.getDuration();
                    if (d > 0 && d != C.TIME_UNSET) player.seekTo(pr * d / 100);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        bottomBar.addView(seek);
        aspectBtn = Ui.circleBtn(this, "⛶", 15);
        aspectBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cycleAspect(); }
        });
        bottomBar.addView(aspectBtn);
        Button subBtn = Ui.circleBtn(this, "💬", 15);
        subBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlgSubs(); }
        });
        bottomBar.addView(subBtn);
        Button sleepBtn = Ui.circleBtn(this, "⏱", 16);
        sleepBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlgSleep(); }
        });
        bottomBar.addView(sleepBtn);
        TextView liveBadge = Ui.label(this, isVod ? "" : "● LIVE", 13, Ui.RED, true);
        bottomBar.addView(liveBadge);
        // stream quality badge (SD / HD / FHD / 4K), filled in when the
        // video size is known — like OTT Navigator shows
        qBadge = Ui.label(this, "", 12, Ui.TEAL, true);
        qBadge.setVisibility(View.GONE);
        bottomBar.addView(qBadge);
        FrameLayout.LayoutParams bllp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(bottomBar, bllp);

        buildChanDrawer();

        // gestures on the video area: tap toggles controls,
        // vertical swipe on left = brightness, on right = volume
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (inPip) return false; // PiP window: leave taps to the system
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getX();
                        downY = e.getY();
                        swiping = false;
                        // NOTE: never cancel the hint auto-hide here. A tap
                        // right after a swipe used to remove the pending hide
                        // and strand the hint on screen forever. showHint()
                        // re-arms the hide on every swipe update instead, so
                        // the hint can never outlive 1200ms past its last update.
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getX() - downX;
                        float dy = e.getY() - downY;
                        if (!swiping && Math.abs(dy) > Ui.dp(PlayerActivity.this, 24)
                                && Math.abs(dy) > Math.abs(dx) * 1.5f) {
                            swiping = true;
                            swipeBright = downX < root.getWidth() / 2f;
                            if (swipeBright) {
                                float b = getWindow().getAttributes().screenBrightness;
                                swipeStartBright = b < 0 ? 0.5f : b;
                            } else {
                                swipeMaxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                                swipeStartVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                            }
                        }
                        if (swiping) {
                            float frac = (downY - e.getY()) / (root.getHeight() * 0.8f);
                            if (swipeBright) {
                                float b = swipeStartBright + frac;
                                if (b < 0.01f) b = 0.01f;
                                if (b > 1f) b = 1f;
                                WindowManager.LayoutParams lp = getWindow().getAttributes();
                                lp.screenBrightness = b;
                                getWindow().setAttributes(lp);
                                showHint("🔆", (int) (b * 100) + "%");
                            } else {
                                int vol = swipeStartVol + Math.round(frac * swipeMaxVol);
                                if (vol < 0) vol = 0;
                                if (vol > swipeMaxVol) vol = swipeMaxVol;
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
                                showHint("🔊", vol + " / " + swipeMaxVol);
                            }
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (swiping) {
                            swiping = false;
                            // hint auto-hide was already re-armed by the last
                            // showHint(); nothing to do here
                        } else if (e.getAction() == MotionEvent.ACTION_UP) {
                            setControls(!controlsVisible);
                        }
                        return true;
                }
                return false;
            }
        });

        setContentView(root);
        root.post(new Runnable() {
            @Override public void run() { layoutSurface(); }
        });
    }

    // ---------------- fullscreen ----------------
    private void enterFullscreen() {
        android.view.Window w = getWindow();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            // draw behind system bars; content (incl. cutout area) is ours
            w.setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterFullscreen();
    }

    // ---------------- aspect ratio ----------------
    private void cycleAspect() {
        aspectMode = (aspectMode + 1) % 3;
        layoutSurface();
        showHint("⭐", "Aspect: " + ASPECT_NAMES[aspectMode]);
        scheduleHide();
    }

    private void layoutSurface() {
        if (root == null || surface == null) return;
        int cw = root.getWidth();
        int ch = root.getHeight();
        if (cw <= 0 || ch <= 0) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            cw = dm.widthPixels;
            ch = dm.heightPixels;
        }
        FrameLayout.LayoutParams lp;
        if (aspectMode == 1 || videoW <= 0 || videoH <= 0) {
            // Fill: stretch to full surface (may distort)
            lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER);
        } else {
            float vr = videoW / (float) videoH;
            float cr = cw / (float) ch;
            int w, h;
            if (aspectMode == 0) { // Fit: letterbox, keep ratio
                if (vr > cr) { w = cw; h = (int) (cw / vr); }
                else { h = ch; w = (int) (ch * vr); }
            } else { // Zoom: fill keeping ratio, crop overflow
                if (vr > cr) { h = ch; w = (int) (ch * vr); }
                else { w = cw; h = (int) (cw / vr); }
            }
            lp = new FrameLayout.LayoutParams(w, h, Gravity.CENTER);
        }
        surface.setLayoutParams(lp);
    }

    // ---------------- stream quality badge ----------------
    private void updateQualityBadge(int height) {
        if (qBadge == null) return;
        String q = height >= 2160 ? "4K"
                : height >= 1080 ? "FHD"
                : height >= 720 ? "HD"
                : height > 0 ? "SD" : "";
        qBadge.setText(q);
        qBadge.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ---------------- in-player channel drawer ----------------
    private void buildChanDrawer() {
        chanDrawer = new LinearLayout(this);
        chanDrawer.setOrientation(LinearLayout.VERTICAL);
        chanDrawer.setBackgroundColor(0xF217100A);
        // swallow taps so they don't toggle the player controls underneath
        chanDrawer.setClickable(true);
        chanDrawer.setVisibility(View.GONE);
        int p = Ui.dp(this, 12);
        chanDrawer.setPadding(p, p, p, p);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.label(this, "📺 Channels", 16, Ui.INK, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tlp);
        head.addView(title);
        Button x = Ui.circleBtn(this, "✕", 14);
        x.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleDrawer(); }
        });
        head.addView(x);
        chanDrawer.addView(head);

        chanSearch = Ui.field(this, "🔍 Search…");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        chanSearch.setLayoutParams(slp);
        chanSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                drawerQuery = s.toString().toLowerCase().trim();
                filterDrawer();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        chanDrawer.addView(chanSearch);

        chanListView = new ListView(this);
        chanListView.setDividerHeight(0);
        chanAdapter = new BaseAdapter() {
            @Override public int getCount() { return drawerShown.size(); }
            @Override public Object getItem(int i) { return drawerShown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup parent) {
                Channel c = drawerShown.get(i);
                boolean cur = c == PlayerQueue.current();
                LinearLayout row = new LinearLayout(PlayerActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int rp = Ui.dp(PlayerActivity.this, 10);
                row.setPadding(rp, Ui.dp(PlayerActivity.this, 8), rp,
                        Ui.dp(PlayerActivity.this, 8));
                TextView nm = Ui.label(PlayerActivity.this,
                        (cur ? "▶ " : "") + c.name, 14,
                        cur ? Ui.TEAL : Ui.INK, cur);
                LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                nm.setLayoutParams(nlp);
                nm.setMaxLines(1);
                row.addView(nm);
                return row;
            }
        };
        chanListView.setAdapter(chanAdapter);
        chanListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
                playFromDrawer(drawerShown.get(pos));
            }
        });
        chanDrawer.addView(chanListView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                Ui.dp(this, 320), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        root.addView(chanDrawer, dlp);
    }

    private void toggleDrawer() {
        boolean show = chanDrawer.getVisibility() != View.VISIBLE;
        if (show) {
            drawerQuery = "";
            chanSearch.setText("");
            filterDrawer();
            handler.removeCallbacks(hideTask); // keep controls up while browsing
        } else {
            scheduleHide();
        }
        chanDrawer.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void filterDrawer() {
        drawerShown.clear();
        for (Channel c : PlayerQueue.full()) {
            if (drawerShown.size() >= 500) break;
            if (!drawerQuery.isEmpty()
                    && !c.name.toLowerCase().contains(drawerQuery)) continue;
            drawerShown.add(c);
        }
        if (chanAdapter != null) chanAdapter.notifyDataSetChanged();
    }

    /** Switch to a channel picked from the drawer — playback never leaves. */
    private void playFromDrawer(Channel c) {
        List<Channel> full = PlayerQueue.full();
        int idx = full.indexOf(c);
        PlayerQueue.set(full, Math.max(0, idx));
        isVod = c.kind != Channel.LIVE;
        seek.setVisibility(isVod ? View.VISIBLE : View.GONE);
        chanDrawer.setVisibility(View.GONE);
        setControls(true);
        playCurrent();
        if (chanAdapter != null) chanAdapter.notifyDataSetChanged();
    }

    // ---------------- picture-in-picture ----------------
    /** Shrink into a corner window that keeps playing. */
    private void enterPip() {
        if (android.os.Build.VERSION.SDK_INT < 26 || player == null) return;
        try {
            android.app.PictureInPictureParams.Builder b =
                    new android.app.PictureInPictureParams.Builder();
            int w = videoW > 0 ? videoW : 16;
            int h = videoH > 0 ? videoH : 9;
            b.setAspectRatio(new android.util.Rational(w, h));
            enterPictureInPictureMode(b.build());
        } catch (Exception ignored) {}
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
                                             android.content.res.Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        inPip = isInPictureInPictureMode;
        if (isInPictureInPictureMode) {
            if (chanDrawer != null) chanDrawer.setVisibility(View.GONE);
            topBar.setVisibility(View.GONE);
            bottomBar.setVisibility(View.GONE);
            spinner.setVisibility(View.GONE);
            if (hintView != null) hintView.setVisibility(View.GONE);
            handler.removeCallbacks(hideTask);
        } else {
            setControls(true);
            enterFullscreen();
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Home button: shrink into a corner window instead of stopping.
        if (player != null && player.isPlaying()) enterPip();
    }

    @Override
    public void onBackPressed() {
        if (chanDrawer != null && chanDrawer.getVisibility() == View.VISIBLE) {
            toggleDrawer();
            return;
        }
        super.onBackPressed();
    }

    // ---------------- gesture hint ----------------
    private void showHint(String icon, String text) {
        hintView.setText(icon + "  " + text);
        hintView.setVisibility(View.VISIBLE);
        // Re-arm auto-hide on every update: guarantees the hint clears even
        // if this touch stream's ACTION_UP never reaches us (missed on some
        // devices when a system gesture steals the stream).
        handler.removeCallbacks(hintHideTask);
        handler.postDelayed(hintHideTask, 1200);
    }

    // ---------------- subtitles ----------------
    private void dlgSubs() {
        if (player == null) return;
        final ArrayList<String> langs = new ArrayList<>();
        final ArrayList<String> labels = new ArrayList<>();
        Tracks tracks = player.getCurrentTracks();
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < g.length; i++) {
                String lang = g.getTrackFormat(i).language;
                if (lang == null) lang = "";
                if (langs.contains(lang)) continue;
                langs.add(lang);
                String label = g.getTrackFormat(i).label;
                if (label == null || label.isEmpty()) {
                    label = lang.isEmpty() ? ("Track " + (langs.size())) : lang;
                }
                labels.add(label);
            }
        }
        if (langs.isEmpty()) {
            Toast.makeText(this, "No subtitles in this stream", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] items = new String[labels.size() + 1];
        items[0] = "Off";
        for (int i = 0; i < labels.size(); i++) items[i + 1] = labels.get(i);
        new AlertDialog.Builder(this)
                .setTitle("Subtitles")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (w == 0) {
                            player.setTrackSelectionParameters(
                                    player.getTrackSelectionParameters().buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            .build());
                            subView.setText("");
                            Toast.makeText(PlayerActivity.this,
                                    "Subtitles off", Toast.LENGTH_SHORT).show();
                        } else {
                            String lang = langs.get(w - 1);
                            player.setTrackSelectionParameters(
                                    player.getTrackSelectionParameters().buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            .setPreferredTextLanguage(lang.isEmpty() ? null : lang)
                                            .build());
                            Toast.makeText(PlayerActivity.this,
                                    "Subtitles: " + items[w], Toast.LENGTH_SHORT).show();
                        }
                        scheduleHide();
                    }
                }).show();
    }

    private void dlgSleep() {
        final String[] opts = {"Off", "15 min", "30 min", "60 min", "90 min", "120 min"};
        new AlertDialog.Builder(this)
                .setTitle("Sleep timer")
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        handler.removeCallbacks(sleepTask);
                        sleepTask = null;
                        if (w > 0) {
                            int mins = new int[]{0, 15, 30, 60, 90, 120}[w];
                            sleepTask = new Runnable() {
                                @Override public void run() { finish(); }
                            };
                            handler.postDelayed(sleepTask, mins * 60L * 1000L);
                            Toast.makeText(PlayerActivity.this,
                                    "Sleep in " + mins + " min", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(PlayerActivity.this,
                                    "Sleep timer off", Toast.LENGTH_SHORT).show();
                        }
                        scheduleHide();
                    }
                }).show();
    }

    private void playCurrent() {
        final Channel c = PlayerQueue.current();
        if (c == null) { finish(); return; }
        titleText.setText(c.name);
        posText.setText(PlayerQueue.position());
        ImageLoader.load(c.logo, logoView, android.R.drawable.ic_media_play);
        errText.setText("");
        updateQualityBadge(0); // hide until the new stream's size is known
        nowNextText.setVisibility(View.GONE);
        updateClock();
        // record recent (skip series containers)
        if (c.kind != Channel.SERIES) {
            String accId = PlayerQueue.accountId();
            if (!accId.isEmpty()) new Store(this).addRecent(accId, c.key);
        }
        // Now/Next for Xtream live channels
        if (c.kind == Channel.LIVE && c.server != null && !c.server.isEmpty()) {
            new Thread(new Runnable() {
                @Override public void run() {
                    final List<XtreamClient.Program> progs = XtreamClient.shortEpg(c);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (progs.isEmpty()) return;
                            StringBuilder sb = new StringBuilder();
                            XtreamClient.Program now = progs.get(0);
                            sb.append("Now: ").append(now.title);
                            String tr = now.timeRange();
                            if (!tr.startsWith("–") && tr.length() > 1) sb.append(" (").append(tr).append(")");
                            if (progs.size() > 1) {
                                XtreamClient.Program nx = progs.get(1);
                                sb.append("   •   Next: ").append(nx.title);
                            }
                            nowNextText.setText(sb.toString());
                            nowNextText.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }).start();
        }
        spinner.setVisibility(View.VISIBLE);
        playBtn.setText("⏸");
        videoW = 0;
        videoH = 0;
        layoutSurface();
        player.stop();
        player.clearMediaItems();
        player.setMediaItem(MediaItem.fromUri(c.url));
        player.prepare();
        player.setPlayWhenReady(true);
        scheduleHide();
        handler.removeCallbacks(seekTask);
        handler.post(seekTask);
    }

    private void updateClock() {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("HH:mm");
            clockText.setText(f.format(new java.util.Date()));
        } catch (Exception ignored) {}
    }

    private void togglePlay() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
            playBtn.setText("▶");
        } else {
            player.play();
            playBtn.setText("⏸");
        }
        scheduleHide();
    }

    private void setControls(boolean show) {
        controlsVisible = show;
        topBar.setVisibility(show ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(show ? View.VISIBLE : View.GONE);
        handler.removeCallbacks(hideTask);
        if (show) scheduleHide();
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideTask);
        handler.postDelayed(hideTask, 3500);
    }

    private void zap(boolean next) {
        if (!PlayerQueue.hasMultiple()) {
            Toast.makeText(this, "Single item", Toast.LENGTH_SHORT).show();
            return;
        }
        if (next) PlayerQueue.next(); else PlayerQueue.prev();
        isVod = PlayerQueue.current().kind != Channel.LIVE;
        seek.setVisibility(isVod ? View.VISIBLE : View.GONE);
        setControls(true);
        playCurrent();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                zap(false);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                zap(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                setControls(!controlsVisible);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                togglePlay();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // In PiP the video must keep playing in the corner window.
        boolean pip = android.os.Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode();
        if (player != null && !pip) player.pause();
        handler.removeCallbacks(seekTask);
        handler.removeCallbacks(hideTask);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (root != null) {
            root.post(new Runnable() {
                @Override public void run() { layoutSurface(); }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterFullscreen();
        if (player != null && controlsVisible && !inPip) player.play();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(seekTask);
        handler.removeCallbacks(hideTask);
        if (sleepTask != null) handler.removeCallbacks(sleepTask);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
