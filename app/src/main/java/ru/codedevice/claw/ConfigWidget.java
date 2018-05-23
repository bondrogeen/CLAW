package ru.codedevice.claw;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

public class ConfigWidget extends AppCompatActivity {
    int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    SharedPreferences settings;
    private String TAG = "ConfigWidget";
    Intent resultValue;
    Context context;
    EditText text;
    EditText name;
    EditText title;
    RadioGroup type_group;
    FloatingActionButton buttonAdd;
    String textType;
    String textText;
    String textName;
    String textTitle;

    public final static String WIDGET_PREF = "widget_settings";
    public final static String WIDGET_KEY_TYPE = "widget_type_";
    public final static String WIDGET_KEY_TEXT = "widget_text_";
    public final static String WIDGET_KEY_TITLE = "widget_title_";
    public final static String WIDGET_KEY_NAME = "widget_name_";
    public final static String WIDGET_KEY_COLOR = "widget_color_";

    public final static String WIDGET_TYPE_TEXT_AND_TITLE = "testTitle";
    public final static String WIDGET_TYPE_BUTTON = "button";

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
        resultValue.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_CANCELED, resultValue);

        textText = "--";
        textType = ConfigWidget.WIDGET_TYPE_TEXT_AND_TITLE;
        textName = "Widget_"+appWidgetId;
        textTitle = "Title";

        text = findViewById(R.id.widget_text);
        name = findViewById(R.id.widget_name);
        title = findViewById(R.id.widget_title);

        name.setText(textName, TextView.BufferType.EDITABLE);
        text.setText(textText, TextView.BufferType.EDITABLE);
        title.setText(textTitle, TextView.BufferType.EDITABLE);
        type_group = findViewById(R.id.widget_type_grour);

        buttonAdd = findViewById(R.id.widget_button_add);
        buttonAdd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SaveButton();
                finish();
            }
        });

    }

    public String GetCheckedRadioButton(RadioGroup radioGroup){
        String type = "";
        int checkedId = radioGroup.getCheckedRadioButtonId();
        switch (checkedId) {
            case R.id.widget_type_text_title:
                type = ConfigWidget.WIDGET_TYPE_TEXT_AND_TITLE;
                break;
            case R.id.widget_type_button:
                type = ConfigWidget.WIDGET_TYPE_BUTTON;
                break;
        }
        Log.d(TAG, "onClick "+type);
        return type;
    }
    public void SaveButton(){
        textName = String.valueOf(name.getText());
        textText = String.valueOf(text.getText());
        textTitle = String.valueOf(title.getText());
        textType = GetCheckedRadioButton(type_group);
        Log.d(TAG, "textName " + textName);
        Log.d(TAG, "textText " + textText);
        Log.d(TAG, "textType " + textType);
        Log.d(TAG, "textTitle " + textTitle);

        SharedPreferences.Editor editor = settings.edit();
        editor.putString(WIDGET_KEY_TEXT + appWidgetId, textText);
        editor.putString(WIDGET_KEY_NAME+ appWidgetId, textName);
        editor.putString(WIDGET_KEY_TYPE + appWidgetId, textType);
        editor.putString(WIDGET_KEY_TITLE + appWidgetId, textTitle);
        editor.commit();
        setResult(RESULT_OK, resultValue);
        Log.d(TAG, "finish config " + appWidgetId);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        AppWidgetOne.updateAppWidget(this, appWidgetManager, appWidgetId, settings);

        Intent service = new Intent(ConfigWidget.this, AppMqttService.class);
        service.putExtra("status", "widget");
        service.putExtra("textName", textName);
        service.putExtra("textText", textText);
        service.putExtra("textTitle", textTitle);
        service.putExtra("textType", textType);

        startService(service);
    }
}
