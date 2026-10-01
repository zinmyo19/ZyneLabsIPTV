package com.zynelabs.iptv.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Space-western theme view builders shared by all activities.
 *  Warm charcoal + amber whiskey + hologram cyan. (Constant names kept for compat.)
 *
 *  <p>Multi-theme support: the palette fields are mutable and reloaded from
 *  the user's theme choice via {@link #applyTheme(Context)}. Every activity
 *  calls it in onCreate() before building views, so the whole app follows
 *  one theme. The same palette names are shared with Dominic's other apps
 *  (VPN, Chinese Learn) so all his apps can look unified.</p>
 */
public class Ui {
    // default = Calm Night (overwritten by applyTheme)
    public static int PAPER = 0xFF0C0906;
    public static int CARD = 0xFF17100A;
    public static int CARD2 = 0xFF20150C;
    public static int TEAL = 0xFFFFAA33;
    public static int TEAL_DARK = 0xFF7A4A1E;
    public static int GOLD = 0xFF00E5FF;
    public static int RED = 0xFFFF5A4E;
    public static int INK = 0xFFF5EDE0;
    public static int MUTED = 0xFFA89A86;

    public static final String[] THEME_IDS =
            {"calm", "ocean", "ember", "grape", "paper"};
    public static final String[] THEME_NAMES =
            {"🌙 Calm Night", "🌊 Ocean Dark", "🔥 Ember", "💜 Grape", "⬜ Paper Light"};

    /** Load the user's theme into the palette. Call in every onCreate(). */
    public static void applyTheme(Context c) {
        artCache.clear(); // fallback art is tinted with the palette
        String t;
        try {
            t = new com.zynelabs.iptv.data.Store(c).theme();
        } catch (Exception e) { t = "calm"; }
        if ("ocean".equals(t)) {
            PAPER = 0xFF06121F; CARD = 0xFF0B1C30; CARD2 = 0xFF10263F;
            TEAL = 0xFF29B6F6; TEAL_DARK = 0xFF1565C0; GOLD = 0xFFFFD54F;
            RED = 0xFFEF5350; INK = 0xFFE8F4FF; MUTED = 0xFF8AA8C2;
        } else if ("ember".equals(t)) {
            PAPER = 0xFF1A0E0A; CARD = 0xFF241310; CARD2 = 0xFF2E1813;
            TEAL = 0xFFFF6E40; TEAL_DARK = 0xFFA63D22; GOLD = 0xFFFFD54F;
            RED = 0xFFFF5252; INK = 0xFFFFF0E6; MUTED = 0xFFC2A08A;
        } else if ("grape".equals(t)) {
            PAPER = 0xFF140F1E; CARD = 0xFF1D1629; CARD2 = 0xFF251D36;
            TEAL = 0xFFB388FF; TEAL_DARK = 0xFF5E35B1; GOLD = 0xFFFFE082;
            RED = 0xFFEF5350; INK = 0xFFF3EDFF; MUTED = 0xFFB8A8D2;
        } else if ("paper".equals(t)) {
            PAPER = 0xFFF7F3EC; CARD = 0xFFFFFFFF; CARD2 = 0xFFEFE7D8;
            TEAL = 0xFFB26A1B; TEAL_DARK = 0xFF7A4A1E; GOLD = 0xFF0097A7;
            RED = 0xFFD32F2F; INK = 0xFF2A2118; MUTED = 0xFF8A7A66;
        } else { // calm
            PAPER = 0xFF0C0906; CARD = 0xFF17100A; CARD2 = 0xFF20150C;
            TEAL = 0xFFFFAA33; TEAL_DARK = 0xFF7A4A1E; GOLD = 0xFF00E5FF;
            RED = 0xFFFF5A4E; INK = 0xFFF5EDE0; MUTED = 0xFFA89A86;
        }
    }

    public static String themeName(Context c) {
        String t = "calm";
        try { t = new com.zynelabs.iptv.data.Store(c).theme(); } catch (Exception ignored) {}
        for (int i = 0; i < THEME_IDS.length; i++)
            if (THEME_IDS[i].equals(t)) return THEME_NAMES[i];
        return THEME_NAMES[0];
    }

    public static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static GradientDrawable cardBg() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(CARD);
        d.setCornerRadius(14);
        d.setStroke(1, TEAL_DARK);
        return d;
    }

    /** Gradient card (subtle top-light) for tiles and rows. */
    public static GradientDrawable cardBgGrad(Context c) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{CARD2, CARD});
        d.setCornerRadius(dp(c, 14));
        d.setStroke(1, TEAL_DARK);
        return d;
    }

    /** Linear gradient background with rounded corners. */
    public static GradientDrawable gradientBg(Context c, int start, int end, int radiusDp) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /** Modern menu button: gradient, icon badge, title + subtitle, chevron. */
    public static LinearLayout menuButton(Context c, String icon, String title, String subtitle) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setBackground(gradientBg(c, TEAL_DARK, 0xFFC97A2E, 18));
        int p = dp(c, 16);
        b.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(c, 10));
        b.setLayoutParams(lp);
        b.setElevation(dp(c, 6));
        b.setFocusable(true);
        tvGlow(b);

        TextView badge = label(c, icon, 24, INK, false);
        badge.setBackground(pillBg(0x33FFFFFF));
        int bs = dp(c, 52);
        badge.setLayoutParams(new LinearLayout.LayoutParams(bs, bs));
        badge.setGravity(Gravity.CENTER);
        b.addView(badge);

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mlp.setMargins(dp(c, 14), 0, dp(c, 8), 0);
        mid.setLayoutParams(mlp);
        mid.addView(label(c, title, 17, INK, true));
        mid.addView(label(c, subtitle, 12, 0xBBF5EDE0, false));
        b.addView(mid);

        b.addView(label(c, "›", 26, GOLD, false));
        return b;
    }

    /** Circular translucent button for the player overlay. */
    public static Button circleBtn(Context c, String text, int sp) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextSize(sp);
        b.setTextColor(INK);
        b.setBackground(pillBg(0x4D000000));
        int s = dp(c, 48);
        b.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return b;
    }

    /** Flat transport glyph: no pill/gradient background, theme-aware text.
     *  D-pad/TV focus gets the subtle row highlight. */
    public static Button flatBtn(Context c, String text, int sp) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextSize(sp);
        b.setTextColor(INK);
        b.setBackground(rowSelector(c));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        int p = dp(c, 12);
        b.setPadding(p, dp(c, 8), p, dp(c, 8));
        b.setFocusable(true);
        return b;
    }

    public static GradientDrawable pillBg(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(999);
        return d;
    }

    public static TextView label(Context c, String t, int sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        return v;
    }

    public static LinearLayout spacer(Context c, int dpH) {
        LinearLayout s = new LinearLayout(c);
        s.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, dpH)));
        return s;
    }

    public static Button bigButton(Context c, String text, int bgColor, int textColor) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextSize(16);
        b.setTextColor(textColor);
        b.setAllCaps(false);
        b.setBackground(gradientBg(c, TEAL_DARK, 0xFFC97A2E, 18));
        b.setElevation(dp(c, 6));
        int p = dp(c, 14);
        b.setPadding(p, p, p, p);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    public static Button chip(Context c, String text, boolean selected) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(selected ? PAPER : TEAL);
        GradientDrawable d;
        if (selected) {
            d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{TEAL, TEAL_DARK});
            d.setCornerRadius(dp(c, 999));
            b.setElevation(dp(c, 3));
        } else {
            d = new GradientDrawable();
            d.setColor(CARD);
            d.setCornerRadius(dp(c, 999));
            d.setStroke(1, TEAL_DARK);
        }
        b.setBackground(d);
        int hp = dp(c, 14), vp = dp(c, 8);
        b.setPadding(hp, vp, hp, vp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(c, 8), 0);
        b.setLayoutParams(lp);
        b.setFocusable(true);
        tvGlow(b);
        return b;
    }

    public static EditText field(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(INK);
        e.setTextSize(15);
        GradientDrawable d = new GradientDrawable();
        d.setColor(CARD2);
        d.setCornerRadius(dp(c, 10));
        d.setStroke(1, TEAL_DARK);
        e.setBackground(d);
        int p = dp(c, 12);
        e.setPadding(p, p, p, p);
        e.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    public static TextView emptyView(Context c, String text) {
        TextView v = label(c, text, 15, MUTED, false);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(c, 16), dp(c, 48), dp(c, 16), dp(c, 48));
        return v;
    }

    /** Compact top-bar icon button: the default Button minWidth (88dp) makes
     *  a row of icons overflow and pushes the last one off screen. */
    public static Button barBtn(Context c, String text, float size) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextSize(size);
        b.setBackgroundColor(0x00000000);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        int p = dp(c, 10);
        b.setPadding(p, dp(c, 4), p, dp(c, 4));
        return b;
    }

    public static TextView sectionHeader(Context c, String text) {
        TextView v = label(c, text, 13, GOLD, true);
        v.setPadding(dp(c, 4), dp(c, 18), dp(c, 4), dp(c, 6));
        return v;
    }

    /** A tappable settings row: icon + title + subtitle + value. */
    public static LinearLayout settingRow(Context c, String icon, String title, String subtitle, String value) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(cardBgGrad(c));
        row.setElevation(dp(c, 2));
        int p = dp(c, 14);
        row.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(c, 8));
        row.setLayoutParams(lp);
        row.setFocusable(true);
        tvGlow(row);

        TextView ic = label(c, icon, 22, TEAL, false);
        row.addView(ic);

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mlp.setMargins(dp(c, 12), 0, dp(c, 8), 0);
        mid.setLayoutParams(mlp);
        mid.addView(label(c, title, 15, INK, true));
        if (subtitle != null && !subtitle.isEmpty()) mid.addView(label(c, subtitle, 12, MUTED, false));
        row.addView(mid);

        TextView val = label(c, value == null ? "" : value, 13, TEAL, false);
        row.addView(val);
        TextView chev = label(c, "›", 20, MUTED, false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dp(c, 6), 0, 0, 0);
        chev.setLayoutParams(clp);
        row.addView(chev);
        return row;
    }

    /** Compact horizontal tile for a button row (weight set by caller). */
    public static LinearLayout tileButton(Context c, String icon, String title) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setBackground(gradientBg(c, TEAL_DARK, 0xFFC97A2E, 16));
        int p = dp(c, 12);
        b.setPadding(p, p, p, p);
        b.setElevation(dp(c, 4));
        b.setFocusable(true);
        tvGlow(b);
        TextView ic = label(c, icon, 28, INK, false);
        ic.setGravity(Gravity.CENTER);
        b.addView(ic);
        TextView t = label(c, title, 13, INK, true);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(0, dp(c, 6), 0, 0);
        t.setLayoutParams(tlp);
        b.addView(t);
        return b;
    }

    /** Compact single-line settings row (fits the whole page on one screen). */
    public static LinearLayout settingRowS(Context c, String icon, String title, String value) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(cardBgGrad(c));
        row.setElevation(dp(c, 1));
        int p = dp(c, 10);
        row.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(c, 6));
        row.setLayoutParams(lp);
        row.setFocusable(true);
        tvGlow(row);

        row.addView(label(c, icon, 18, TEAL, false));
        TextView t = label(c, title, 14, INK, true);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mlp.setMargins(dp(c, 10), 0, dp(c, 6), 0);
        t.setLayoutParams(mlp);
        row.addView(t);
        if (value != null && !value.isEmpty()) row.addView(label(c, value, 12, TEAL, false));
        TextView chev = label(c, "›", 18, MUTED, false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dp(c, 4), 0, 0, 0);
        chev.setLayoutParams(clp);
        row.addView(chev);
        return row;
    }

    /** Round contact icon button with a small label underneath (weight set by caller). */
    public static LinearLayout contactBtn(Context c, String icon, String labelText) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        tvGlow(b);
        TextView badge = label(c, icon, 24, INK, false);
        badge.setBackground(pillBg(CARD2));
        int s = dp(c, 54);
        badge.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        badge.setGravity(Gravity.CENTER);
        b.addView(badge);
        TextView l = label(c, labelText, 11, MUTED, false);
        l.setGravity(Gravity.CENTER);
        l.setMaxLines(1);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.setMargins(0, dp(c, 4), 0, 0);
        l.setLayoutParams(llp);
        b.addView(l);
        return b;
    }

    // ---- TV / D-pad remote support ----
    // Custom backgrounds swallow the default focused state, so remote
    // navigation is invisible on TV. tvGlow gives a cyan glow + slight zoom
    // while a view holds focus. enableTvFocus walks a whole layout once.
    private static final int TAG_FOCUS = 0x7E1A0001;
    private static final int TAG_HOVER = 0x7E1A0002;

    public static void tvGlow(final View v) {
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean hasFocus) {
                view.setTag(TAG_FOCUS, hasFocus);
                refreshGlow(view);
            }
        });
        // air-mouse / pointer mode: hover must highlight too, focus won't
        v.setOnHoverListener(new View.OnHoverListener() {
            @Override public boolean onHover(View view, android.view.MotionEvent e) {
                int a = e.getAction();
                if (a == android.view.MotionEvent.ACTION_HOVER_ENTER) {
                    view.setTag(TAG_HOVER, true);
                    refreshGlow(view);
                } else if (a == android.view.MotionEvent.ACTION_HOVER_EXIT) {
                    view.setTag(TAG_HOVER, false);
                    refreshGlow(view);
                }
                return false;
            }
        });
    }

    private static void refreshGlow(View view) {
        boolean on = Boolean.TRUE.equals(view.getTag(TAG_FOCUS))
                || Boolean.TRUE.equals(view.getTag(TAG_HOVER));
        android.graphics.drawable.Drawable bg = view.getBackground();
        if (bg != null) {
            bg.mutate();
            if (on) bg.setColorFilter(0xAA00E5FF,
                    android.graphics.PorterDuff.Mode.SRC_ATOP);
            else bg.clearColorFilter();
        }
        view.setScaleX(on ? 1.05f : 1f);
        view.setScaleY(on ? 1.05f : 1f);
    }

    public static void enableTvFocus(View root) {
        if (root == null) return;
        if (root.isFocusable()) tvGlow(root);
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) enableTvFocus(g.getChildAt(i));
        }
    }

    // ---- per-category fallback channel art ----
    // Channels without a provider logo get a big category glyph on a tinted
    // tile (OTT-style) instead of a generic placeholder icon.
    private static final java.util.Map<String,
            android.graphics.drawable.Drawable> artCache =
            new java.util.HashMap<>();

    /** Fallback art for a channel: category emoji glyph centered on a tile. */
    public static android.graphics.drawable.Drawable catArt(
            Context c, com.zynelabs.iptv.data.Channel ch) {
        String cat = (ch == null) ? "" : ch.getSmartCat();
        android.graphics.drawable.Drawable d = artCache.get(cat);
        if (d != null) return d;
        String glyph = "📺";
        int sp = cat.indexOf(' ');
        if (sp > 0) glyph = cat.substring(0, sp);
        int size = dp(c, 96);
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
        android.graphics.Paint bg = new android.graphics.Paint();
        bg.setColor(CARD2);
        cv.drawRect(0, 0, size, size, bg);
        android.graphics.Paint fg = new android.graphics.Paint();
        fg.setAntiAlias(true);
        fg.setColor(TEAL);
        fg.setTextSize(size * 0.55f);
        fg.setTextAlign(android.graphics.Paint.Align.CENTER);
        android.graphics.Paint.FontMetrics fm = fg.getFontMetrics();
        float y = size / 2f - (fm.ascent + fm.descent) / 2f;
        cv.drawText(glyph, size / 2f, y, fg);
        d = new android.graphics.drawable.BitmapDrawable(c.getResources(), bmp);
        artCache.put(cat, d);
        return d;
    }

    /** Visible selector for ListViews so the focused row glows on TV. */
    public static android.graphics.drawable.StateListDrawable listSelector(Context c) {
        android.graphics.drawable.StateListDrawable d =
                new android.graphics.drawable.StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0x5500E5FF);
        focused.setCornerRadius(10);
        focused.setStroke(dp(c, 2), 0xFF00E5FF);
        d.addState(new int[]{android.R.attr.state_focused}, focused);
        d.addState(new int[]{android.R.attr.state_pressed}, focused);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0x00000000);
        d.addState(new int[]{}, normal);
        return d;
    }

    // ---- OTT-style plain text rows ----
    // Provider / category / channel lists are simple text rows (icon + title
    // + count), not buttons: no gradients, no chips. D-pad/TV focus gets a
    // subtle theme-aware highlight instead of the button glow.

    /** Subtle row selector: faint theme tint on focus/press, transparent otherwise. */
    public static android.graphics.drawable.StateListDrawable rowSelector(Context c) {
        android.graphics.drawable.StateListDrawable d =
                new android.graphics.drawable.StateListDrawable();
        int hl = (TEAL & 0x00FFFFFF) | 0x26000000; // ~15% theme tint
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(hl);
        d.addState(new int[]{android.R.attr.state_focused}, focused);
        d.addState(new int[]{android.R.attr.state_pressed}, focused);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0x00000000);
        d.addState(new int[]{}, normal);
        return d;
    }

    /** 1dp hairline divider between plain text rows. */
    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor((MUTED & 0x00FFFFFF) | 0x40000000);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    /** Plain text row: [icon] title ......... count. Focusable, subtle highlight. */
    public static LinearLayout textRow(Context c, String icon, String title,
                                       String count) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(rowSelector(c));
        int hp = dp(c, 14);
        row.setPadding(hp, dp(c, 13), hp, dp(c, 13));
        row.setFocusable(true);

        if (icon != null && !icon.isEmpty()) {
            TextView ic = label(c, icon, 20, TEAL, false);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            ilp.setMargins(0, 0, dp(c, 12), 0);
            ic.setLayoutParams(ilp);
            row.addView(ic);
        }
        TextView t = label(c, title, 16, INK, false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        t.setLayoutParams(tlp);
        row.addView(t);
        if (count != null && !count.isEmpty()) {
            TextView n = label(c, count, 13, MUTED, false);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.setMargins(dp(c, 8), 0, 0, 0);
            n.setLayoutParams(nlp);
            row.addView(n);
        }
        TextView chev = label(c, "›", 20, MUTED, false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dp(c, 6), 0, 0, 0);
        chev.setLayoutParams(clp);
        row.addView(chev);
        return row;
    }
}
