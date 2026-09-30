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
 *  Warm charcoal + amber whiskey + hologram cyan. (Constant names kept for compat.) */
public class Ui {
    public static final int PAPER = 0xFF0C0906;
    public static final int CARD = 0xFF17100A;
    public static final int CARD2 = 0xFF20150C;
    public static final int TEAL = 0xFFFFAA33;
    public static final int TEAL_DARK = 0xFF7A4A1E;
    public static final int GOLD = 0xFF00E5FF;
    public static final int RED = 0xFFFF5A4E;
    public static final int INK = 0xFFF5EDE0;
    public static final int MUTED = 0xFFA89A86;

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
}
