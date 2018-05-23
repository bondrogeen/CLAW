package ru.codedevice.claw;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.Log;

public class AppReceiver extends BroadcastReceiver {

    String TAG = "AppReceiver";
    Context context;
    Intent i;
    SharedPreferences settings;
    Boolean general_startBoot;
    Boolean general_startNet;


    public void onReceive(Context context, Intent intent) {


        settings = PreferenceManager.getDefaultSharedPreferences(context);
        general_startBoot = settings.getBoolean("general_startBoot", false);
        general_startNet = settings.getBoolean("general_startNet", false);

        String action = intent.getAction();
        Log.i(TAG, "Action : " + action);
        i = new Intent(context, AppMqttService.class);
        if (action.equals("android.net.conn.CONNECTIVITY_CHANGE") && general_startNet){
            if (checkInternet(context)){
                Log.i(TAG, "yes internet");
                context.startService(i);
            }else{
                Log.i(TAG, "no internet");
                context.stopService(i);
            }
        }

        if (general_startBoot && action.equals("android.intent.action.BOOT_COMPLETED")
                || action.equals("android.intent.action.QUICKBOOT_POWERON")
                || action.equals("com.htc.intent.action.QUICKBOOT_POWERON") ){
                context.startService(i);
        }

        if (action.equals("android.intent.action.SCREEN_ON")
                ||action.equals("android.intent.action.SCREEN_OFF")){
            i.putExtra("status","screen");
            context.startService(i);
        }

        if (action.equals("ActionReceiverWidget")) {
            String id = "null";
            String name = "null";

            try {
                id = intent.getStringExtra("id");
                name = intent.getStringExtra("name");
            } catch (NullPointerException e) {
                Log.e(TAG, "msg = null");
            }
            Log.i(TAG, "id = "+id);
            Log.i(TAG, "name = "+name);

        }

    }

    public  boolean checkInternet(Context context) {
        final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }
}