package ru.codedevice.claw;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.UnsupportedEncodingException;

public class MqttService extends Service {

    final String TAG = "MqttService";

    MqttAndroidClient client;
    MqttConnectOptions options;
    SharedPreferences settings;
    Toast toast;
    String clientId;
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        initMQTT();
        return super.onStartCommand(intent, flags, startId);
    }

    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        disconnect();
    }

    public void initMQTT() {
        settings = PreferenceManager.getDefaultSharedPreferences(this);

        final String server = settings.getString("mqtt_server", "");
        final String port = settings.getString("mqtt_port", "");
        final String serverUri = "tcp://"+server+":"+port;
        final String username = settings.getString("mqtt_login", "");
        final String password = settings.getString("mqtt_pass", "");
        final Boolean run = settings.getBoolean("mqtt_run", false);
        final Boolean start = settings.getBoolean("mqtt_switch", false);
        clientId = "CLAW";
//        final String subscriptionTopic = "sensor/+";

//        String clientId = MqttClient.generateClientId();

        client = new MqttAndroidClient(this.getApplicationContext(), serverUri, clientId);
        Log.i(TAG, "isConn: " + client.isConnected());
        if (start && !client.isConnected()) {

            options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setUserName(username);
            options.setPassword(password.toCharArray());
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable throwable) {
                    Log.d(TAG, "connectionLost");
                }

                @Override
                public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                    Log.d(TAG, mqttMessage.toString());
                    Log.i(TAG, "isConn: " + client.isConnected());
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
                }
            });
                connect();
        }
    }

    public void publish(String topic,String payload) {
//        String topic = "foo/bar";
//        String payload = "the payload";
        byte[] encodedPayload = new byte[0];
        try {
            encodedPayload = payload.getBytes("UTF-8");
            MqttMessage message = new MqttMessage(encodedPayload);
            client.publish(clientId+"/"+topic, message);
        } catch (UnsupportedEncodingException | MqttException e) {
            e.printStackTrace();
        }
    }
    public void connect(){
        try {
            IMqttToken token = client.connect(options);
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Connection");
                    pubOne();
                    setSubscribe();
                    toast = Toast.makeText(getApplicationContext(),
                            "Connection", Toast.LENGTH_SHORT);
                    toast.show();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d(TAG, "Connection Failure");
                    toast = Toast.makeText(getApplicationContext(),
                            "Connection Failure", Toast.LENGTH_SHORT);
                    toast.show();
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
    public void disconnect(){

        if(client.isConnected()) {
            try {
                IMqttToken token = client.disconnect();
                token.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Log.d(TAG, "disconnect");
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.d(TAG, "not disconnect");
                    }
                });
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    private void pubOne() {
        publish("info/BRAND",Build.BRAND);
        publish("info/MANUFACTURER",Build.MANUFACTURER);
        publish("info/MODEL",Build.MODEL);
        publish("info/PRODUCT",Build.PRODUCT);
        publish("test","");
    }

    private void setSubscribe() {
        String topic = "test";
        int qos = 1;
        try {
            IMqttToken subToken = client.subscribe(clientId+"/"+topic, qos);
            subToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // The message was published
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken,
                                      Throwable exception) {
                    // The subscription could not be performed, maybe the user was not
                    // authorized to subscribe on the specified topic e.g. using wildcards

                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
