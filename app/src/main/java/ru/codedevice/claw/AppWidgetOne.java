package ru.codedevice.claw;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.Arrays;

/**
 * Implementation of App Widget functionality.
 */
public class AppWidgetOne extends AppWidgetProvider {

    String TAG = "AppWidgetOne";
    public static String ACTION_WIDGET_RECEIVER = "ActionReceiverWidget";

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

    public void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, SharedPreferences sp) {

        String widgetText = sp.getString(ConfigWidget.WIDGET_TEXT + appWidgetId, "--");
        String widgetType = sp.getString(ConfigWidget.WIDGET_TYPE + appWidgetId, "text");
        String widgetName = sp.getString(ConfigWidget.WIDGET_NAME + appWidgetId, "");

//        if (widgetText == null) return;

        Log.i(TAG, String.valueOf(appWidgetId));
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.app_widget_one);

        if(widgetType.equals("text")) {
//            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.app_widget_one);
            views.setImageViewBitmap(R.id.image_time, BuildUpdate(widgetText, 100, context));
            views.setImageViewBitmap(R.id.image_date, BuildUpdate("Время", 50, context));
//        views.setTextViewText(R.id.appwidget_text, widgetText);

            Intent active = new Intent(context, AppReceiver.class);
            active.setAction(ACTION_WIDGET_RECEIVER);
            active.putExtra("id", String.valueOf(appWidgetId));
            active.putExtra("name", widgetName);
            PendingIntent actionPendingIntent = PendingIntent.getBroadcast(context, 0, active, 0);
            views.setOnClickPendingIntent(R.id.image_time, actionPendingIntent);
        }
//        Intent configIntent = new Intent(context, ConfigWidget.class);
//        configIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
//        configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
//        PendingIntent pIntent = PendingIntent.getActivity(context, appWidgetId, configIntent, 0);
//        views.setOnClickPendingIntent(R.id.image_time, pIntent);



        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(TAG, "onUpdate");
        Log.i(TAG, "appWidgetIds"+ Arrays.toString(appWidgetIds));
        SharedPreferences sp = context.getSharedPreferences(
                ConfigWidget.WIDGET_PREF, Context.MODE_PRIVATE);
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, sp);
        }

    }

    @Override
    public void onEnabled(Context context) {
        Log.i(TAG, "onEnabled");
        // Enter relevant functionality for when the first widget is created

    }

    @Override
    public void onDisabled(Context context) {
        Log.i(TAG, "onDisabled");
        // Enter relevant functionality for when the last widget is disabled
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        Log.d(TAG, "onDeleted " + Arrays.toString(appWidgetIds));
        // Удаляем Preferences
        SharedPreferences.Editor editor = context.getSharedPreferences(
                ConfigWidget.WIDGET_PREF, Context.MODE_PRIVATE).edit();
        for (int widgetID : appWidgetIds) {
            editor.remove(ConfigWidget.WIDGET_TEXT + widgetID);
            editor.remove(ConfigWidget.WIDGET_COLOR + widgetID);
        }
        editor.commit();
    }
}

