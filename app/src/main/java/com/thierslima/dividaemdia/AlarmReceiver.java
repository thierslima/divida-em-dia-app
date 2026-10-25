package com.thierslima.dividaemdia;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import java.text.NumberFormat;
import java.util.Locale;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationHelper.ensureChannels(context);
        String name=intent.getStringExtra("name"); if(name==null)name="Conta";
        double amount=intent.getDoubleExtra("amount",0);int days=intent.getIntExtra("days_before",0);boolean sound=intent.getBooleanExtra("sound",true);
        String when=days==0?"vence hoje":days==1?"vence amanhã":"vence em "+days+" dias";
        String value=NumberFormat.getCurrencyInstance(new Locale("pt","BR")).format(amount);
        Intent open=new Intent(context,MainActivity.class);open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String channel=sound?NotificationHelper.CHANNEL_SOUND:NotificationHelper.CHANNEL_SILENT;
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,channel):new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Dívida em Dia").setContentText(name+" "+when+" • "+value).setStyle(new Notification.BigTextStyle().bigText("Sua conta "+name+" "+when+". Valor: "+value)).setContentIntent(content).setAutoCancel(true).setColor(Color.parseColor("#08A86B"));
        if(Build.VERSION.SDK_INT<26){if(sound)b.setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_VIBRATE);else b.setDefaults(0);b.setPriority(Notification.PRIORITY_HIGH);}
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.notify((name+intent.getStringExtra("due")+days).hashCode()&0x7fffffff,b.build());
    }
}
