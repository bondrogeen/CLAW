package ru.codedevice.claw;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ServiceBoot extends BroadcastReceiver {

    String TAG = "CLAW";

    public void onReceive(Context context, Intent intent) {


        try {
            Thread.sleep(10000L);
            } catch (InterruptedException var3) {

            }

//            Intent i = new Intent(context, MainActivity.class);
//            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            context.startActivity(i);
            Log.i(TAG, "Autostart CLAW");
//            context.startService(new Intent(context, MqttService.class));
        }

}