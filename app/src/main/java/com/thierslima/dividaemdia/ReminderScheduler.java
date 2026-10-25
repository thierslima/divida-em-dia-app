package com.thierslima.dividaemdia;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ReminderScheduler {
    private ReminderScheduler() {}

    public static void scheduleAll(Context context, String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject settings = root.optJSONObject("settings");
            JSONArray reminders = settings != null ? settings.optJSONArray("reminders") : null;
            JSONArray debts = root.optJSONArray("debts");
            if (reminders == null || debts == null) return;

            String globalMode = settings != null ? settings.optString("soundMode", settings.optBoolean("sound", true) ? "exclusive" : "silent") : "exclusive";
            String globalUri = settings != null ? settings.optString("soundUri", "") : "";
            boolean vibration = settings == null || settings.optBoolean("vibration", true);

            Set<Integer> reminderDays = new HashSet<>();
            for (int i = 0; i < reminders.length(); i++) reminderDays.add(reminders.optInt(i));

            for (int i = 0; i < debts.length(); i++) {
                JSONObject debt = debts.optJSONObject(i); if (debt == null) continue;
                if ("paid".equals(debt.optString("status"))) continue;
                String id = debt.optString("id");
                String name = debt.optString("name");
                String due = debt.optString("due");
                double amount = debt.optDouble("amount", 0);

                String debtMode = debt.optString("soundMode", "inherit");
                String mode = "inherit".equals(debtMode) || debtMode.isEmpty() ? globalMode : debtMode;
                String uri = "inherit".equals(debtMode) || debtMode.isEmpty() ? globalUri : debt.optString("soundUri", "");

                for (Integer days : reminderDays) scheduleOne(context, id, name, amount, due, days, mode, uri, vibration);
            }
        } catch (Exception ignored) {}
    }

    public static void cancelAll(Context context, String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject settings = root.optJSONObject("settings");
            JSONArray reminders = settings != null ? settings.optJSONArray("reminders") : null;
            JSONArray debts = root.optJSONArray("debts");
            if (reminders == null || debts == null) return;
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            for (int i = 0; i < debts.length(); i++) {
                JSONObject debt = debts.optJSONObject(i); if (debt == null) continue;
                String id = debt.optString("id");
                for (int j = 0; j < reminders.length(); j++) {
                    int days = reminders.optInt(j);
                    Intent intent = new Intent(context, AlarmReceiver.class);
                    int requestCode = (id + "_" + days).hashCode() & 0x7fffffff;
                    PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                    if (pi != null) { am.cancel(pi); pi.cancel(); }
                }
            }
        } catch (Exception ignored) {}
    }

    private static void scheduleOne(Context context, String id, String name, double amount, String dueIso, int daysBefore, String soundMode, String soundUri, boolean vibration) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date dueDate = f.parse(dueIso); if (dueDate == null) return;
            Calendar c = Calendar.getInstance();
            c.setTime(dueDate); c.add(Calendar.DAY_OF_MONTH, -daysBefore);
            c.set(Calendar.HOUR_OF_DAY, 9); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
            long trigger = c.getTimeInMillis(); if (trigger <= System.currentTimeMillis()) return;

            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.putExtra("debt_id", id); intent.putExtra("name", name); intent.putExtra("amount", amount);
            intent.putExtra("due", dueIso); intent.putExtra("days_before", daysBefore);
            intent.putExtra("sound_mode", soundMode); intent.putExtra("sound_uri", soundUri); intent.putExtra("vibration", vibration);

            int requestCode = (id + "_" + daysBefore).hashCode() & 0x7fffffff;
            PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            if (android.os.Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            else am.set(AlarmManager.RTC_WAKEUP, trigger, pi);
        } catch (Exception ignored) {}
    }
}
