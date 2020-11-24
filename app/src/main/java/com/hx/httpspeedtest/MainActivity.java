package com.hx.httpspeedtest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.os.Bundle;
import android.os.GXTServiceManager;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements HttpDownloader.DownloadNotifier {

    private final static String TAG = "hst_main";
    private final static String PREF_KEY_URL = "url";
    private final static String PREF_KEY_DOWNLOAD_TO_FILE = "download_to_file";
    private final static String PREF_KEY_REPEAT = "repeat";
    private final static String PREF_URL_LIST = "url_list";

    private final static int MSG_START_DOWNLOAD = 1024;
    private final static int MSG_STOP_DOWNLOAD = MSG_START_DOWNLOAD + 1;
    private final static int MSG_DATA_TIMEOUT = MSG_START_DOWNLOAD + 2;
    private final static int AUTO_DOWNLOAD_DELAY = 5;

    private final static int MAX_FAIL_COUNT = 10;
    private final static int MAX_LINE_COUNT = 500;


    private final static String DEF_DOWNLOAD_FILE_PATH = "/sdcard/download/hst.tmp";

    private final static int PROGRESS_UPDATE_INTERVAL_MS = 300;

    private final static String[] DEF_URLS = {
        "http://192.168.0.6/1",
        "http://192.168.2.5/TEST/1080P.ts",
        "http://192.168.1.14/Media/1080p/THTBOTFA_1080.mov",
    };

    @SuppressLint("StaticFieldLeak")
    public static MainActivity sInstance = null;

    //UI elements
    public static AutoCompleteTextView mEditUrl;
    private EditText mEditLog;
    public static Button mBtnStartDownload;

    private CheckBox mChkPerformanceModel;
    private CheckBox mChkDownloadToFile;
    public static CheckBox mChkRepeat;
    private TextView mTxtStatus;
    private Button mBtnClearLog;

    private SharedPreferences mSharedPref;
    private HttpDownloader mDownloader;
    private List<String> mUrlList;
    private ArrayAdapter<String> mUrlListAdapter;
    
    private long mLastCheckPos = 0;
    private long mCurrentSpd = 0;
    private long mFileSize = 0;

    private long mLastProgressTime = 0;

    private MsgHandler mHandler;

    private boolean mDownloading;

    private ConnectivityHelper mHelper;
    private boolean mConnected;

    private int mFailCount;

    private LogFile mLogFile;

    private long mLastDisconnectedTime;
    private int mDisconnectedCount;

    public static Context mContext;

    List <Long> aveSpeed = new ArrayList<>();
    private int cmpltedCount = 0;

    //change cpu freq mode
    private static final String CPU_FREQ_INTERACTICE_MODE = "interactive";
    private static final String CPU_FREQ_PERFORMANCE_MODE = "performance";

    private static final String CPU_FREQ_MODE_SYS_PATH =
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;

        sInstance = this;

        mHandler = new MsgHandler();

        mDownloading = !GXTServiceManager.isGXTProduct();//should be false generally;
        mFailCount = 0;

        mEditUrl = (AutoCompleteTextView) findViewById(R.id.edit_url);
        mEditLog = (EditText) findViewById(R.id.edit_log);
        mBtnStartDownload = (Button) findViewById(R.id.btn_start_download);
        if (GXTServiceManager.isGXTProduct()) {
            mChkDownloadToFile = (CheckBox) findViewById(R.id.check_download_to_file);
        }
        mChkPerformanceModel = findViewById(R.id.check_performance_model);
        mChkRepeat = findViewById(R.id.check_repeat);
        mTxtStatus = (TextView) findViewById(R.id.txt_status);
        mBtnClearLog = findViewById(R.id.btn_clear_log);

        mChkPerformanceModel.setOnClickListener(v -> {

            if (mChkPerformanceModel.isChecked()){
                //Log.d(TAG,"mChkPerformanceModel Checked");

                writeSysNode(CPU_FREQ_PERFORMANCE_MODE, CPU_FREQ_MODE_SYS_PATH);
            }
            else {
                //Log.d(TAG,"mChkPerformanceModel not Checked");

                writeSysNode(CPU_FREQ_INTERACTICE_MODE, CPU_FREQ_MODE_SYS_PATH);
            }
        });

        mBtnStartDownload.setOnClickListener((v)-> {
            mFailCount = 0;
            if (mDownloading
                && mDownloader != null
                && mDownloader.isDownloading()) {
                Log.i(TAG, "Cancelling in progress downloading...");
                mDownloader.cancel(100);
            }
            setDownloading(!mDownloading);
            Log.i(TAG, "Start/Stop button clicked! downloading:" + mDownloading);
            if (mDownloading) {
                mHandler.removeMessages(MSG_STOP_DOWNLOAD);
                mHandler.sendEmptyMessage(MSG_START_DOWNLOAD);
            } else {
                mHandler.removeMessages(MSG_START_DOWNLOAD);
                mHandler.sendEmptyMessage(MSG_STOP_DOWNLOAD);
            }
        });

        mBtnClearLog.setOnClickListener((v)->{
            mEditLog.setText("");
        });

        mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        Set<String> prefUrls = mSharedPref.getStringSet(PREF_URL_LIST, null);
        String url = mSharedPref.getString(PREF_KEY_URL, "");

        if (prefUrls != null) {
            mUrlList = new ArrayList<>(prefUrls);
        } else {
            //List returned by asList doesn't support add function,
            //need to create a normal copy.
            mUrlList = new ArrayList<>(Arrays.asList(DEF_URLS));
        }
        
        if (url.length() > 0) {
            mEditUrl.setText(url);

            //if (MainActivity.mBtnStartDownload.getText()
            //        .equals(mContext.getString(R.string.stop))){//Start

                //mBtnStartDownload.performClick();
            //}

            if (!mUrlList.contains(url)) {
                mUrlList.add(url);
            }
        } else {
            mEditUrl.setText(mUrlList.get(0));
        }

        mUrlListAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, mUrlList);
        mEditUrl.setAdapter(mUrlListAdapter);

        boolean downloadToFile = mSharedPref.getBoolean(PREF_KEY_DOWNLOAD_TO_FILE, false);
        mChkDownloadToFile.setChecked(downloadToFile);

        boolean repeat = mSharedPref.getBoolean(PREF_KEY_REPEAT, true);
        mChkRepeat.setChecked(repeat);

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }

        mConnected = false;
        mHelper = new ConnectivityHelper(this, mCallback);
        mHelper.init();

        mLastDisconnectedTime = -1;
        mDisconnectedCount = 0;


        mLogFile = new LogFile();
        if (!mLogFile.open()) {
            Log.i(TAG, "Failed to open log file!");
            log(R.string.log_file_open_failed);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            mChkDownloadToFile.setChecked(false);
            mChkDownloadToFile.setEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mBtnStartDownload.requestFocus();
    }

    @Override
    public void onDestroy() {
        //store configurations {
        String url = mEditUrl.getText().toString();

        if (url.length() > 0) {
            mSharedPref.edit().putBoolean(PREF_KEY_DOWNLOAD_TO_FILE, mChkDownloadToFile.isChecked());
            mSharedPref.edit().putString(PREF_KEY_URL, url).commit();
        }

        Set<String> urlSet = new HashSet<>(mUrlList);
        mSharedPref.edit().remove(PREF_URL_LIST).apply();
        mSharedPref.edit().putStringSet(PREF_URL_LIST, urlSet).commit();

        mSharedPref.edit().putBoolean(PREF_KEY_REPEAT, mChkRepeat.isChecked());

        //}
        if (mDownloader != null
            && mDownloader.isDownloading()) {
            mDownloader.cancel(500);
        }

        mHelper.uninit();

        writeSysNode(CPU_FREQ_INTERACTICE_MODE, CPU_FREQ_MODE_SYS_PATH);

        mLogFile.close();

        sInstance = null;

        super.onDestroy();
    }

    public static boolean isActivityExist(){

        MainActivity activity = sInstance;

        if (activity == null){
            return false;
        }

        return !activity.isFinishing() && !activity.isDestroyed();
    }

    private void log(String msg) {
        Calendar now = Calendar.getInstance();
        String hour = String.format("%02d", now.get(Calendar.HOUR_OF_DAY));
        String minute = String.format("%02d", now.get(Calendar.MINUTE));
        String seconds = String.format("%02d", now.get(Calendar.SECOND));
        String log = hour + ":"
                + minute + ":"
                + seconds + " "
                + msg + "\n";

        if (mEditLog.getLineCount() > MAX_LINE_COUNT) {
            mEditLog.getText().delete(0,
            mEditLog.getText().length() / 2);
        }

        mEditLog.append(log);

        mLogFile.write(log);
    }

    private void log(int resId) {
        log(getString(resId));
    }

    @Override
    public void onBackPressed() {
        if (mDownloading) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            //Uncomment the below code to Set the message and title from the strings.xml file
            builder.setMessage(R.string.exit_when_downloading)
                    .setTitle(R.string.confirm)
                    .setPositiveButton(R.string.yes, (dialog, id)-> {
                        finish();
                    })
                    .setNegativeButton(R.string.no, (dialog, id)-> {
                        dialog.cancel();
                    });

            AlertDialog alert = builder.create();
            alert.show();
        } else {
            finish();
        }
    }

    private void setDownloading(boolean downloading) {
        mDownloading = downloading;
        if (downloading) {
            mBtnStartDownload.setText(R.string.stop);
        } else {
            mBtnStartDownload.setText(R.string.start);
        }
    }

    private boolean onStartDownload() {
        String url = mEditUrl.getText().toString();

        if (url.length() > 0 && !mUrlList.contains(url)) {
            mUrlList.add(url);
            mUrlListAdapter.add(url);
        }


        String filePath = null;
        if (mChkDownloadToFile.isChecked()) {
            filePath = DEF_DOWNLOAD_FILE_PATH;
        }

        mDownloader = new HttpDownloader(url, filePath, this);
        mDownloader.start();

        mChkDownloadToFile.setEnabled(false);

        log(R.string.log_connecting);

        return true;
    }

    public class MsgHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_START_DOWNLOAD:
                    removeMessages(MSG_START_DOWNLOAD);
                    onStartDownload();
                    break;
                case MSG_STOP_DOWNLOAD:

                    if (mDownloader != null) {
                        mDownloader.cancel(500);
                    }

                    long speed = 0;
                    if (!aveSpeed.isEmpty()){
                        Collections.sort(aveSpeed);
                        if (aveSpeed.size() == 2){
                            for (int i = 0;i < aveSpeed.size();i++){
                                //Log.d(TAG,"aveSpeed: "+aveSpeed.get(i));
                                speed += aveSpeed.get(i);
                            }
                            speed /= aveSpeed.size();
                            log(logResult(aveSpeed.size(),(int) speed));
                        } else if (aveSpeed.size() >= 3){

                            aveSpeed.remove(0);
                            aveSpeed.remove(aveSpeed.size() - 1);

//                        Log.d(TAG,"aveSpeed: "+aveSpeed
//                                +"smallest: "+smallest
//                                +"largest: "+largest);
                            //Log.d(TAG,"aveSpeed.size: "+aveSpeed.size());
                            for (int i = 0;i < aveSpeed.size();i++){
                                //Log.d(TAG,"aveSpeed: "+aveSpeed.get(i));
                                speed += aveSpeed.get(i);
                            }

                            //Log.d(TAG,"speed: "+speed);
                            cmpltedCount -= 2;
                            //Log.d(TAG,"cmpltedCount: "+cmpltedCount);
                            speed  /= cmpltedCount;
                            //Log.d(TAG,"speed: "+speed);

                            log(logResult(cmpltedCount+2,(int) speed));
                            //aveSpeed.clear();
                            cmpltedCount = 0;
                        }
                    }

                    aveSpeed.clear();
                    mLogFile.flush();
                    break;
                case MSG_DATA_TIMEOUT:
                    onProgress(null, mLastCheckPos, mFileSize);
                    break;
                default:
                    break;
            }
        }
    }

    String logResult(int count,int speed){

        StringBuilder result = new StringBuilder();

        String msgCount = getString(
                R.string.log_test_completed_count,(int) (count));

        String msgSpeed = getString(
                R.string.log_test_completed_speed,(int) (speed));

        return result.append(msgCount).append(msgSpeed).toString();
    }

    @Override
    public void onError(final int code, final Object para) {
        runOnUiThread(() -> {
            String msg = "";
            switch (code) {
                case HttpDownloader.ERROR_CODE_EXCEPTION:
                    msg = getString(R.string.download_error_exception);
                    msg += "\n";
                    msg += ((Exception) para).getMessage() + "\n";
                    break;
                case HttpDownloader.ERROR_CODE_HTTP_RESPONSE:
                    msg = getString(R.string.download_error_response);
                    msg += (int) para + "\n";
                    break;
                case HttpDownloader.ERROR_CODE_USER_CANCEL:
                    msg = getString(R.string.download_error_usercancel);
                    msg += "\n";
                    break;
                default:
                    break;
            }

            log(msg);

            mTxtStatus.setText(R.string.status_error);

            if (code != HttpDownloader.ERROR_CODE_USER_CANCEL) {
                mFailCount++;
                if (mChkRepeat.isChecked()
                    && mDownloading) {
                    if (mFailCount < MAX_FAIL_COUNT) {
                        log(getString(R.string.redownload_delay, AUTO_DOWNLOAD_DELAY));
                        mHandler.sendEmptyMessageDelayed(MSG_START_DOWNLOAD, AUTO_DOWNLOAD_DELAY * 1000);
                    } else {
                        log(getString(R.string.exceed_max_fail_count));
                    }
                }
            } else {
                setDownloading(false);
            }

            mChkDownloadToFile.setEnabled(true);
        });
    }

    @Override
    public void onProgress(final String url,
                           final long currentPos, final long totalSize) {
        mFileSize = totalSize;
        mHandler.removeMessages(MSG_DATA_TIMEOUT);
        if (currentPos == 0) {
            mLastCheckPos = 0;
            runOnUiThread(() -> {
                log(R.string.log_download_start);
            });
        }

        long currentTime = System.currentTimeMillis();
        if (currentPos < totalSize) {
            if (currentTime - mLastProgressTime < PROGRESS_UPDATE_INTERVAL_MS) {
                return;
            }
        }

        long diff = currentTime - mLastProgressTime;
        mCurrentSpd = (currentPos - mLastCheckPos) * 1000 / diff;
        mLastCheckPos = currentPos;

        mLastProgressTime = currentTime;
        final String msg = getString(R.string.status_downloading,
                (int) (mCurrentSpd) / 1024,
                (int) (currentPos / 1024),
                (int) (totalSize / 1024));

        runOnUiThread(() -> {
            mTxtStatus.setText(msg);
        });

        mHandler.sendEmptyMessageDelayed(MSG_DATA_TIMEOUT, PROGRESS_UPDATE_INTERVAL_MS * 4);
    }

    @Override
    public void onCompleted(final long startTime, final long endTime, final long size) {
        runOnUiThread(() -> {
            mChkDownloadToFile.setEnabled(true);

            long diff = (endTime - startTime);
            long bps = size * 1000 / diff;

            String msg = getString(R.string.log_download_completed, (int) (bps / 1024));
            log(msg);
            bps /= 1024;
            //Log.d(TAG,"bps: "+bps);
            aveSpeed.add(bps);
            cmpltedCount++;
            //Log.d(TAG,"cmpltedCount++: "+cmpltedCount);

            mTxtStatus.setText(getString(R.string.status_completed));

            if (mChkRepeat.isChecked()) {
                if (mConnected) {
                    Log.i(TAG, "redownload...");
                    mHandler.sendEmptyMessageDelayed(MSG_START_DOWNLOAD, 1000);
                }
            } else {
                setDownloading(false);
            }
        });

    }

    NetworkCallback mCallback = new NetworkCallback() {
        public void onAvailable(Network network) {
            Log.i(TAG, "Network available:" + network);
            mConnected = GXTServiceManager.isPackageInstalled(MainActivity.this,
                    getPackageName());  //should be always true generally for HX products
            mFailCount = 0;
            runOnUiThread(()-> {
                mBtnStartDownload.setEnabled(true);

                String networkName = mHelper.getActiveNetworkName();
                if (mLastDisconnectedTime == -1) {
                    log(getString(R.string.network_connected, networkName));
                } else {
                    int diff = (int)(System.currentTimeMillis() - mLastDisconnectedTime ) / 1000;
                    log(getString(R.string.network_reconnected, networkName, diff));
                }

                if (mChkRepeat.isChecked() && mDownloading) {
                    Log.i(TAG, "Auto start download...");
                    mHandler.sendEmptyMessageDelayed(MSG_START_DOWNLOAD, 1000);
                }
            });
        }

        public void onLost(Network network) {
            Log.i(TAG, "Network lost:" + network);
            runOnUiThread(()-> {
                mBtnStartDownload.setEnabled(false);
                mDisconnectedCount++;
                log(getString(R.string.network_disconnected, mDisconnectedCount));
                mConnected = false;
                mLastDisconnectedTime = System.currentTimeMillis();
            });
        }
    };

    //change cpu freq

    private void echoSysNode(String pref,String sys_path){

        Process p = null;
        DataOutputStream os = null;
        try {
            p = Runtime.getRuntime().exec("sh");
            os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("echo "+ pref + " > "+ sys_path + "\n");
            os.writeBytes("exit\n");
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(MainActivity.TAG, " can't write " + sys_path + e.getMessage());
        } finally {
            if(p != null)  { p.destroy(); }
            if(os != null){
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        Log.d(MainActivity.TAG, " writed " + sys_path );
    }

    private void writeSysNode(String pref,String sys_path){

        try {
            BufferedWriter bufWriter = null;
            bufWriter = new BufferedWriter(new FileWriter(sys_path));
            bufWriter.write(pref);
            bufWriter.close();

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG,"can't write the " + sys_path);
            Toast.makeText(this,
                    getResources().getString(R.string.fail_write_sys_file)
                    ,Toast.LENGTH_LONG).show();
        }
    }

}


