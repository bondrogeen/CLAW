package ru.codedevice.claw;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.MqttPingSender;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.internal.ClientComms;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class AppMqttService extends Service implements MqttCallback {

    final String TAG = "AppMqttService";

    MqttAndroidClient MQTTclient;
    MqttConnectOptions options;
    SharedPreferences settings;
    TextToSpeech tts;
    Toast toast;
    BroadcastReceiver br;
    PowerManager.WakeLock wl = null;
    Context context;
    Timer myTimer;
    ReTimerTask myReTimerTask;

    String clientId;
    String mqtt_server;
    String mqtt_device;
    String mqtt_port;
    String serverUri;
    String mqtt_username;
    String mqtt_password;
    Boolean run;
    Boolean general_startBoot;
//    Boolean mqttRun;
    Boolean tts_OK;
//    Boolean tts_TIME;
    Boolean connectionLost = false;
    Integer timeReLost = 60000;
    String topic = "comm/*";

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void onCreate() {
        super.onCreate();
        initSettings();
        initTTS();
        initMQTT();
        initBroadReceiver();
        Log.d(TAG, "onCreate");
    }

    public void initBroadReceiver() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        br = new AppReceiver();
        registerReceiver(br, filter);
    }
    public void initSettings() {

        settings = PreferenceManager.getDefaultSharedPreferences(this);
        clientId = "CLAW";
        mqtt_server = settings.getString("mqtt_server", "");
        mqtt_port = settings.getString("mqtt_port", "");
        serverUri = "tcp://" + mqtt_server + ":" + mqtt_port;
        mqtt_username = settings.getString("mqtt_login", "");
        mqtt_password = settings.getString("mqtt_pass", "");
        mqtt_device = settings.getString("mqtt_device", Build.MODEL.replaceAll("\\s+",""));
        run = settings.getBoolean("mqtt_run", false);
        general_startBoot = settings.getBoolean("general_startBoot", false);
        if (mqtt_device==null || mqtt_device.equals("")) {
            mqtt_device = Build.MODEL.replaceAll("\\s+","");
        }
    }
    public void initMQTT() {
        Log.i(TAG, "Start initMQTT");

        MQTTclient = new MqttAndroidClient(this.getApplicationContext(), serverUri, clientId);
        options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);

        Log.i(TAG, "mqtt_username!=null " + String.valueOf(mqtt_username!=null));
        Log.i(TAG, "!mqtt_username.equals('') " +String.valueOf( !mqtt_username.equals("")));
        if (mqtt_username!=null && !mqtt_username.equals("")){
            options.setUserName(mqtt_username);
        }
        if (mqtt_password!=null && !mqtt_password.equals("")){
            options.setPassword(mqtt_password.toCharArray());
        }

        MQTTclient.setCallback(this);
    }

    private void initTTS() {
        tts_OK = false;
        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(Locale.getDefault());
                    if (result == TextToSpeech.LANG_MISSING_DATA
                            || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "This Language is not supported");
                    } else {
                        tts_OK = true;
                        Log.i("TTS", "This Language is supported");
                    }
                } else {
                    Log.e("TTS", "Initilization Failed!");
                }
            }
        });
    }

    public boolean checkInternet() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        assert cm != null;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "startId :" + startId);
        Log.d(TAG, "flags :" + flags);
        Boolean net = checkInternet();

        if (intent != null && intent.getExtras() != null) {
            String status = intent.getStringExtra("status");
            Log.d(TAG, "status :" + status);

            switch (status) {
                case "screen":
                    publish("info/display/status", getDisplay());
                    break;
                case "publish":
                    String topic = intent.getStringExtra("topic");
                    String value = intent.getStringExtra("value");
                    publish(topic, value);
                    break;
                case "widget":
                    String textName = intent.getStringExtra("textName");
                    String textType = intent.getStringExtra("textType");
                    if(textType.equals(ConfigWidget.WIDGET_TYPE_TEXT_AND_TITLE)){
                        publish("comm/widget/"+textName, "");
                    }
                    if(textType.equals(ConfigWidget.WIDGET_TYPE_BUTTON)){
//                        publish("info/widget/"+textName, "");
                    }
                    break;
            }
        } else {
            Log.i(TAG, "isConnected() "+MQTTclient.isConnected());
            if (!MQTTclient.isConnected() && net) {
                connect();
                Log.d(TAG, "Connect MQTT");
            }else{
                if(!net){
                    toast = Toast.makeText(getApplicationContext(),
                            "No network connection", Toast.LENGTH_SHORT);
                    toast.show();
                    sendBrodecast("noNetwork");
                    stopSelf();
                }

            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        disconnect();
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        if (myTimer!=null) {
            myTimer.cancel();
            myTimer = null;
        }
        unregisterReceiver(br);
    }

    @Override
    public void connectionLost(Throwable throwable) {

        connectionLost = true;
        Log.d(TAG, "connectionLost");
//        myTimer = new Timer();
//        myReTimerTask = new ReTimerTask();
//        myTimer.schedule(myReTimerTask, 10000, timeReLost);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        Log.d(TAG, "topic: "+topic + " value: "+message);
        Log.d(TAG, message.toString());
        if (message.toString().equals("")){return;}

        if (topic.equals(clientId + "/" + mqtt_device +"/comm/tts/request")){
            speakOut(message.toString());
        }
        if (topic.equals(clientId + "/" + mqtt_device +"/comm/tts/command")){
//            String mes = message.toString().toLowerCase();
//            if(mes.equals("stop")){//
//            }
        }
        if (topic.equals(clientId + "/" + mqtt_device +"/comm/display/level")){
            if(isNumber(message.toString())){
                int num = Integer.parseInt(message.toString());
                setBrightness(num);
            }
        }
        if (topic.equals(clientId + "/" + mqtt_device +"/comm/display/mode")){
            int num = isTrue(message.toString());
            if(num==1 || num==2){
                setBrightnessMode(num);
            }
        }
        if (topic.equals(clientId + "/" + mqtt_device +"/comm/display/timeOff")){
            if(isNumber(message.toString())){
                int num = Integer.parseInt(message.toString());
                setTimeOff(num);
            }
        }
        if (topic.equals(clientId + "/" + mqtt_device +"/comm/display/toWake")){
            int num = isTrue(message.toString());
            if(num==1 || num==2){
                setDisplay(num);
            }
        }
        if (topic.equals(clientId + "/" + mqtt_device +"/comm/notification/simple")){
            notification(message.toString());
        }
        if (topic.equals(clientId + "/" + mqtt_device +"/comm/notification/alert")){
            alert(message.toString());
        }
        if (topic.equals(clientId + "/" + mqtt_device +"/comm/widget/one")){
            updateInfoWidget();
            Log.d(TAG, "widget");
        }

    }

    private int isTrue(String message){
        String mes = message.toLowerCase();
        int res = 0;
        if(mes.equals("true")
                ||mes.equals("1")
                ||mes.equals("auto")
                ||mes.equals("on")
                ){
            res = 1;
        }else if (mes.equals("false")
                ||mes.equals("0")
                ||mes.equals("manual")
                ||mes.equals("off")
                ){
            res = 2;
        }
        return res;
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
    }

    public void publish(String topic, String payload) {
        if(MQTTclient.isConnected()) {
            byte[] encodedPayload = new byte[0];
            try {
                encodedPayload = payload.getBytes("UTF-8");
                MqttMessage message = new MqttMessage(encodedPayload);
                MQTTclient.publish(clientId + "/" + mqtt_device + "/" + topic, message);
            } catch (UnsupportedEncodingException | MqttException e) {
                e.printStackTrace();
            }
        }
    }

    public void connect() {
        try {
            IMqttToken token = MQTTclient.connect(options);
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Connection");
//                    notification( "Title ; Text");
                    pubOne();
                    toast = Toast.makeText(getApplicationContext(),
                            "Connection", Toast.LENGTH_SHORT);
                    toast.show();
                    sendBrodecast("Connection");
                    setSubscribe();
                    Log.d(TAG, "Connection end");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    if (!connectionLost){
                        Log.d(TAG, "Connection Failure");
                        toast = Toast.makeText(getApplicationContext(),
                                "Connection Failure", Toast.LENGTH_SHORT);
                        toast.show();
                        sendBrodecast("ConnectionFailure");
                        stopSelf();
                    }
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        Log.d(TAG, "Disconnect start");
        Log.d(TAG, "Disconnect isConnected "+MQTTclient.isConnected());
        if (MQTTclient != null) {
//            if (MQTTclient.isConnected()) {
//                MQTTclient.close();
//            }
//            options.setAutomaticReconnect(false);

            try {
                MQTTclient.disconnect();
            } catch (MqttException e) {
                e.printStackTrace();
            }
//            MQTTclient.unregisterResources();
//            MQTTclient.close();
            MQTTclient = null;
//            stopService(new Intent(context, MqttService.class));
            Log.d(TAG, "Disconnect success");
            sendBrodecast("disconnect");
        }
    }

    private void updateInfoWidget(){
        Intent intent = new Intent(this, AppWidgetOne.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        int[] ids = {1};
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent);
    }

    private void pubOne() {
        publish("info/general/BRAND", Build.BRAND);
        publish("info/general/MODEL", Build.MODEL);
        publish("info/general/PRODUCT", Build.PRODUCT);
        publish("info/display/level", String.valueOf(getBrightness()));
        publish("info/display/mode", getBrightnessMode());
        publish("info/display/status", getDisplay());
        publish("info/display/timeOff", String.valueOf(getTimeOff()));
        publish("info/display/sleep", "true");
        publish("info/tts/talk", "false");
        publish("comm/tts/request", "");
        publish("comm/tts/command", "");
        publish("comm/display/level", "");
        publish("comm/display/mode", "");
        publish("comm/display/toWake", "");
        publish("comm/display/timeOff", "");
        publish("comm/notification/simple", "");
        publish("comm/notification/alert", "");
        publish("comm/widget/one", "");
    }

    private void setSubscribe() {
        int qos = 1;
        try {
            IMqttToken subToken = MQTTclient.subscribe(clientId + "/" + mqtt_device +"/"+topic, qos);
            subToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // The message was published
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken,
                                      Throwable exception) {
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void speakOut(String text) {
        if (tts_OK){
            setTtsUtteranceProgressListener();
            HashMap<String, String> map = new HashMap<>();
            map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,"messageID");
            tts.speak(text, TextToSpeech.QUEUE_ADD, map);
        }
    }

    private void setTtsUtteranceProgressListener() {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                publish("info/tts/talk", "true");
                Log.i(TAG, "TTS onStart");
            }

            @Override
            public void onDone(String utteranceId) {
                publish("info/tts/talk", "false");
                Log.i(TAG, "TTS onDone");
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS onError");
            }
        });
    }

    private void sendBrodecast(String text){
        Intent intent = new Intent(MainActivity.BROADCAST_ACTION);
        intent.putExtra(MainActivity.PARAM_STATUS,text);
        sendBroadcast(intent);

    }

    private static boolean isNumber(String str) {
        return str.matches("[-+]?[\\d]+([.][\\d]+)?");
    }

    private int getBrightness(){
        int brightness = 0;
        try {
            brightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception ignored) {

        }
        return brightness;
    }
    private int getTimeOff(){
        int timeOff = 0;
        try {
            timeOff = Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_OFF_TIMEOUT);
            timeOff = timeOff/1000;
        } catch (Exception ignored) {

        }
        return timeOff;
    }
    private void setTimeOff(int time){
        publish("info/display/timeOff", String.valueOf(time));
        if(time!=-1){
            time = time*1000;
        }
        try {
            Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_OFF_TIMEOUT,time);
        } catch (Exception ignored) {

        }
    }

    private String getBrightnessMode(){
        String mode = "";
        try {
            if (Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE) == 1){
                mode="auto";
            }else {
                mode = "manual";
            }
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        return mode;
    }

    private void setBrightnessMode(int value){
        if (value == 1){
            Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        }else if (value == 2){
            Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        }
        publish("info/display/mode", getBrightnessMode());
    }

    private void setBrightness(int value){
        if(value<4){value=4;}
        if(value>100){value=100;}
        if (value <=100 && value >=4){
            int num = (int) Math.round(value*2.55);
            Log.i("Brightness", String.valueOf(num));
            Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,num );
            publish("info/display/level", String.valueOf(value));
            publish("info/display/mode", "manual");
        }
    }

    private String getDisplay(){
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        String display;
        assert pm != null;
        if(pm.isScreenOn()){
            display="true";
        }else{
            display="false";
        }
        return display;
    }
    private void setDisplay(int val){
        Log.i("setDisplay : ", String.valueOf(val));
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (val == 1 && wl == null&& pm != null) {
            wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    TAG);
            wl.acquire();
            publish("info/display/sleep", "false");
        }else if (val == 2 && wl != null){
            wl.release();
            wl=null;
            publish("info/display/sleep", "true");
        }

    }

    private void notification(String str){
        Log.i("notification : ", "String  : "+str);
        String[] subStr;
        subStr = str.split(";");
        String text = "";
        String title = "";
        if (subStr.length >= 2){
            text = subStr[1];
            title = subStr[0];
        }else{
            text = str;
        }
        Log.i("notification : ", "String  : "+ Arrays.toString(subStr));
        Intent resultIntent = new Intent(this, MainActivity.class);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setAutoCancel(true)
                        .setTicker(title)
                        .setWhen(System.currentTimeMillis())
                        .setDefaults(Notification.DEFAULT_ALL)
                        .setContentIntent(resultPendingIntent);

        Notification notification = builder.build();

        // Show Notification
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(1, notification);

    }

    private void alert(String str){
        Log.i("alert : ", "String  : "+str);
        String[] subStr;
        subStr = str.split(";");
        String text = "";
        String title = "";
        if (subStr.length >= 2){
            text = subStr[1];
            title = subStr[0];
        }else{
            text = str;
        }
        Log.i("alert : ", "String  : " + Arrays.toString(subStr));

        sendBrodecast("Alert");
    }

    class ReTimerTask extends TimerTask {
        @Override
        public void run() {
            Log.i("Timer", "Start");
            if (MQTTclient.isConnected()) {
                myTimer.cancel();
                myTimer = null;
                connectionLost = false;
                Log.i("Timer", "Stop");
            }else{
                connect();
            }
        }
    }



}