package ru.codedevice.claw;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.widget.Toast;

public class AppReceiver extends BroadcastReceiver {

    String TAG = "AppReceiver";
    Context context;
    Intent i;
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "Action : " + action);
        i = new Intent(context, MqttService.class);
        if (action.equals("android.net.conn.CONNECTIVITY_CHANGE")){
            if (checkInternet(context)){
                Log.i(TAG, "yes internet");
                i.putExtra("status","autoStart");
                context.startService(i);
            }else{
                Log.i(TAG, "no internet");
                context.stopService(i);
            }
        }

        if (action.equals("android.intent.action.BOOT_COMPLETED")
                || action.equals("android.intent.action.QUICKBOOT_POWERON")
                || action.equals("com.htc.intent.action.QUICKBOOT_POWERON") ){
                context.startService(i);
        }

        if (action.equals("android.intent.action.SCREEN_ON")
                ||action.equals("android.intent.action.SCREEN_OFF")){
            i.putExtra("status","screen");
            context.startService(i);
        }
    }

    public  boolean checkInternet(Context context) {
        final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }
}