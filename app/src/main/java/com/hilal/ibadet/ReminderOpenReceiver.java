package com.hilal.ibadet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Bildirim tıklamasını önce sese müdahale ederek, sonra güvenli biçimde MainActivity'ye aktarır. */
public class ReminderOpenReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        ReminderReceiver.stopActiveSound();
        String id = intent == null ? "" : intent.getStringExtra("hilalReminderId");
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        open.putExtra("hilalReminderId", id == null ? "" : id);
        open.setData(Uri.parse("hilal://reminder/" + Uri.encode(id == null ? "" : id)));
        try { context.startActivity(open); } catch (Exception ignored) {}
    }
}
