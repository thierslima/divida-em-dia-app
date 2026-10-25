package com.thierslima.dividaemdia;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

public final class NotificationHelper {
    private static Ringtone previewRingtone;
    private NotificationHelper() {}

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelFor(context, "exclusive", "", true);
            channelFor(context, "silent", "", false);
        }
    }

    public static Uri resolveSoundUri(Context context, String mode, String uriString) {
        if (mode == null || mode.isEmpty()) mode = "exclusive";
        switch (mode) {
            case "silent": return null;
            case "soft": return resourceUri(context, R.raw.alerta_suave);
            case "clear": return resourceUri(context, R.raw.aviso_claro);
            case "double": return resourceUri(context, R.raw.toque_duplo);
            case "system": return Settings.System.DEFAULT_NOTIFICATION_URI;
            case "uri":
                try { return (uriString == null || uriString.isEmpty()) ? Settings.System.DEFAULT_NOTIFICATION_URI : Uri.parse(uriString); }
                catch (Exception ignored) { return Settings.System.DEFAULT_NOTIFICATION_URI; }
            case "exclusive":
            default: return resourceUri(context, R.raw.divida_em_dia);
        }
    }

    private static Uri resourceUri(Context context, int resId) {
        return Uri.parse("android.resource://" + context.getPackageName() + "/" + resId);
    }

    public static String channelFor(Context context, String mode, String uriString, boolean vibration) {
        if (mode == null || mode.isEmpty()) mode = "exclusive";
        String unique = mode + "|" + (uriString == null ? "" : uriString) + "|" + vibration;
        String id = "ded_v12_" + mode.replaceAll("[^a-zA-Z0-9_]", "_") + "_" + Integer.toHexString(unique.hashCode());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return id;

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return id;
        Uri soundUri = resolveSoundUri(context, mode, uriString);
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel = new NotificationChannel(id, "Lembretes • " + labelFor(mode), importance);
        channel.setDescription("Lembretes de contas e dívidas do Dívida em Dia");
        channel.enableVibration(vibration);
        if (vibration) channel.setVibrationPattern(new long[]{0, 140, 90, 180});
        if (soundUri == null) channel.setSound(null, null);
        else channel.setSound(soundUri, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        nm.createNotificationChannel(channel);
        return id;
    }

    private static String labelFor(String mode) {
        switch (mode) {
            case "soft": return "Alerta suave";
            case "clear": return "Aviso claro";
            case "double": return "Toque duplo";
            case "system": return "Som padrão do celular";
            case "uri": return "Som personalizado";
            case "silent": return "Sem som";
            default: return "Som Dívida em Dia";
        }
    }

    public static void previewSound(Context context, String mode, String uriString) {
        try {
            if (previewRingtone != null && previewRingtone.isPlaying()) previewRingtone.stop();
            Uri uri = resolveSoundUri(context, mode, uriString);
            if (uri == null) return;
            previewRingtone = RingtoneManager.getRingtone(context, uri);
            if (previewRingtone != null) previewRingtone.play();
        } catch (Exception ignored) {}
    }

    public static void showTestNotification(Context context, String mode, String uriString, boolean vibration) {
        String channelId = channelFor(context, mode, uriString, vibration);
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 9021, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, channelId) : new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Teste • Dívida em Dia")
                .setContentText("Seu som de lembrete está funcionando.")
                .setStyle(new Notification.BigTextStyle().bigText("Seu som de lembrete está funcionando. Quando uma dívida estiver próxima do vencimento, o nome, valor e data aparecerão aqui."))
                .setContentIntent(content).setAutoCancel(true).setColor(Color.parseColor("#08A86B"));
        if (Build.VERSION.SDK_INT < 26) {
            Uri sound = resolveSoundUri(context, mode, uriString);
            if (sound != null) b.setSound(sound);
            if (vibration) b.setVibrate(new long[]{0,140,90,180});
            b.setPriority(Notification.PRIORITY_HIGH);
        }
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(9021, b.build());
    }
}
