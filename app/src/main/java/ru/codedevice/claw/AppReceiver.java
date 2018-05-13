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

        if (action.equals("android.net.conn.CONNECTIVITY_CHANGE")){
            i = new Intent(context, MqttService.class);
            if (checkInternet(context)){
                Log.i(TAG, "yes internet");
                i.putExtra("status","autoStart");
                context.startService(i);
            }else{
                Log.i(TAG, "no internet");
                context.stopService(i);
            }
        }

//        try {
//            Thread.sleep(10000L);
//            } catch (InterruptedException var3) {
//
//            }
//
////            Intent i = new Intent(context, MainActivity.class);
////            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
////            context.startActivity(i);
//            Log.i(TAG, "Autostart CLAW");
////            context.startService(new Intent(context, MqttService.class));
        }

    public  boolean checkInternet(Context context) {
        final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }
}