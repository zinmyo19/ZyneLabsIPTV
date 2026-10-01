package com.zynelabs.iptv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.zynelabs.iptv.data.PlAccount;
import com.zynelabs.iptv.data.Store;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;

/**
 * OTT-style setup: shows a QR code + 6-digit code. The user scans it with
 * their phone, fills the playlist/MAC on the web page, hits Save — this
 * activity polls the sync worker and imports the account automatically.
 */
public class WebSetupActivity extends Activity {
    private static final String BASE =
            "https://zyne-iptv-sync.zynelabs.workers.dev";
    private static final long TIMEOUT_MS = 5 * 60 * 1000L;

    private Store store;
    private String code;
    private volatile boolean polling = true;
    private volatile boolean done = false;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.applyTheme(this);
        store = new Store(this);
        code = String.format("%06d", new Random().nextInt(1000000));
        final String link = BASE + "/add?code=" + code;

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 24);
        root.setPadding(p, p, p, p);

        TextView title = Ui.label(this, "Add playlist from your phone", 20, Ui.INK, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        root.addView(Ui.spacer(this, 12));

        ImageView qr = new ImageView(this);
        qr.setImageBitmap(qrBitmap(link, 560));
        int qs = Ui.dp(this, 230);
        root.addView(qr, new LinearLayout.LayoutParams(qs, qs));
        root.addView(Ui.spacer(this, 12));

        TextView codeV = new TextView(this);
        codeV.setText(code.substring(0, 3) + " " + code.substring(3));
        codeV.setTextSize(40);
        codeV.setTextColor(0xFF00E5FF);
        codeV.setGravity(Gravity.CENTER);
        root.addView(codeV);
        root.addView(Ui.spacer(this, 8));

        TextView how = Ui.label(this,
                "1. Scan the QR with your phone camera\n" +
                "2. Enter the code above on the page\n" +
                "3. Fill in your playlist / Xtream / MAC and Save\n" +
                "The playlist appears here automatically.",
                15, Ui.MUTED, false);
        how.setGravity(Gravity.CENTER);
        root.addView(how);
        root.addView(Ui.spacer(this, 12));

        status = Ui.label(this, "Waiting for your phone…", 14, Ui.TEAL, true);
        status.setGravity(Gravity.CENTER);
        root.addView(status);
        root.addView(Ui.spacer(this, 16));

        android.widget.Button again = Ui.flatBtn(this, "New code", 15);
        again.setTextColor(Ui.TEAL);
        again.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                polling = false;
                done = true;
                recreate();
            }
        });
        root.addView(again, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        sv.addView(root);
        setContentView(sv);
        Ui.enableTvFocus(root);
        poll();
    }

    private void poll() {
        final long start = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override public void run() {
                while (polling && !done
                        && System.currentTimeMillis() - start < TIMEOUT_MS) {
                    try {
                        String body = get(BASE + "/api/fetch?code=" + code);
                        if (body != null) {
                            onPayload(new JSONObject(body));
                            return;
                        }
                    } catch (Exception ignored) {}
                    try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
                }
                if (!done) runOnUiThread(new Runnable() {
                    @Override public void run() {
                        status.setText("Code expired — tap New code and try again.");
                    }
                });
            }
        }).start();
    }

    private void onPayload(final JSONObject o) {
        done = true;
        polling = false;
        // single-use: delete the code
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    URL u = new URL(BASE + "/api/consume");
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setConnectTimeout(8000);
                    byte[] b = ("{\"code\":\"" + code + "\"}").getBytes("UTF-8");
                    c.getOutputStream().write(b);
                    c.getResponseCode();
                    c.disconnect();
                } catch (Exception ignored) {}
            }
        }).start();

        final PlAccount a = new PlAccount();
        a.name = o.optString("name", "Playlist");
        if (a.name.isEmpty()) a.name = "Playlist";
        String type = o.optString("type", "m3u");
        String url = o.optString("url", "").trim();
        if ("xtream".equals(type)) {
            a.type = "xtream";
            a.server = url;
            a.user = o.optString("username", "");
            a.pass = o.optString("password", "");
        } else if ("stalker".equals(type)) {
            String mac = o.optString("mac", "").trim().toUpperCase();
            if (!com.zynelabs.iptv.data.StalkerClient.validMac(mac)) {
                fail("MAC looks wrong — try again");
                return;
            }
            a.type = "stalker";
            a.url = com.zynelabs.iptv.data.StalkerClient.normPortal(url);
            a.mac = mac;
        } else {
            a.type = "m3u_url";
            a.url = url;
        }
        // Xtream logins store the link in a.server, not a.url
        String link = "xtream".equals(a.type) ? a.server : a.url;
        if (link == null || link.isEmpty()) {
            fail("Empty link — try again");
            return;
        }
        runOnUiThread(new Runnable() {
            @Override public void run() {
                final EditText input = Ui.field(WebSetupActivity.this, "Playlist name");
                input.setText(a.name);
                input.setSelection(input.getText().length());
                new AlertDialog.Builder(WebSetupActivity.this)
                        .setTitle("Name this playlist")
                        .setView(input)
                        .setCancelable(false)
                        .setPositiveButton("Save",
                                new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                String typed = input.getText().toString().trim();
                                if (!typed.isEmpty()) a.name = typed;
                                addAndFinish(a);
                            }
                        })
                        .setNegativeButton("Cancel",
                                new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                addAndFinish(a);
                            }
                        })
                        .show();
            }
        });
    }

    private void addAndFinish(PlAccount a) {
        store.addAccount(a);
        Toast.makeText(WebSetupActivity.this,
                "Added: " + a.name, Toast.LENGTH_LONG).show();
        setResult(RESULT_OK);
        finish();
    }

    private void fail(final String msg) {
        done = true;
        runOnUiThread(new Runnable() {
            @Override public void run() {
                status.setText(msg);
                Toast.makeText(WebSetupActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String get(String urlStr) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(urlStr);
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) return null;
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private Bitmap qrBitmap(String text, int size) {
        try {
            BitMatrix m = new QRCodeWriter().encode(
                    text, BarcodeFormat.QR_CODE, size, size);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    bmp.setPixel(x, y, m.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            return bmp;
        } catch (Exception e) {
            return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        }
    }

    @Override
    protected void onDestroy() {
        polling = false;
        done = true;
        super.onDestroy();
    }
}
