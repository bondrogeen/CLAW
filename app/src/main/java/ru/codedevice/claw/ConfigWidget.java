package ru.codedevice.claw;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class ConfigWidget extends AppCompatActivity {
    int widgetID = AppWidgetManager.INVALID_APPWIDGET_ID;
    private String TAG = "ConfigWidget";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config_widget);

        Log.d(TAG,  "onCreate widget");


        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            widgetID = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (widgetID == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
        }
        Log.d(TAG,  "widgetID "+widgetID);

    }


}
