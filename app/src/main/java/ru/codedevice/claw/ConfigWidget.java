package ru.codedevice.claw;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;

public class ConfigWidget extends AppCompatActivity {
    int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    SharedPreferences settings;
    private String TAG = "ConfigWidget";
    Intent resultValue;
    EditText text;
    RadioGroup type_group;
    Button widget_buttone_add;
    String type = "text";
    String settings_text;
    String settings_type;
    String settings_name;


    public final static String WIDGET_PREF = "widget_type_";
    public final static String WIDGET_TYPE = "widget_type_";
    public final static String WIDGET_TEXT = "widget_text_";
    public final static String WIDGET_NAME = "widget_name_";
    public final static String WIDGET_COLOR = "widget_color_";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config_widget);
        Log.d(TAG, "onCreate config");

        settings = getSharedPreferences(WIDGET_PREF, MODE_PRIVATE);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
        }

        resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        setResult(RESULT_CANCELED, resultValue);

        settings_text = settings.getString(ConfigWidget.WIDGET_TEXT + appWidgetId, "");
        settings_type = settings.getString(ConfigWidget.WIDGET_TYPE + appWidgetId, "");
        settings_name = settings.getString(ConfigWidget.WIDGET_NAME + appWidgetId, "");

        text = findViewById(R.id.widget_text);
        type_group = findViewById(R.id.widget_type_grour);
        widget_buttone_add = findViewById(R.id.widget_button_add);



        type_group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.widget_type_text:
                        type = "text";
                        break;
                    case R.id.widget_type_text_title:
                        type = "text_title";
                        break;
                    case R.id.widget_type_button:
                        type = "button";
                        break;
                }
                Log.d(TAG, "onClick "+type);
            }
        });


        View.OnClickListener button_click = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SaveButton();
                finish();
            }
        };

        // присвоим обработчик кнопке OK (btnOk)
        widget_buttone_add.setOnClickListener(button_click);


    }

    public void SaveButton(){
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(WIDGET_TEXT + appWidgetId, text.getText().toString());
        editor.putString(WIDGET_TYPE + appWidgetId, type);
        editor.commit();
        setResult(RESULT_OK, resultValue);
        Log.d(TAG, "finish config " + appWidgetId);
    }
}
