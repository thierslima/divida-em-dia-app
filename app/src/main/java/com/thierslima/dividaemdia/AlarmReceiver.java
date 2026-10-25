package com.thierslima.dividaemdia;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String name = intent.getStringExtra("name"); if (name == null || name.isEmpty()) name = "Conta";
        double amount = intent.getDoubleExtra("amount", 0);
        int days = intent.getIntExtra("days_before", 0);
        String due = intent.getStringExtra("due");
        String mode = intent.getStringExtra("sound_mode"); if (mode == null) mode = "exclusive";
        String soundUri = intent.getStringExtra("sound_uri"); if (soundUri == null) soundUri = "";
        boolean vibration = intent.getBooleanExtra("vibration", true);

        String value = NumberFormat.getCurrencyInstance(new Locale("pt", "BR")).format(amount);
        String title = days == 0 ? "Vence hoje: " + name : days == 1 ? "Conta vencendo amanhã" : "Conta vencendo em " + days + " dias";
        String dueText = formatDate(due);
        String body = name + " • " + value + (dueText.isEmpty() ? "" : " • Vence em " + dueText);

        String channel = NotificationHelper.channelFor(context, mode, soundUri, vibration);
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, (name + due).hashCode() & 0x7fffffff, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, channel) : new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body + "\nToque para abrir o Dívida em Dia."))
                .setContentIntent(content)
                .setAutoCancel(true)
                .setColor(Color.parseColor("#08A86B"));

        if (Build.VERSION.SDK_INT < 26) {
            Uri uri = NotificationHelper.resolveSoundUri(context, mode, soundUri);
            if (uri != null) b.setSound(uri);
            if (vibration) b.setVibrate(new long[]{0,140,90,180});
            b.setPriority(Notification.PRIORITY_HIGH);
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify((name + due + days).hashCode() & 0x7fffffff, b.build());
    }

    private String formatDate(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            return d == null ? "" : new SimpleDateFormat("dd/MM/yyyy", new Locale("pt", "BR")).format(d);
        } catch (Exception e) { return ""; }
    }
}
