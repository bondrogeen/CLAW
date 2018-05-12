package ru.codedevice.claw;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private String TAG = "ClawMainActivity";
    public final static String BROADCAST_ACTION = "ru.codedevice.claw";
    public final static String PARAM_STATUS = "status";

    BroadcastReceiver br;
    Button mqttSend, mqttStart;
    EditText mqttTextTopic, mqttTextValue;
    Intent intent;
    Toast toast;

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mqttStart = findViewById(R.id.mqtt_start);
        mqttSend =  findViewById(R.id.mqtt_send);
        mqttTextTopic = findViewById(R.id.topic_text);
        mqttTextValue = findViewById(R.id.value_text);
        View.OnClickListener mqttStartOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG,  "Click button Start");
                if(isMyServiceRunning(MqttService.class)){
                    stopService(new Intent(MainActivity.this, MqttService.class));
                    mqttStart.setBackgroundColor(Color.GREEN);
                    mqttStart.setText(R.string.textButtonStart_start);
                }else{
                    startService(new Intent(MainActivity.this, MqttService.class));
                }
                mqttStart.setEnabled(false);
            }
        };

        View.OnClickListener mqttSendOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG,  "Click button Send");
                if(isMyServiceRunning(MqttService.class)){
                    String topic = mqttTextTopic.getText().toString();
                    String value = mqttTextValue.getText().toString();
                    if (value.equals("")){
                        toast = Toast.makeText(getApplicationContext(), "Fill in the fields 'value'", Toast.LENGTH_SHORT);
                        toast.show();
                        mqttTextValue.requestFocus();
                    }
                    if (topic.equals("")){
                        toast = Toast.makeText(getApplicationContext(), "Fill in the fields 'topic'", Toast.LENGTH_SHORT);
                        toast.show();
                        mqttTextTopic.requestFocus();
                    }

                    if(!topic.equals("") && !value.equals("")){
                        Intent service = new Intent(MainActivity.this, MqttService.class);
                        service.putExtra("topic", topic);
                        service.putExtra("value", value);
                        startService(service);
                    }

                }else{
                    toast = Toast.makeText(getApplicationContext(), "First, connect to the broker", Toast.LENGTH_SHORT);
                    toast.show();
                }
            }
        };

        mqttStart.setOnClickListener(mqttStartOnClick);
        mqttSend.setOnClickListener(mqttSendOnClick);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        if(isMyServiceRunning(MqttService.class)){
            Log.d(TAG,  "MqttService is run");
            mqttStart.setBackgroundColor(Color.RED);
            mqttStart.setText(R.string.R_string_textButtonStart_stop);
            mqttSend.setEnabled(true);
        }else{
            Log.d(TAG,  "MqttService is not run");
            mqttStart.setBackgroundColor(Color.GREEN);
            mqttStart.setText(R.string.textButtonStart_start);
            mqttSend.setEnabled(false);
        }

        initBrodecast();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
//            return true;
        }
        if (id == R.id.action_about) {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
//            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_settings) {
            intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_iobroker) {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://iobroker.net/new-site/"));
            startActivity(intent);
        } else if (id == R.id.nav_smart_home) {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/SmartsHome"));
            startActivity(intent);
        } else if (id == R.id.nav_about) {
            intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void initBrodecast(){
        br = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String status = intent.getStringExtra(PARAM_STATUS);
                Log.i(TAG, "onReceive: status = " + status);
                if(status.equals("Connection")){
                    mqttStart.setBackgroundColor(Color.RED);
                    mqttStart.setText(R.string.R_string_textButtonStart_stop);
                    mqttStart.setEnabled(true);
                    mqttSend.setEnabled(true);
                }
                if(status.equals("ConnectionFailure")
                        || status.equals("noEnable")
                        || status.equals("disconnectFailure")
                        || status.equals("disconnect")
                        || status.equals("noNetwork")){
                    mqttStart.setEnabled(true);
                    mqttSend.setEnabled(false);
                    if (status.equals("noEnable")){
                        intent = new Intent(MainActivity.this, SettingsActivity.class);
                        startActivity(intent);
                    }
                }

            }
        };

        IntentFilter intFilt = new IntentFilter(BROADCAST_ACTION);
        registerReceiver(br, intFilt);
    }

    protected void onResume() {
        super.onResume();
    }

    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(br);
    }
}


