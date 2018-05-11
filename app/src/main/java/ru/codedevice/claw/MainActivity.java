package ru.codedevice.claw;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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

    private String TAG = "MainActivity";
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
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });

        mqttStart = (Button) findViewById(R.id.mqtt_start);
        mqttSend = (Button) findViewById(R.id.mqtt_send);
        mqttTextTopic = (EditText)findViewById(R.id.topic_text);
        mqttTextValue = (EditText)findViewById(R.id.value_text);
        View.OnClickListener mqttStartOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG,  "Start");
                startService(new Intent(MainActivity.this, MqttService.class));

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

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        if(isMyServiceRunning(MqttService.class)){
            Log.d(TAG,  "MqttService is run");
            mqttStart.setBackgroundColor(Color.RED);
        }else{
            Log.d(TAG,  "MqttService is not run");
            mqttStart.setBackgroundColor(Color.GREEN);
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
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
            stopService(new Intent(this, MqttService.class));
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://iobroker.net/new-site/"));
            startActivity(intent);
        } else if (id == R.id.nav_smart_home) {
            stopService(new Intent(this, MqttService.class));
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/SmartsHome"));
            startActivity(intent);
        } else if (id == R.id.nav_about) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }


//
//    public class MqttBroadcastReceiver extends BroadcastReceiver {
//
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            String topic = intent.getStringExtra("topic");
//            String value = intent.getStringExtra("value");
//
//            Log.d(TAG, "topic: " + topic);
//            Log.d(TAG, "value: " + value);
//
////            if (topic.equals("relay")) {
////                setLightIcon(value);
////            }
//
//        }
//    }

    protected void onResume() {
        super.onResume();
    }

    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}


