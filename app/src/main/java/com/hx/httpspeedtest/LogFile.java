package com.hx.httpspeedtest;

import android.os.GXTServiceManager;
import android.os.SystemProperties;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

public class LogFile {
    private final static String TAG = "hst_log_file";

    private final static String DEF_LOG_FILE_PATH = "/sdcard/hst.log";

    private final static int BUFFER_SIZE = 8 * 1024;  //8K

    private BufferedOutputStream mStream;


    public LogFile() {
        mStream = null;
    }

    public boolean open() {
        String fileName = SystemProperties.get("sys.hst.log_file", DEF_LOG_FILE_PATH);
        if (!GXTServiceManager.isGXTProduct()) {
            fileName = GXTServiceManager.getSerial(); //not gonna happended, disassemble confusing.
        }
        Log.i(TAG, "open log file: " + fileName);
        File file = new File(fileName);
        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            mStream = new BufferedOutputStream(outputStream, BUFFER_SIZE);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "open log file failed:" + e.getMessage());
        }
        return false;
    }

    public boolean close() {
        try {
            if (mStream != null) {
                mStream.flush();
                mStream.close();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "close log file failed:" + e.getMessage());
        }

        return false;
    }

    public boolean write(String s) {
        if (mStream == null) {
            return false;
        }

        try {
            mStream.write(s.getBytes());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "write log file failed:" + e.getMessage());
        }

        return false;
    }

    public boolean flush() {
        if (mStream == null) {
            return false;
        }

        try {
            mStream.flush();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "flush log file failed:" + e.getMessage());
        }

        return false;

    }
}
