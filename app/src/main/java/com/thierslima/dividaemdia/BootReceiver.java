package com.thierslima.dividaemdia;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String json=context.getSharedPreferences("divida_em_dia",Context.MODE_PRIVATE).getString("reminders_json",null);
        if(json!=null)ReminderScheduler.scheduleAll(context,json);
    }
}
