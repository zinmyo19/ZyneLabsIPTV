package com.zynelabs.iptv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
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
import com.zynelabs.iptv.data.StreamRecorder;
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
    private Button seekBackBtn, seekFwdBtn;
    private Handler handler = new Handler();
    private boolean isVod = false;
    private boolean controlsVisible = true;
    private boolean resolvingLink = false; // stalker create_link in flight
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
    private Button catchupBtn;
    private Button recBtn;
    private StreamRecorder recorder = new StreamRecorder();
    private boolean catchupMode = false;
    private Channel catchupChan = null;
    private AudioManager audioManager;
    private int aspectMode = 0; // 0=Fit 1=Fill 2=Zoom
    private float videoScale = 1.0f; // OTT-style video scale mode (0.7–1.4)
    private long pendingResumeMs = 0; // VOD "continue watching" offer
    private static final String[] ASPECT_NAMES = {"Fit", "Fill", "Zoom"};
    private int videoW = 0, videoH = 0;
    private float downX, downY;
    private boolean swiping = false, swipeBright = false;
    private int swipeStartVol = 0, swipeMaxVol = 15;
    private float swipeStartBright = 0.5f;
    private Runnable hintHideTask = new Runnable() {
        @Override public void run() { if (hintView != null) hintView.setVisibility(View.GONE); }
    };

    // OTT-style channel number direct entry (remote 0–9)
    private String numBuf = "";
    private Runnable numTask = new Runnable() {
        @Override public void run() { commitNumber(); }
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
        Ui.applyTheme(this);
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
        videoScale = new Store(this).videoScale();
        buildPlayer();
        build();
        Ui.enableTvFocus(root);
        if (playBtn != null) playBtn.requestFocus(); // TV: D-pad starts here
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
                    if (state == Player.STATE_READY && pendingResumeMs > 0) {
                        offerResume(pendingResumeMs);
                        pendingResumeMs = 0;
                    }
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
        Button close = Ui.flatBtn(this, "✕", 16);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        // in-player channel browser: search + switch without leaving playback
        Button drawerBtn = Ui.flatBtn(this, "📺", 15);
        drawerBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleDrawer(); }
        });
        row.addView(drawerBtn);
        // picture-in-picture: keep playing in a screen corner
        Button pipBtn = Ui.flatBtn(this, "PiP", 10);
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
        playBtn = Ui.flatBtn(this, "⏸", 18);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { togglePlay(); }
        });
        bottomBar.addView(playBtn);
        Button prevCh = Ui.flatBtn(this, "⏮", 15);
        prevCh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { zap(false); }
        });
        bottomBar.addView(prevCh);
        Button nextCh = Ui.flatBtn(this, "⏭", 15);
        nextCh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { zap(true); }
        });
        bottomBar.addView(nextCh);
        // ±10s seek buttons (VOD / media library only — live has no timeline)
        seekBackBtn = Ui.flatBtn(this, "⏪", 15);
        seekBackBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (player != null)
                    player.seekTo(Math.max(0, player.getCurrentPosition() - 10_000));
            }
        });
        seekBackBtn.setVisibility(isVod ? View.VISIBLE : View.GONE);
        bottomBar.addView(seekBackBtn);
        seekFwdBtn = Ui.flatBtn(this, "⏩", 15);
        seekFwdBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (player != null) {
                    long d = player.getDuration();
                    long np = player.getCurrentPosition() + 10_000;
                    player.seekTo(d > 0 && d != C.TIME_UNSET ? Math.min(np, d) : np);
                }
            }
        });
        seekFwdBtn.setVisibility(isVod ? View.VISIBLE : View.GONE);
        bottomBar.addView(seekFwdBtn);
        seek = new SeekBar(this);
        LinearLayout.LayoutParams sklp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        seek.setLayoutParams(sklp);
        updateVodUi();
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
        aspectBtn = Ui.flatBtn(this, "⛶", 15);
        aspectBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cycleAspect(); }
        });
        aspectBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showScaleDialog(); return true; }
        });
        bottomBar.addView(aspectBtn);
        Button subBtn = Ui.flatBtn(this, "💬", 15);
        subBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlgSubs(); }
        });
        bottomBar.addView(subBtn);
        Button sleepBtn = Ui.flatBtn(this, "⏱", 16);
        sleepBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlgSleep(); }
        });
        bottomBar.addView(sleepBtn);
        // playback speed (OTT-style)
        Button speedBtn = Ui.flatBtn(this, "🏃", 14);
        speedBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlgSpeed(); }
        });
        bottomBar.addView(speedBtn);
        // audio track selection (OTT-style)
        Button audioBtn = Ui.flatBtn(this, "🎧", 14);
        audioBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlgAudio(); }
        });
        bottomBar.addView(audioBtn);
        // catch-up / archive (Xtream timeshift, OTT-style) — shown only
        // for live channels whose provider enables archive
        catchupBtn = Ui.flatBtn(this, "\uD83D\uDCFC", 14);
        catchupBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (catchupMode) backToLive();
                else dlgCatchup();
            }
        });
        catchupBtn.setVisibility(View.GONE);
        bottomBar.addView(catchupBtn);
        // open in external player (VLC / MX Player)
        Button extBtn = Ui.flatBtn(this, "↗", 15);
        extBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openExternal(); }
        });
        bottomBar.addView(extBtn);
        // record while watching (Fred TV style) — HLS segments or raw
        // stream saved as .ts into Downloads/ZyneLabsIPTV/
        recBtn = Ui.flatBtn(this, "⏺", 15);
        recBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleRecord(); }
        });
        bottomBar.addView(recBtn);
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
        showHint("⭐", "Aspect: " + ASPECT_NAMES[aspectMode]
                + "  •  Scale: " + Math.round(videoScale * 100) + "% (hold ⛶ to change)");
        scheduleHide();
    }

    /** OTT-style video scale mode: 70%–140% zoom applied on top of the aspect mode. */
    private void showScaleDialog() {
        final int n = 15; // 70% .. 140% in 5% steps
        final String[] labels = new String[n];
        final float[] vals = new float[n];
        int checked = 6; // 100%
        for (int i = 0; i < n; i++) {
            vals[i] = 0.70f + i * 0.05f;
            labels[i] = Math.round(vals[i] * 100) + "%";
            if (Math.abs(vals[i] - videoScale) < 0.001f) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Video scale mode")
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        videoScale = vals[which];
                        new Store(PlayerActivity.this).setVideoScale(videoScale);
                        layoutSurface();
                        showHint("🔍", "Scale: " + labels[which]);
                        d.dismiss();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
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
            // video scale mode (OTT-style): zoom the fitted surface; the
            // parent clips the overflow so it behaves like crop-zoom
            w = Math.max(1, (int) (w * videoScale));
            h = Math.max(1, (int) (h * videoScale));
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
        TextView title = Ui.label(this, "Channels", 16, Ui.INK, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tlp);
        head.addView(title);
        Button x = Ui.flatBtn(this, "✕", 14);
        x.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleDrawer(); }
        });
        head.addView(x);
        chanDrawer.addView(head);

        chanSearch = Ui.field(this, "Search…");
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
        chanListView.setSelector(Ui.listSelector(this));
        chanListView.setDrawSelectorOnTop(true);
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
                        (cur ? "▶ " : "") + dispName(c), 14,
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
                    && !c.name.toLowerCase().contains(drawerQuery)
                    && !dispName(c).toLowerCase().contains(drawerQuery)) continue;
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
        updateVodUi();
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

    // ---------------- playback speed (OTT-style) ----------------
    private void dlgSpeed() {
        if (player == null) return;
        final float[] vals = {0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        final String[] labels = new String[vals.length];
        float cur = player.getPlaybackParameters().speed;
        int checked = 3;
        for (int i = 0; i < vals.length; i++) {
            labels[i] = (vals[i] == 1f ? "Normal" : vals[i] + "x");
            if (Math.abs(vals[i] - cur) < 0.01f) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Playback speed")
                .setSingleChoiceItems(labels, checked,
                        new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        player.setPlaybackSpeed(vals[w]);
                        showHint("🏃", "Speed: " + labels[w]);
                        d.dismiss();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    // ---------------- audio track selection (OTT-style) ----------------
    private void dlgAudio() {
        if (player == null) return;
        final ArrayList<String> langs = new ArrayList<>();
        final ArrayList<String> labels = new ArrayList<>();
        Tracks tracks = player.getCurrentTracks();
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < g.length; i++) {
                String lang = g.getTrackFormat(i).language;
                if (lang == null) lang = "";
                if (langs.contains(lang)) continue;
                langs.add(lang);
                String label = g.getTrackFormat(i).label;
                if (label == null || label.isEmpty()) {
                    label = lang.isEmpty() ? ("Track " + langs.size()) : lang;
                }
                labels.add(label);
            }
        }
        if (langs.size() < 2) {
            Toast.makeText(this,
                    langs.isEmpty() ? "No audio tracks in this stream"
                            : "Only one audio track",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] items = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Audio track")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String lang = langs.get(w);
                        player.setTrackSelectionParameters(
                                player.getTrackSelectionParameters().buildUpon()
                                        .setPreferredAudioLanguage(
                                                lang.isEmpty() ? null : lang)
                                        .build());
                        Toast.makeText(PlayerActivity.this,
                                "Audio: " + items[w], Toast.LENGTH_SHORT).show();
                        scheduleHide();
                    }
                }).show();
    }

    // ---------------- catch-up / archive (Xtream timeshift, OTT-style) ----------------
    private void dlgCatchup() {
        final Channel live = PlayerQueue.current();
        if (live == null || !live.archive) return;
        setControls(true);
        Toast.makeText(this, "Loading program guide…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                final java.util.List<XtreamClient.Program> all =
                        XtreamClient.catchupEpg(live);
                final java.util.List<XtreamClient.Program> past = new java.util.ArrayList<>();
                final java.util.List<Long> starts = new java.util.ArrayList<>();
                final java.util.List<Long> durs = new java.util.ArrayList<>();
                long now = System.currentTimeMillis();
                for (XtreamClient.Program pr : all) {
                    long st = XtreamClient.parseEpgTime(pr.start);
                    long en = XtreamClient.parseEpgTime(pr.stop);
                    if (st < 0 || st > now) continue; // only already-aired
                    long durMin = en > st ? (en - st) / 60000 : 60;
                    if (durMin < 1) durMin = 60;
                    past.add(pr); starts.add(st); durs.add(durMin);
                    if (past.size() >= 60) break;
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() { showCatchupList(live, past, starts, durs); }
                });
            }
        }).start();
    }

    private void showCatchupList(final Channel live,
                                 final java.util.List<XtreamClient.Program> past,
                                 final java.util.List<Long> starts,
                                 final java.util.List<Long> durs) {
        if (past.isEmpty()) {
            Toast.makeText(this, "No catch-up programs found", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] items = new String[past.size()];
        for (int i = 0; i < past.size(); i++) {
            XtreamClient.Program pr = past.get(i);
            items[i] = pr.timeRange() + "  " + pr.title;
        }
        new AlertDialog.Builder(this)
                .setTitle("\uD83D\uDCFC Catch-up — " + dispName(live))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        playCatchup(live, past.get(w), starts.get(w), durs.get(w));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void playCatchup(Channel live, XtreamClient.Program pr,
                             long startMs, long durMin) {
        String url = XtreamClient.timeshiftUrl(live, startMs, durMin);
        if (url.isEmpty()) {
            Toast.makeText(this, "Catch-up URL failed", Toast.LENGTH_SHORT).show();
            return;
        }
        Channel cu = new Channel();
        cu.kind = Channel.VOD; // seek bar visible, like VOD
        cu.key = live.key + "_cu" + startMs;
        cu.name = pr.title;
        cu.logo = live.logo;
        cu.group = live.group;
        cu.url = url;
        cu.server = live.server; cu.user = live.user; cu.pass = live.pass;
        cu.streamId = live.streamId;
        catchupMode = true;
        catchupChan = cu;
        isVod = true;
        updateVodUi();
        startPlayback(cu);
        showHint("\uD83D\uDCFC", pr.title);
        Toast.makeText(this, "📼 Catch-up — tap 📼 again for live",
                Toast.LENGTH_LONG).show();
    }

    /** Leave catch-up and return to the live channel. */
    private void backToLive() {
        catchupMode = false;
        catchupChan = null;
        Channel live = PlayerQueue.current();
        if (live == null) return;
        isVod = false;
        updateVodUi();
        playCurrent();
        Toast.makeText(this, "🔴 Back to live", Toast.LENGTH_SHORT).show();
    }

    // ---------------- external player (OTT-style fallback) ----------------
    private void openExternal() {
        Channel c = PlayerQueue.current();
        if (c == null || c.url == null || c.url.isEmpty()) {
            Toast.makeText(this, "No stream URL", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String mime = "video/*";
            String u = c.url.toLowerCase();
            if (u.contains(".m3u8")) mime = "application/x-mpegURL";
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(c.url), mime);
            startActivity(Intent.createChooser(i, "Open with…"));
        } catch (Exception e) {
            Toast.makeText(this, "No app can open this stream",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- channel number direct entry (OTT-style) ----------------
    private void onNumberKey(int digit) {
        if (numBuf.length() >= 4) numBuf = "";
        numBuf += digit;
        showHint("🔢", "Channel " + numBuf + "…");
        handler.removeCallbacks(numTask);
        handler.postDelayed(numTask, 1200);
        setControls(true);
    }

    private void commitNumber() {
        if (numBuf.isEmpty()) return;
        int n;
        try { n = Integer.parseInt(numBuf); } catch (Exception e) { n = -1; }
        numBuf = "";
        if (n < 1) return;
        Channel c = PlayerQueue.byNumber(n);
        if (c == null) {
            showHint("🔢", "No channel " + n);
            return;
        }
        if (!PlayerQueue.jumpToNumber(n)) {
            // not in the current zap list (e.g. filtered): play directly,
            // keeping the canonical full list intact for later number jumps
            PlayerQueue.playSingle(c);
        }
        isVod = PlayerQueue.current().kind != Channel.LIVE;
        updateVodUi();
        playCurrent();
        showHint("🔢", n + " · " + dispName(c));
    }

    private void offerResume(final long ms) {
        final Channel c = PlayerQueue.current();
        if (c == null) return;
        long dur = player != null ? player.getDuration() : 0;
        // stale bookmark (past the end) — drop it silently
        if (dur > 0 && dur != C.TIME_UNSET && ms >= dur - 15_000) {
            String accId = PlayerQueue.accountId();
            if (!accId.isEmpty()) new Store(this).clearResumePos(accId, c.key);
            return;
        }
        setControls(true);
        new AlertDialog.Builder(this)
                .setTitle("Continue watching?")
                .setMessage("Resume \"" + dispName(c) + "\" from " + fmtTime(ms) + "?")
                .setPositiveButton("▶ Resume", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (player != null) player.seekTo(ms);
                        scheduleHide();
                    }
                })
                .setNegativeButton("↺ Restart", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String accId = PlayerQueue.accountId();
                        if (!accId.isEmpty())
                            new Store(PlayerActivity.this).clearResumePos(accId, c.key);
                        scheduleHide();
                    }
                })
                .show();
    }

    private static String fmtTime(long ms) {
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec)
                : String.format("%d:%02d", m, sec);
    }

    private void playCurrent() {
        final Channel c = PlayerQueue.current();
        if (c == null) { finish(); return; }
        stopRecording(); // new channel = new stream; stop any active recording
        catchupMode = false;
        catchupChan = null;
        // Stalker portal channels need a fresh stream URL per play (create_link).
        if (c.stalkerCmd != null && !c.stalkerCmd.isEmpty() && !resolvingLink) {
            resolvingLink = true;
            spinner.setVisibility(View.VISIBLE);
            errText.setText("Resolving stream…");
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        final String url = com.zynelabs.iptv.data.StalkerClient.resolve(c);
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                resolvingLink = false;
                                c.url = url;
                                startPlayback(c);
                            }
                        });
                    } catch (final Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                resolvingLink = false;
                                spinner.setVisibility(View.GONE);
                                errText.setText("Stream failed: " + e.getMessage());
                            }
                        });
                    }
                }
            }).start();
            return;
        }
        startPlayback(c);
    }


    /** Display name: user's custom rename if set, else the playlist name. */
    private String dispName(Channel c) {
        String accId = PlayerQueue.accountId();
        if (!accId.isEmpty()) {
            String n = new Store(this).customName(accId, c.key);
            if (!n.isEmpty()) return n;
        }
        return c.name;
    }

    /** Begin ExoPlayer playback for a channel whose stream URL is ready. */
    private void startPlayback(final Channel c) {
        int num = (c.kind == Channel.LIVE) ? PlayerQueue.numberOf(c) : -1;
        titleText.setText(num > 0 ? num + " · " + dispName(c) : dispName(c));
        // catch-up button: only on live channels with provider archive support
        if (catchupBtn != null)
            catchupBtn.setVisibility(
                    (catchupMode || (c.kind == Channel.LIVE && c.archive))
                            ? View.VISIBLE : View.GONE);
        posText.setText(PlayerQueue.position());
        ImageLoader.load(c.logo, logoView, Ui.catArt(this, c));
        errText.setText("");
        updateQualityBadge(0); // hide until the new stream's size is known
        nowNextText.setVisibility(View.GONE);
        updateClock();
        player.setPlaybackSpeed(1f); // new channel: back to normal speed
        // VOD resume ("continue watching", like OTT Navigator)
        pendingResumeMs = 0;
        if (c.kind != Channel.LIVE) {
            String accId = PlayerQueue.accountId();
            if (!accId.isEmpty()) {
                long rz = new Store(this).resumePos(accId, c.key);
                if (rz > 10_000) pendingResumeMs = rz;
            }
        }
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

    /** Record-while-watching toggle (Fred TV style). */
    private void toggleRecord() {
        if (recorder.isRecording()) {
            recorder.stop();
            return;
        }
        final Channel c = PlayerQueue.current();
        if (c == null || c.url == null || c.url.isEmpty()) {
            Toast.makeText(this, "No stream to record", Toast.LENGTH_SHORT).show();
            return;
        }
        if (c.url.toLowerCase(java.util.Locale.US).contains(".mpd")) {
            Toast.makeText(this, "DASH recording not supported", Toast.LENGTH_SHORT).show();
            return;
        }
        final String fileName = StreamRecorder.fileNameFor(dispName(c));
        recorder.start(this, c.url, fileName, new StreamRecorder.Listener() {
            @Override public void onStarted(final String fn) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (recBtn != null) recBtn.setTextColor(Ui.RED);
                        Toast.makeText(PlayerActivity.this,
                                "Recording… " + fn, Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onStopped(final String fn, final long bytes) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (recBtn != null) recBtn.setTextColor(Ui.INK);
                        if (bytes > 0)
                            Toast.makeText(PlayerActivity.this,
                                    "Saved: Downloads/ZyneLabsIPTV/" + fn,
                                    Toast.LENGTH_LONG).show();
                        else
                            Toast.makeText(PlayerActivity.this,
                                    "Recording stopped (no data)",
                                    Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (recBtn != null) recBtn.setTextColor(Ui.INK);
                        Toast.makeText(PlayerActivity.this,
                                "Record failed: " + msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        scheduleHide();
    }

    private void stopRecording() {
        if (recorder.isRecording()) recorder.stop();
        if (recBtn != null) recBtn.setTextColor(Ui.INK);
    }

    private void setControls(boolean show) {
        controlsVisible = show;
        topBar.setVisibility(show ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && playBtn != null) playBtn.requestFocus();
        handler.removeCallbacks(hideTask);
        if (show) scheduleHide();
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideTask);
        handler.postDelayed(hideTask, 3500);
    }

    /** Show/hide the VOD-only timeline controls (seekbar + ±10s buttons). */
    private void updateVodUi() {
        int v = isVod ? View.VISIBLE : View.GONE;
        if (seek != null) seek.setVisibility(v);
        if (seekBackBtn != null) seekBackBtn.setVisibility(v);
        if (seekFwdBtn != null) seekFwdBtn.setVisibility(v);
    }

    private void zap(boolean next) {
        if (!PlayerQueue.hasMultiple()) {
            Toast.makeText(this, "Single item", Toast.LENGTH_SHORT).show();
            return;
        }
        if (next) PlayerQueue.next(); else PlayerQueue.prev();
        isVod = PlayerQueue.current().kind != Channel.LIVE;
        updateVodUi();
        setControls(true);
        playCurrent();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean drawerOpen = chanDrawer != null
                && chanDrawer.getVisibility() == View.VISIBLE;
        if (keyCode == KeyEvent.KEYCODE_BACK && drawerOpen) {
            toggleDrawer();
            return true;
        }
        // channel drawer open: let the list consume D-pad
        if (drawerOpen) return super.onKeyDown(keyCode, event);
        // OTT-style direct channel number entry (remote 0–9), live only
        if (!isVod) {
            int digit = -1;
            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9)
                digit = keyCode - KeyEvent.KEYCODE_0;
            else if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0
                    && keyCode <= KeyEvent.KEYCODE_NUMPAD_9)
                digit = keyCode - KeyEvent.KEYCODE_NUMPAD_0;
            if (digit >= 0) { onNumberKey(digit); return true; }
        }
        // transport keys always work
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                togglePlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                zap(true);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                zap(false);
                return true;
        }
        if (!controlsVisible) {
            // fullscreen watching: remote shortcuts (OTT-style)
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_CHANNEL_UP:
                    zap(false);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                    zap(true);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (isVod && player != null)
                        player.seekTo(Math.max(0, player.getCurrentPosition() - 10_000));
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (isVod && player != null)
                        player.seekTo(player.getCurrentPosition() + 10_000);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    setControls(true);
                    return true;
            }
            return super.onKeyDown(keyCode, event);
        }
        // controls visible: D-pad moves focus between buttons (now glowing),
        // CENTER/ENTER clicks the focused button via default handling
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // In PiP the video must keep playing in the corner window.
        boolean pip = android.os.Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode();
        if (player != null && !pip) player.pause();
        // VOD "continue watching" bookmark (OTT-style)
        if (isVod && player != null) {
            Channel c = PlayerQueue.current();
            String accId = PlayerQueue.accountId();
            if (c != null && !accId.isEmpty()) {
                Store st = new Store(this);
                long pos = player.getCurrentPosition();
                long dur = player.getDuration();
                if (pos > 10_000 && dur > 0 && dur != C.TIME_UNSET && pos < dur - 15_000) {
                    st.setResumePos(accId, c.key, pos);
                } else if (dur > 0 && dur != C.TIME_UNSET && pos >= dur - 15_000) {
                    st.clearResumePos(accId, c.key); // watched to the end
                }
            }
        }
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
        stopRecording();
        handler.removeCallbacks(seekTask);
        handler.removeCallbacks(hideTask);
        if (sleepTask != null) handler.removeCallbacks(sleepTask);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
