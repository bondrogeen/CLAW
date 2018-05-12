package ru.codedevice.claw;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Button;
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

    String clientId;
    String server;
    String port;
    String serverUri;
    String username;
    String password;
    Boolean run;
    Boolean start;
    Boolean tts_OK;
    Boolean tts_TIME;

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void onCreate() {
        super.onCreate();
        initTTS();
        initMQTT();
        Log.d(TAG, "onCreate");
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "startId :" + startId);
        if (intent != null && intent.getExtras() != null) {
            String topic = intent.getStringExtra("topic");
            String value = intent.getStringExtra("value");
            Log.d(TAG, "topic :" + topic);
            Log.d(TAG, "value :" + value);
            publish(topic, value);
        } else {
            if (start && !MQTTclient.isConnected()) {
                connect();
                Log.d(TAG, "Connect MQTT");
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
    }

    @Override
    public void connectionLost(Throwable throwable) {
        Log.d(TAG, "connectionLost");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        Log.d(TAG, "topic: "+topic + " value: "+message);
        Log.d(TAG, message.toString());
        if (message.equals("")){return;}
        if (topic.equals(clientId+"/tts/request")){
            speakOut(message.toString());
        }
        if (topic.equals(clientId+"/tts/command")){
            String mes = message.toString().toLowerCase();
            if(mes.equals("stop")){

            }

        }

    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
    }

    public void initMQTT() {
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        server = settings.getString("mqtt_server", "");
        port = settings.getString("mqtt_port", "");
        serverUri = "tcp://" + server + ":" + port;
        username = settings.getString("mqtt_login", "");
        password = settings.getString("mqtt_pass", "");
        run = settings.getBoolean("mqtt_run", false);
        start = settings.getBoolean("mqtt_switch", false);
        clientId = "CLAW";

        MQTTclient = new MqttAndroidClient(this.getApplicationContext(), serverUri, clientId);
        Log.i(TAG, "isConn: " + MQTTclient.isConnected());
        options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setUserName(username);
        options.setPassword(password.toCharArray());
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
                    setSubscribe();
                    toast = Toast.makeText(getApplicationContext(),
                            "Connection", Toast.LENGTH_SHORT);
                    toast.show();
                    sendBrodecast("Connection");
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
        if (MQTTclient.isConnected()) {
            try {
                IMqttToken token = MQTTclient.disconnect();
                token.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        toast = Toast.makeText(getApplicationContext(),
                                "Disconnect", Toast.LENGTH_SHORT);
                        toast.show();
                        Log.d(TAG, "Disconnect success");
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.d(TAG, "Disconnect failure");
                        toast = Toast.makeText(getApplicationContext(),
                                "Disconnect failure", Toast.LENGTH_SHORT);
                        toast.show();
                    }
                });
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    private void pubOne() {
        publish("info/BRAND", Build.BRAND);
        publish("info/MANUFACTURER", Build.MANUFACTURER);
        publish("info/MODEL", Build.MODEL);
        publish("info/PRODUCT", Build.PRODUCT);
        publish("tts/request", "");
        publish("tts/command", "");
    }

    private void setSubscribe() {
        String topic = "tts/*";
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
                Log.i(TAG, "TTS onStart");
            }

            @Override
            public void onDone(String utteranceId) {
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

}