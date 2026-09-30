package com.zynelabs.iptv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.zynelabs.iptv.data.Channel;
import com.zynelabs.iptv.data.PlAccount;
import com.zynelabs.iptv.data.XtreamClient;

import java.util.ArrayList;
import java.util.List;

/** Episode picker dialog for a series channel. Shared by the home screens. */
public class SeriesDialog {

    public static void show(final Activity act, final PlAccount acc, final Channel s) {
        final AlertDialog dlg = new AlertDialog.Builder(act)
                .setTitle(s.name)
                .setMessage("Loading episodes…")
                .create();
        dlg.show();
        new Thread(new Runnable() {
            @Override public void run() {
                final List<XtreamClient.Episode> eps = XtreamClient.episodes(s);
                act.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        dlg.dismiss();
                        if (eps.isEmpty()) {
                            Toast.makeText(act, "No episodes found", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        List<String> labels = new ArrayList<>();
                        for (XtreamClient.Episode e : eps) labels.add(e.label);
                        ListView lv = new ListView(act);
                        lv.setAdapter(new ArrayAdapter<>(act,
                                android.R.layout.simple_list_item_1, labels));
                        new AlertDialog.Builder(act)
                                .setTitle(s.name)
                                .setView(lv)
                                .show();
                        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                                XtreamClient.Episode ep = eps.get(pos);
                                Channel one = new Channel();
                                one.kind = Channel.VOD;
                                one.key = s.key + "_" + pos;
                                one.name = s.name + " — " + ep.label;
                                one.logo = s.logo;
                                one.url = ep.url;
                                List<Channel> single = new ArrayList<>();
                                single.add(one);
                                PlayerQueue.setAccount(acc.id);
                                PlayerQueue.set(single, 0);
                                act.startActivity(new Intent(act, PlayerActivity.class));
                            }
                        });
                    }
                });
            }
        }).start();
    }
}
