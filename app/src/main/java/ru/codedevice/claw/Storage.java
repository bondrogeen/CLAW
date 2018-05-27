package ru.codedevice.claw;

import android.os.Environment;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class Storage {
    static final String TAG = "Storage";
    static String path = Environment.getExternalStorageDirectory() + "/MQTTAndroidControl";



    public void Seve() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
//            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
//            }
//        }
    }

    private static File create(String dir){
        File dirDirectory = null;
        if (isExternalStorageWritable()){
            Log.v(TAG, "isExternalStorageWritable");
            File appDirectory = new File(path);
            dirDirectory = new File(appDirectory + "/"+dir);
            if (!appDirectory.exists()) {
                appDirectory.mkdir();
                Log.v(TAG, "create app folder");
            }
            if (!dirDirectory.exists()) {
                dirDirectory.mkdir();
                Log.v(TAG, "create log folder");
            }
            return dirDirectory;
        }
        return null;
    }

    public static void put(String key, JSONObject obj){
        File dir = create("json");
        if (dir!=null){
            File file = new File(dir, key+".json");
            writeFile(file,obj.toString());
//        JSONObject jsonObj = new JSONObject("{\"phonetype\":\"N95\",\"cat\":\"WP\"}");
        }


    }

    public static JSONObject get(String key){
        File file = new File(new File(path+"/json"), key+".json");
        JSONObject jsonObj = null;
        if(file.exists()) {
            Log.v(TAG, "file.exists()");
            try {
                jsonObj = new JSONObject(readFile(file));
                Log.v(TAG, "jsonObj"+jsonObj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return jsonObj;
    }

    private static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

//    private static boolean isExternalStorageReadable() {
//        String state = Environment.getExternalStorageState();
//        if ( Environment.MEDIA_MOUNTED.equals( state ) ||
//                Environment.MEDIA_MOUNTED_READ_ONLY.equals( state ) ) {
//            return true;
//        }
//        return false;
//    }

    private static void writeFile(File file, String buf) {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(file));
            bw.write(buf);
            bw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String readFile(File file) {
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            while ((line = br.readLine()) != null) {
                stringBuilder.append(line);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return String.valueOf(stringBuilder);
    }
}
