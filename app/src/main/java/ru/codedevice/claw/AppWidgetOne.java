package ru.codedevice.claw;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;
import android.widget.RemoteViews;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Implementation of App Widget functionality.
 */
public class AppWidgetOne extends AppWidgetProvider {

    static String TAG = "AppWidgetOne";
    public static String ACTION_WIDGET_RECEIVER = "ActionReceiverWidget";
    static RemoteViews views;

    static JSONObject allWidget = new JSONObject();
    static JSONObject tempWidget = new JSONObject();

    public static Bitmap BuildUpdate(String time, int size , Context context){
        Paint paint = new Paint();
        paint.setTextSize(size);
        Typeface ourCustomTypeface = Typeface.createFromAsset(context.getAssets(),"fonts/Lato-Light.ttf");
//        Typeface ourCustomTypeface = Typeface.createFromAsset(context.getAssets(),"fonts/BPdotsPlusBold.otf");
        paint.setTypeface(ourCustomTypeface);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setSubpixelText(true);
        paint.setAntiAlias(true);
        float baseline = -paint.ascent();
        int width = (int) (paint.measureText(time)+0.5f);
        int height = (int) (baseline + paint.descent()+0.5f);
        Bitmap image = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(image);
        canvas.drawText(time,0,baseline,paint);
        return image;

    }

    public static Bitmap BuildUpdateButton(String time, int size , Context context){
        Paint paint = new Paint();
        paint.setTextSize(size);
        Typeface ourCustomTypeface = Typeface.createFromAsset(context.getAssets(),"fonts/Lato-Light.ttf");
        paint.setTypeface(ourCustomTypeface);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setSubpixelText(true);
        paint.setAntiAlias(true);
        float baseline = -paint.ascent();
        int width = (int) (paint.measureText(time)+0.5f);
        int height = (int) (baseline + paint.descent()+0.5f);
        Bitmap image = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(image);
        canvas.drawText(time,0,baseline,paint);
        return image;
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, SharedPreferences sp) {
        Log.i(TAG, "updateAppWidget");
        String widgetText = "";
        String widgetType = "";
        String widgetName = "";
        String widgetTitle = "";
        String widgetValue = "false";

        widgetText = sp.getString(ConfigWidget.WIDGET_KEY_TEXT + appWidgetId, widgetText);
        widgetType = sp.getString(ConfigWidget.WIDGET_KEY_TYPE + appWidgetId, widgetType);
        widgetName = sp.getString(ConfigWidget.WIDGET_KEY_NAME + appWidgetId, widgetName);
        widgetTitle = sp.getString(ConfigWidget.WIDGET_KEY_TITLE + appWidgetId, widgetTitle);
        widgetValue = sp.getString(ConfigWidget.WIDGET_KEY_VALUE + appWidgetId, widgetValue);

//        Log.e(TAG, "AppWidgetOne widgetText "+widgetText);
//        Log.e(TAG, "AppWidgetOne widgetType "+widgetType);
//        Log.e(TAG, "AppWidgetOne widgetName "+widgetName);
//        Log.e(TAG, "AppWidgetOne widgetTitle "+widgetTitle);
        Log.e(TAG, "AppWidgetOne widgetValue "+widgetValue);

        if (widgetName.equals("")) return;

        try {
            tempWidget.put("ID",appWidgetId);
            tempWidget.put("NAME",widgetName);
            tempWidget.put("VALUE",widgetValue);
            tempWidget.put("TITLE",widgetTitle);
            tempWidget.put("TEXT",widgetText);
            tempWidget.put("TYPE",widgetType);
            allWidget.put(widgetName,tempWidget);
            tempWidget=null;
            tempWidget = new JSONObject();
            Log.e(TAG, "widgetName "+widgetName);
            Log.e(TAG, "allWidget "+allWidget);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Intent active = new Intent(context, AppReceiver.class);
        active.setAction(ACTION_WIDGET_RECEIVER);
        active.putExtra("widgetId", String.valueOf(appWidgetId));
        active.putExtra("widgetName", widgetName);
        active.putExtra("widgetValue", widgetValue);
        active.putExtra("widgetType", widgetType);
        PendingIntent actionPendingIntent = PendingIntent.getBroadcast(context, appWidgetId, active, 0);

        if(widgetType.equals(ConfigWidget.WIDGET_TYPE_TEXT_AND_TITLE)) {
            views = new RemoteViews(context.getPackageName(), R.layout.app_widget_text_and_title);
            views.setImageViewBitmap(R.id.image_data, BuildUpdate(widgetText, 100, context));
            views.setImageViewBitmap(R.id.image_title, BuildUpdate(widgetTitle, 50, context));
            views.setOnClickPendingIntent(R.id.image_data, actionPendingIntent);
        }

        if(widgetType.equals(ConfigWidget.WIDGET_TYPE_BUTTON)) {


            views = new RemoteViews(context.getPackageName(), R.layout.app_widget_button);
            views.setImageViewBitmap(R.id.image_title, BuildUpdateButton(widgetTitle, 100, context));
//            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.button_off);
            if(widgetValue.equals("false")){
                views.setImageViewResource(R.id.image_button,R.drawable.button_on);
            }else{
                views.setImageViewResource(R.id.image_button,R.drawable.button_off);
            }
            views.setOnClickPendingIntent(R.id.image_button, actionPendingIntent);
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(TAG, "onUpdate");
        SharedPreferences sp = context.getSharedPreferences(
                ConfigWidget.WIDGET_PREF, Context.MODE_PRIVATE);
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, sp);
        }
    }

    @Override
    public void onEnabled(Context context) {
        Log.i(TAG, "onEnabled");
    }

    @Override
    public void onDisabled(Context context) {
        Log.i(TAG, "onDisabled");
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        Log.d(TAG, "onDeleted " + Arrays.toString(appWidgetIds));
        for (int appWidgetId : appWidgetIds) {
            delWidgetJSON(String.valueOf(appWidgetId));
        }
        // Удаляем Preferences
        SharedPreferences.Editor editor = context.getSharedPreferences(
                ConfigWidget.WIDGET_PREF, Context.MODE_PRIVATE).edit();
        for (int widgetID : appWidgetIds) {
            editor.remove(ConfigWidget.WIDGET_KEY_TEXT + widgetID);
            editor.remove(ConfigWidget.WIDGET_KEY_TITLE + widgetID);
            editor.remove(ConfigWidget.WIDGET_KEY_TYPE + widgetID);
            editor.remove(ConfigWidget.WIDGET_KEY_NAME + widgetID);
            editor.remove(ConfigWidget.WIDGET_KEY_VALUE + widgetID);
        }
        editor.commit();
    }

    public void delWidgetJSON(String id){
        JSONObject widgetName;
        String widgetKey = null;
        Log.d(TAG, "Start del widget"+id);
        if (allWidget.length()>0){
            Iterator<String> keysJSON = allWidget.keys();
            while(keysJSON.hasNext()) {
                String key = keysJSON.next();
                try {
                    widgetName = allWidget.getJSONObject(key);
                    String widgetId = widgetName.getString("ID");
                    Log.d(TAG, "widgetId " + widgetId);

                    if(id.equals(widgetId)){
                        widgetKey = key;
                        Log.d(TAG, "del widgetId " + widgetId);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            allWidget.remove(widgetKey);
        }
    }
}

