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
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsMessage;
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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class AppMqttService extends Service implements MqttCallback {

    final String TAG = "AppMqttService";

    MqttAndroidClient MQTTclient;
    MqttConnectOptions options;
    SharedPreferences settings;
    SharedPreferences sp;
    TextToSpeech tts;
    Toast toast;
    BroadcastReceiver br;
    PowerManager.WakeLock wl = null;
    Context context;

    String clientId;
    String mqtt_server;
    String mqtt_device;
    String mqtt_port;
    String serverUri;
    String mqtt_username;
    String mqtt_password;
    String mqtt_first_topic;
    Boolean run;
    Boolean general_startBoot;
    Boolean tts_OK;
    Boolean connectionLost = false;
    String topic = "comm/*";
    JSONObject wedgetNameJSON;
    JSONObject allWedgetJSON;

    String widgetName;
    String widgetId;
    String widgetType;
    String widgetValue;
    String widgetText;
    String widgetTitle;
    int notificationIdNumber = 1;

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
        sp = getSharedPreferences(ConfigWidget.WIDGET_PREF, MODE_PRIVATE);
        Log.d(TAG, "onCreate");
    }

    public void initBroadReceiver() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        if (settings.getBoolean("send_data_battery", true)){
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        }
        br = new AppReceiver();
        registerReceiver(br, filter);
    }
    public void initSettings() {

        settings = PreferenceManager.getDefaultSharedPreferences(this);
        clientId = "MAC";
        if (!settings.getString("mqtt_first_topic", "").equals("")){
            clientId = settings.getString("mqtt_first_topic", "")+"/"+clientId;
        }
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
                Locale def_local = Locale.getDefault();
                if (def_local!=null && status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(def_local);
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
//        Log.d(TAG, "flags :" + flags);
        Boolean net = checkInternet();

        if (intent != null && intent.getExtras() != null) {
            String status = intent.getStringExtra("statusInit");
            Log.d(TAG, "statusInit :" + status);
            switch (status) {
                case "screen":
                    publish("info/display/status", getDisplay());
                    break;
                case "publish":
                    String topic = intent.getStringExtra("topic");
                    String value = intent.getStringExtra("value");
                    publish(topic, value);
                    break;
                case "sms":
//                    String topic = intent.getStringExtra("topic");
//                    String value = intent.getStringExtra("value");

                    Bundle bundle = intent.getExtras();
                    if(bundle != null) {
                        Object[] pdus = (Object[]) bundle.get("pdus");
                        String format = bundle.getString("format");
                        SmsMessage[] messages = new SmsMessage[pdus.length];
                        for(int i = 0; i < pdus.length; i++) {
                            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                            }else {
                                messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                            }
                            String senderPhoneNo = messages[i].getDisplayOriginatingAddress();
                            Toast.makeText(context, "Message " + messages[0].getMessageBody() + ", from " + senderPhoneNo, Toast.LENGTH_SHORT).show();
                        }
                    }

//                    publish(topic, value);
                    break;
                case "power":
                    String power = intent.getStringExtra("power");
                    publish("info/battery/charging", power);
                    break;
                case "batteryStatus":
                    String battery = intent.getStringExtra("battery");
                    publish("info/battery/status", battery);
                    break;
                case "battery":
                    int level = intent.getIntExtra("level", -1);
                    publish("info/battery/level", String.valueOf(level));
//                    int scale = intent.getIntExtra("scale", -1);
//                    publish("info/battery/scale", String.valueOf(scale));
//                    int stat = intent.getIntExtra("status", -1);
//                    publish("info/battery/status", String.valueOf(stat));
                    int voltage = intent.getIntExtra("voltage", -1);
                    publish("info/battery/voltage", String.valueOf(voltage));
                    int plugtype = intent.getIntExtra("plugged", -1);
                    String type = "";
                    if(plugtype==0){
                        type = "none";
                    }else if(plugtype==1){
                        type = "charging";
                    }else if(plugtype==2){
                        type = "usb";
                    }else{
                        type = String.valueOf(plugtype);
                    }
                    publish("info/battery/plugtype", type);
                    int health = intent.getIntExtra("health", -1);
                    publish("info/battery/health", String.valueOf(health));
                    int temperature = intent.getIntExtra("temperature", -1);
                    publish("info/battery/temperature", String.valueOf(temperature));
                    break;
                case "widget":
                    widgetName = intent.getStringExtra("widgetName");
                    widgetId = intent.getStringExtra("widgetId");
                    widgetType = intent.getStringExtra("widgetType");
                    widgetValue = sp.getString(ConfigWidget.WIDGET_KEY_VALUE + widgetId, "false");

                    Log.e(TAG, "AppMqttService widgetId = " + widgetId);
                    Log.e(TAG, "AppMqttService widgetName = " + widgetName);
                    Log.e(TAG, "AppMqttService widgetType = " + widgetType);
                    if (MQTTclient.isConnected()) {
                        if (widgetType.equals(ConfigWidget.WIDGET_TYPE_TEXT_AND_TITLE)) {
                            publish("info/widget/" + widgetName + "/tap", widgetValue);
                        }
                        if (widgetType.equals(ConfigWidget.WIDGET_TYPE_BUTTON)) {
                            publish("info/widget/" + widgetName + "/tap", widgetValue);
                        }
                        SharedPreferences.Editor editor = sp.edit();
                        widgetValue = (widgetValue.equals("false") ? "true" : "false");
                        Log.e(TAG, "AppMqttService widgetValue revirs = " + widgetValue);
                        editor.putString(ConfigWidget.WIDGET_KEY_VALUE + widgetId, widgetValue);
                        editor.apply();

                        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
                        AppWidgetOne.updateAppWidget(this, appWidgetManager, Integer.parseInt(widgetId), sp);
                    }
                    break;
                case "widget_create":
                    widgetName = intent.getStringExtra("widgetName");

                    try {
//                        wedgetNameJSON = AppWidgetOne.allWidget.getJSONObject(widgetName);
                        wedgetNameJSON = Storage.get("allWidget").getJSONObject(widgetName);
                        widgetId = wedgetNameJSON.getString("ID");
                        widgetType = wedgetNameJSON.getString("TYPE");
                        widgetText = wedgetNameJSON.getString("TEXT");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    Log.e(TAG, "AppMqttService widgetId = " + widgetId);
                    Log.e(TAG, "AppMqttService widgetName = " + widgetName);
                    Log.e(TAG, "AppMqttService widgetType = " + widgetType);
                    Log.e(TAG, "AppMqttService widgetText = " + widgetText);

                    widgetValue = sp.getString(ConfigWidget.WIDGET_KEY_VALUE + widgetId, "false");
                    widgetTitle = sp.getString(ConfigWidget.WIDGET_KEY_TITLE + widgetId, "false");
                    if (MQTTclient.isConnected()) {
                        if (widgetType.equals(ConfigWidget.WIDGET_TYPE_TEXT_AND_TITLE)) {
                            publish("comm/widget/" + widgetName + "/text", widgetText);
                            publish("comm/widget/" + widgetName + "/title", widgetTitle);
                        }
                        if (widgetType.equals(ConfigWidget.WIDGET_TYPE_BUTTON)) {
                            publish("comm/widget/" + widgetName + "/button", widgetValue);
                            publish("comm/widget/" + widgetName + "/title", widgetTitle);
                        }
                    }
                    break;
            }

            if(!connectionLost){
                stopSelf();
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
        unregisterReceiver(br);
    }

    @Override
    public void connectionLost(Throwable throwable) {
        Log.d(TAG, "connectionLost");
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
        if (topic.equals(clientId + "/" + mqtt_device +"/comm/notification/create")){
            notification(message.toString());
        }
        if (topic.equals(clientId + "/" + mqtt_device +"/comm/notification/delete")){
            notificationDel(message.toString());
        }
        if (topic.equals(clientId + "/" + mqtt_device +"/comm/notification/alert")){
            alert(message.toString());
        }
        if (topic.contains(clientId + "/" + mqtt_device +"/comm/widget/")){
            String[] arrTopic;
            arrTopic = topic.split("/");
            Log.d(TAG, "topic "+topic);
            Log.d(TAG, "arrTopic "+arrTopic[4]);
            Log.d(TAG, "WTF");
//            wedgetNameJSON = AppWidgetOne.allWidget.getJSONObject(arrTopic[4]);
            wedgetNameJSON = Storage.get("allWidget").getJSONObject(arrTopic[4]);
            if(wedgetNameJSON!=null){
                Log.d(TAG, "wedgetNameJSON "+wedgetNameJSON);
                SharedPreferences.Editor editor = sp.edit();
                if(arrTopic[5].equals("text")){
                    Log.d(TAG, "arrTopic[5].equals(\"text\")");
                    editor.putString(ConfigWidget.WIDGET_KEY_TEXT + wedgetNameJSON.getString("ID"), String.valueOf(message));
                    publish("comm/widget/"+arrTopic[4]+"/text", "");
                    publish("info/widget/"+arrTopic[4]+"/text", String.valueOf(message));
                }
                if(arrTopic[5].equals("button")){
                    Log.d(TAG, "arrTopic[5].equals(\"button\")");
                    editor.putString(ConfigWidget.WIDGET_KEY_VALUE + wedgetNameJSON.getString("ID"), String.valueOf(message));
                    publish("comm/widget/"+arrTopic[4]+"/button", "");
                    publish("info/widget/"+arrTopic[4]+"/tap", String.valueOf(message));
                }
                if(arrTopic[5].equals("title")){
                    Log.d(TAG, "arrTopic[5].equals(\"title\")");
                    editor.putString(ConfigWidget.WIDGET_KEY_TITLE + wedgetNameJSON.getString("ID"), String.valueOf(message));
                    publish("comm/widget/"+arrTopic[4]+"/title", "");
                    publish("info/widget/"+arrTopic[4]+"/title", String.valueOf(message));
                }
                editor.apply();
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
                AppWidgetOne.updateAppWidget(this, appWidgetManager, Integer.parseInt(wedgetNameJSON.getString("ID")), sp);
            }
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
        connectionLost = true;
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
                    Log.d(TAG, "Connection Failure");
                    toast = Toast.makeText(getApplicationContext(),
                            "Connection Failure", Toast.LENGTH_SHORT);
                    toast.show();
                    sendBrodecast("ConnectionFailure");
                    stopSelf();
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        Log.d(TAG, "Disconnect start");
        Log.d(TAG, "Disconnect isConnected "+MQTTclient.isConnected());
        if (MQTTclient != null && connectionLost) {
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
        }
        sendBrodecast("disconnect");
    }

//    private void updateInfoWidget(){
//        Intent intent = new Intent(this, AppWidgetOne.class);
//        AppWidgetManager manager = AppWidgetManager.getInstance(context);
//        final int[] appWidgetIds = manager.getAppWidgetIds(new ComponentName(context, AppWidgetOne.class));
//        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
//        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
//        sendBroadcast(intent);
//    }

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
        publish("comm/notification/create", "");
        publish("comm/notification/delete", "");
        publish("comm/notification/alert", "");
        publish("info/battery/charging", "");
        initWidget();

    }

    private void initWidget(){
        allWedgetJSON = AppWidgetOne.allWidget;
        JSONObject widgetKey;
        Log.d(TAG, "lenth "+String.valueOf(allWedgetJSON.length()));
        if (allWedgetJSON.length()>0){
            Iterator<String> keysJSON = allWedgetJSON.keys();
            while(keysJSON.hasNext()) {
                String key = keysJSON.next();
                Log.e(TAG,"key "+key);
                try {
                    widgetKey = allWedgetJSON.getJSONObject(key);
                    Log.e(TAG,"widgetKey "+widgetKey);
                    String Name = widgetKey.getString("NAME");
                    String Type = widgetKey.getString("TYPE");
                    String Text = widgetKey.getString("TEXT");
                    String Title = widgetKey.getString("TITLE");
                    String Value = widgetKey.getString("VALUE");
                    if (Type.equals(ConfigWidget.WIDGET_TYPE_TEXT_AND_TITLE)) {
                        publish("comm/widget/" + Name + "/text", Text);
                        publish("comm/widget/" + Name + "/title", Title);
                    }if (Type.equals(ConfigWidget.WIDGET_TYPE_BUTTON)) {
                        publish("comm/widget/" + Name + "/button", Value);
                        publish("comm/widget/" + Name + "/title", Title);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
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
            brightness = (int) Math.round(brightness/2.55);
        } catch (Exception ignored) {

        }
        return brightness;
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

    private void batteryInfo(String str){
//        intent = IntentUtil.getIntentResultFromBroadcast(service, Intent.ACTION_BATTERY_CHANGED);

//        level = intent.getIntExtra("level", -1);
//        scale = intent.getIntExtra("scale", -1);
//        status = intent.getIntExtra("status", -1);
//        voltage = intent.getIntExtra("voltage", -1);
//        plugtype = intent.getIntExtra("plugged", 0);
//        health = intent.getIntExtra("health", 0);
//        temperature = intent.getIntExtra("temperature", 0);
    }
    private void notificationDel(String str){
        if(str.toLowerCase().equals("all")){
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.cancelAll();
        }else{
            if (str.matches("[0-9]*")) {
                NotificationManager notificationManager =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                notificationManager.cancel(Integer.parseInt(str));
            }
        }
    }

    private void notification(String str){
        Log.i("notification : ", "String  : "+str);
        JSONObject nativeJSON = new JSONObject();
        JSONObject tempJSON;
        notificationIdNumber = notificationIdNumber+1;
        try {
            nativeJSON.put("text","");
            nativeJSON.put("title","Title");
            nativeJSON.put("id",notificationIdNumber);
            nativeJSON.put("rightinfo","");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        String text = "";
        String title = "";
        String id = "";
        String rightinfo = "";

        tempJSON = parseJSONValid(nativeJSON,str);
        nativeJSON = null;
        try {
            text = tempJSON.getString("text");
            title = tempJSON.getString("title");
            id = tempJSON.getString("id");
            rightinfo = tempJSON.getString("rightinfo");
        } catch (JSONException e) {
            e.printStackTrace();
        }


        Intent resultIntent = new Intent(this, NotificationActivity.class);
        resultIntent.putExtra("id", id);
        resultIntent.putExtra("text", text);
        resultIntent.putExtra("title", title);

        PendingIntent resultPendingIntent = PendingIntent.getActivity(this, Integer.parseInt(id), resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setAutoCancel(true)
                        .setTicker(title)
                        .setContentInfo(rightinfo)
                        .setWhen(System.currentTimeMillis())
                        .setDefaults(Notification.DEFAULT_ALL)
                        .setContentIntent(resultPendingIntent);

        Notification notification = builder.build();

        // Show Notification
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(Integer.parseInt(id), notification);
        Log.i("notification : ", "id  : "+id);
    }


    public JSONObject parseJSONValid(JSONObject nativeObj, String str) {

        JSONObject mergedObj = new JSONObject();
        JSONObject parseObj = new JSONObject();;
        try {
            parseObj = new JSONObject(str);
        } catch (JSONException ex) {
            Log.i("notification : ", "No JSON");
            try {
                parseObj.put("text",str);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        Iterator i1 = nativeObj.keys();
        Iterator i2 = parseObj.keys();
        String tmp_key;
        while(i1.hasNext()) {
            tmp_key = (String) i1.next();
            try {
                mergedObj.put(tmp_key, nativeObj.get(tmp_key));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        while(i2.hasNext()) {
            tmp_key = (String) i2.next();
            try {
                mergedObj.put(tmp_key, parseObj.get(tmp_key));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        Log.i("mergedObj : ", String.valueOf(mergedObj));
        return mergedObj;
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
}