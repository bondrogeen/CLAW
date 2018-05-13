package ru.codedevice.claw;

import android.app.Service;
import android.content.BroadcastReceiver;
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
import android.util.Log;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Locale;

public class MqttService extends Service implements MqttCallback {

    final String TAG = "MqttService";

    MqttAndroidClient MQTTclient;
    MqttConnectOptions options;
    SharedPreferences settings;
    TextToSpeech tts;
    Toast toast;
//    Context context;
    BroadcastReceiver br;
    PowerManager.WakeLock wl = null;

    String clientId;
    String mqtt_server;
    String mqtt_device;
    String mqtt_port;
    String serverUri;
    String mqtt_username;
    String mqtt_password;
    Boolean run;
    Boolean mqtt_autoStart;
//    Boolean mqttRun;
    Boolean tts_OK;
//    Boolean tts_TIME;
    Boolean connectionLost;

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void onCreate() {
        super.onCreate();
        initTTS();
        initMQTT();
        Log.d(TAG, "onCreate");
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        br = new AppReceiver();
        registerReceiver(br, filter);
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
                case "autoStart":
                    if (mqtt_autoStart) {
                        Log.d(TAG, "autoStart isConnected : " + MQTTclient.isConnected());
                        Log.d(TAG, "autoStart net : " + net);
                        if (!MQTTclient.isConnected() && net) {
                            connect();
                            Log.d(TAG, "autoStart");
                        }
                    }
                    break;
                case "screen":
                    publish("info/display/status", getDisplay());
                    break;
                case "publish":
                    String topic = intent.getStringExtra("topic");
                    String value = intent.getStringExtra("value");
                    publish(topic, value);
                    break;
            }
        } else {
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
        sendBrodecast("disconnect");
    }

    @Override
    public void connectionLost(Throwable throwable) {
        connectionLost=true;
        Log.d(TAG, "connectionLost");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        Log.d(TAG, "topic: "+topic + " value: "+message);
        Log.d(TAG, message.toString());
        if (message.toString().equals("")){return;}
        if (topic.equals(clientId+"/comm/tts/request")){
            speakOut(message.toString());
        }
        if (topic.equals(clientId+"/comm/tts/command")){
//            String mes = message.toString().toLowerCase();
//            if(mes.equals("stop")){//
//            }
        }
        if (topic.equals(clientId+"/comm/display/level")){
            if(isNumber(message.toString())){
                int num = Integer.parseInt(message.toString());
                setBrightness(num);
            }
        }
        if (topic.equals(clientId+"/comm/display/mode")){
            int num = isTrue(message.toString());
            if(num==1 || num==2){
                setBrightnessMode(num);
            }
        }
        if (topic.equals(clientId+"/comm/display/toWake")){
            int num = isTrue(message.toString());
            if(num==1 || num==2){
                setDisplay(num);
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

    public void initMQTT() {
        Log.i(TAG, "Start initMQTT");
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        mqtt_server = settings.getString("mqtt_server", "");
        mqtt_port = settings.getString("mqtt_port", "");
        serverUri = "tcp://" + mqtt_server + ":" + mqtt_port;
        mqtt_username = settings.getString("mqtt_login", "");
        mqtt_password = settings.getString("mqtt_pass", "");
        mqtt_device = settings.getString("mqtt_device", Build.MODEL);

        run = settings.getBoolean("mqtt_run", false);
        mqtt_autoStart = settings.getBoolean("mqtt_switch", false);
        clientId = "CLAW";

        MQTTclient = new MqttAndroidClient(this.getApplicationContext(), serverUri, clientId);
        options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setUserName(mqtt_username);
        options.setPassword(mqtt_password.toCharArray());
        MQTTclient.setCallback(this);
    }

    public void publish(String topic, String payload) {
        byte[] encodedPayload = new byte[0];
        try {
            encodedPayload = payload.getBytes("UTF-8");
            MqttMessage message = new MqttMessage(encodedPayload);
            MQTTclient.publish(clientId + "/" + topic, message);
        } catch (UnsupportedEncodingException | MqttException e) {
            e.printStackTrace();
        }
    }

    public void connect() {
        try {
            IMqttToken token = MQTTclient.connect(options);
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Connection");
                    pubOne();
                    toast = Toast.makeText(getApplicationContext(),
                            "Connection", Toast.LENGTH_SHORT);
                    toast.show();
                    sendBrodecast("Connection");
                    setSubscribe();
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
        if (MQTTclient.isConnected()||connectionLost) {
            Log.d(TAG, "Disconnect START ");
            try {
                IMqttToken token = MQTTclient.disconnect();
                token.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        options.setAutomaticReconnect(false);
                        toast = Toast.makeText(getApplicationContext(),
                                "Disconnect", Toast.LENGTH_SHORT);
                        toast.show();
                        Log.d(TAG, "Disconnect success");
                        sendBrodecast("disconnect");
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.d(TAG, "Disconnect failure");
                        toast = Toast.makeText(getApplicationContext(),
                                "Disconnect failure", Toast.LENGTH_SHORT);
                        toast.show();
                        sendBrodecast("disconnectFailure");
                    }
                });
            } catch (MqttException e) {
                e.printStackTrace();
            }

        }
    }

    private void pubOne() {
        publish("info/general/BRAND", Build.BRAND);
        publish("info/general/MODEL", Build.MODEL);
        publish("info/general/PRODUCT", Build.PRODUCT);
        publish("info/display/level", String.valueOf(getBrightness()));
        publish("info/display/mode", getBrightnessMode());
        publish("info/display/status", getDisplay());
        publish("info/display/sleep", "true");
        publish("info/tts/talk", "false");
        publish("comm/tts/request", "");
        publish("comm/tts/command", "");
        publish("comm/display/level", "");
        publish("comm/display/mode", "");
        publish("comm/display/toWake", "");
    }

    private void setSubscribe() {
        String topic = "comm/*";
        int qos = 1;
        try {
            IMqttToken subToken = MQTTclient.subscribe(clientId + "/" + topic, qos);
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
}