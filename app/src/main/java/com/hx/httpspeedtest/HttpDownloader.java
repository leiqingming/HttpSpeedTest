package com.hx.httpspeedtest;

import android.util.Log;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpDownloader {
    private static final String TAG = "hst_downloader";
    private static final int BUFFER_SIZE = 8192;

    public static final int ERROR_CODE_HTTP_RESPONSE = -1;
    public static final int ERROR_CODE_EXCEPTION = -2;
    public static final int ERROR_CODE_USER_CANCEL = -3;


    private Thread mThread;
    private String mUrl;
    private String mFilePath;
    private DownloadNotifier mNotifier;
    private boolean mCancelling;

    public interface DownloadNotifier {
        void onError(final int code, final Object para);

        void onProgress(final String url,
            final long currentPos, final long totalSize);


        void onCompleted(final long startTime,
            final long endTime,
            final long fileSize);
    }

    public HttpDownloader(String url,
                          String destFile, DownloadNotifier notifier) {
        mUrl = url;
        mFilePath = destFile;

        mNotifier = notifier;
    }

    public void start() {
        mThread = new DownloadThread();
        mThread.start();
    }

    public boolean cancel(long timeout) {
        mCancelling = true;

        try {
            mThread.join(timeout);
            return true;
        } catch (Exception e) {
            Log.i(TAG, "Error waiting thread to exit");
            e.printStackTrace();
        }

        return false;
    }


    private class DownloadThread extends Thread {
        @Override
        public void run() {
            try {
                URL url = new URL(mUrl);
                HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
                int responseCode = httpConn.getResponseCode();

                // always check HTTP response code first
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    String fileName = "";
                    String disposition = httpConn.getHeaderField("Content-Disposition");
                    String contentType = httpConn.getContentType();
                    int contentLength = httpConn.getContentLength();

                    if (disposition != null) {
                        // extracts file name from header field
                        int index = disposition.indexOf("filename=");
                        if (index > 0) {
                            fileName = disposition.substring(index + 10,
                                    disposition.length() - 1);
                        }
                    } else {
                        // extracts file name from URL
                        fileName = mUrl.substring(mUrl.lastIndexOf("/") + 1,
                                mUrl.length());
                    }

                    System.out.println("Content-Type = " + contentType);
                    System.out.println("Content-Disposition = " + disposition);
                    System.out.println("Content-Length = " + contentLength);
                    System.out.println("fileName = " + fileName);

                    // opens input stream from the HTTP connection
                    InputStream inputStream = httpConn.getInputStream();


                    // opens an output stream to save into file
                    FileOutputStream outputStream = null;
                    if (mFilePath != null) {
                        outputStream = new FileOutputStream(mFilePath);
                    }

                    int bytesRead = -1;
                    long pos = 0;
                    long downloadStartTime = System.currentTimeMillis();
                    byte[] buffer = new byte[BUFFER_SIZE];
                    if (mNotifier != null) {
                        mNotifier.onProgress(mUrl, 0, contentLength);
                    }
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        if (outputStream != null) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        pos += bytesRead;
                        if (mCancelling) {
                            if (mNotifier != null) {
                                mNotifier.onError(ERROR_CODE_USER_CANCEL, null);
                                break;
                            }
                        }
                        if (mNotifier != null) {
                            mNotifier.onProgress(mUrl, pos, contentLength);
                        }
                    }

                    if (outputStream != null) {
                        outputStream.close();
                    }
                    inputStream.close();

                    if (pos >= contentLength) {
                        System.out.println("File downloaded");
                        if (mNotifier != null) {
                            mNotifier.onCompleted(downloadStartTime,
                                    System.currentTimeMillis(),
                                    contentLength);
                        }
                    }

                } else {
                    System.out.println("No file to download. Server replied HTTP code: " + responseCode);
                    if (mNotifier != null) {
                        mNotifier.onError(ERROR_CODE_HTTP_RESPONSE, responseCode);
                    }
                }
                httpConn.disconnect();
                Log.i(TAG, "http disconnected!");
            } catch (Exception e) {
                if (mNotifier != null) {
                    mNotifier.onError(ERROR_CODE_EXCEPTION, e);
                }
            }
        }
    }

    public boolean isDownloading() {
        return mThread.isAlive();
    }
}