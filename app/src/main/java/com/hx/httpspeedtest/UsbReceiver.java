package com.hx.httpspeedtest;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class UsbReceiver extends BroadcastReceiver {

    public static final String TAG = "UsbReceiver";

    private boolean DEBUG = true;

    public static String mountPath = null;

    SharedPreferences mSharedPref;

    private final static String PREF_KEY_URL = "url";

    public UsbReceiver() {
        super();

        //mSharedPref = PreferenceManager.getDefaultSharedPreferences(MainActivity.mContext);
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        if (DEBUG) Log.i(TAG,"onReceive: "+intent);
        if (action.equals(Intent.ACTION_MEDIA_MOUNTED)){

            mountPath = intent.getData().getPath();
            if (mountPath.equals("/storage/emulated/0")){
                if (DEBUG) Log.d(TAG, "mountPath = " + "/storage/emulated/0");
                return;
            }
            if (DEBUG) Log.d(TAG, "mountPath = " + mountPath);

            if (!MainActivity.isActivityExist()){
                return;
            }

            String uri = readFile(mountPath);
            if (DEBUG) Log.d(TAG,"uri: "+uri);
            if (uri != null && !uri.equals("")){

                mSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = mSharedPref.edit();
                editor.putString(PREF_KEY_URL,uri);
                editor.apply();

                if (isMainActivityAlive(context,MainActivity.class.getName())){
                    MainActivity.mEditUrl.setText(uri);
                    MainActivity.mChkRepeat.setChecked(true);
                    if (DEBUG) Log.d(TAG,"mBtnStartDownload.getText(): "+
                            MainActivity.mBtnStartDownload.getText());
                    if (MainActivity.mBtnStartDownload.getText()
                            .equals(context.getString(R.string.start))){//Start
                        if (DEBUG) Log.d(TAG,"开始");
                        MainActivity.mBtnStartDownload.performClick();
                    }else {
                        if (DEBUG) Log.d(TAG,"停止");
                        MainActivity.mBtnStartDownload.performClick();
                        MainActivity.mBtnStartDownload.performClick();
                    }

                }else {
                    Intent intentVideo = new Intent();
                    intentVideo.setClass(context, MainActivity.class);
                    intentVideo.addFlags(FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);

                    context.startActivity(intentVideo);
                }
            }else {
                Toast.makeText(context,"解析链接文件失败",Toast.LENGTH_LONG).show();
            }
        }else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)
                || action.equals(Intent.ACTION_MEDIA_EJECT)){

            if (!MainActivity.isActivityExist()){
                return;
            }

            if (isMainActivityAlive(context,MainActivity.class.getName())){
                if ( MainActivity.mBtnStartDownload.getText() != null
                        && MainActivity.mBtnStartDownload.getText()
                        .equals(context.getString(R.string.stop))){
                    if (DEBUG) Log.d(TAG,"stop");
                    MainActivity.mBtnStartDownload.performClick();
                }
            }
        }else if (action.equals(intent.ACTION_BOOT_COMPLETED)){

            mountPath = intent.getData().getPath();

            if (mountPath.equals("/storage/emulated/0")){
                return;
            }

//            IntentFilter usbFilter= new IntentFilter();
//            usbFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
//            usbFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
//            usbFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
//            usbFilter.addDataScheme("file");
//
//            context.getApplicationContext().registerReceiver(this,usbFilter);

        }


    }
    private String readFile(String mountPath){

        String usbPath = mountPath;//UsbReceiver.mountPath;

        String mnt_usb_path = null;

        String filePath = null;

        File parentDir;
        File[] files;

        File fileUri;
        String content = null;

        if (usbPath == null){

            mnt_usb_path = getUsbPath();

            if (mnt_usb_path != null){

                usbPath = mnt_usb_path;

            }
        }

        if (usbPath != null){

            usbPath += "/";

            parentDir = new File(usbPath);
            files = parentDir.listFiles();

            if (files != null){
                for (File file :files){

                    if (file.isDirectory()){
                        //filePath = null;
                    }else {
                        String dirsName = file.getName();
                        //String dirsPath = file.getPath();
                        if (dirsName.equals("HXSpeedTestUri.txt")) {
                            //return dirsPath;
                            String dirsPath = file.getPath();
                            if (DEBUG) Log.d(TAG,"dirsPath: "+dirsPath);
                            filePath = dirsPath;

                        }
                    }
                }
            }

            if (filePath != null){
                if (DEBUG) Log.d(TAG,"filePath: "+filePath);
                fileUri = new File(filePath);
                if (fileUri.exists()){
                    if (fileUri.getName().endsWith("txt")){
                        try {
                            InputStream instream = new FileInputStream(filePath);
                            if (instream != null) {
                                InputStreamReader inputreader
                                        = new InputStreamReader(instream, "UTF-8");
                                BufferedReader buffreader = new BufferedReader(inputreader);
                                String line = "";
                                //分行读取
                                while ((line = buffreader.readLine()) != null) {
                                    //content += line + "\n";
                                    content = line;
                                }
                                instream.close();
                            }

                        } catch (FileNotFoundException e1) {
                            if (DEBUG) Log.d(TAG,"FileNotFoundException");
                            content = null;
                            //e1.printStackTrace();
                        } catch (IOException e2) {
                            e2.printStackTrace();
                        }
                    }
                }
            }
        }
        return content;
    }

    private String getUsbPath() {

        String root = "/storage";

        String dirsPath = null;

        File parentDir = new File(root);

        File[] files = parentDir.listFiles();

        if (files == null) {
            if (DEBUG) Log.e(TAG, root + " No file listed in " + root);
            return null;
        }

        for (File file : files) {

            if (DEBUG) Log.i(TAG, root + " files: " + file.getName());

            if (file.isDirectory()) {
                String dirsName = file.getName();
                dirsPath = file.getPath();
                if (DEBUG) Log.i(TAG, root + " dirsName " + dirsName);
                if (DEBUG) Log.i(TAG, root + " dirsPath " + dirsPath);

                if (dirsName.equals("emulated") || dirsName.equals("self")) {

                } else {
                    if (DEBUG) Log.i(TAG, root + " dirsPath " + file.getPath());
                    dirsPath = file.getPath();

                    if (DEBUG) Log.i(TAG, root + " dirsName " + dirsName);

                    return dirsPath;
                }

            } else {

            }

        }
        return null;
    }

    private boolean isMainActivityAlive(Context context, String activityName){
        ActivityManager am = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> list = am.getRunningTasks(100);
        for (ActivityManager.RunningTaskInfo info : list) {

            //if (DEBUG) Log.i(TAG,info.topActivity.getClassName());// + " info.baseActivity.getPackageName()="+info.baseActivity.getPackageName());
            if (info.topActivity.getClassName().equals(activityName)) {
                //if (DEBUG) Log.i(TAG,info.topActivity.getPackageName() + " info.baseActivity.getPackageName()="+info.baseActivity.getPackageName());
                return true;
            }
        }
        return false;
    }

}
