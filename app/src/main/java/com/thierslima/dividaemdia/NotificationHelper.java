package com.thierslima.dividaemdia;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

public final class NotificationHelper {
    public static final String CHANNEL_SOUND = "divida_em_dia_lembretes";
    public static final String CHANNEL_SILENT = "divida_em_dia_silencioso";
    private NotificationHelper() {}

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel sound = new NotificationChannel(CHANNEL_SOUND, "Lembretes de vencimento", NotificationManager.IMPORTANCE_HIGH);
        sound.setDescription("Avisos de contas e dívidas próximas do vencimento");
        sound.enableVibration(true);
        Uri uri = Settings.System.DEFAULT_NOTIFICATION_URI;
        sound.setSound(uri, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
        NotificationChannel silent = new NotificationChannel(CHANNEL_SILENT, "Lembretes silenciosos", NotificationManager.IMPORTANCE_DEFAULT);
        silent.setDescription("Avisos sem som do Dívida em Dia");
        silent.setSound(null, null);
        silent.enableVibration(false);
        nm.createNotificationChannel(sound); nm.createNotificationChannel(silent);
    }
}
