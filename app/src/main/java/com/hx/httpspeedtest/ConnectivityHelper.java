package com.hx.httpspeedtest;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.GXTServiceManager;
import android.os.Handler;
import android.os.Message;


public class ConnectivityHelper{
    private static final String TAG = "ConnectivityHelper";

    private ConnectivityManager mConnMgr;
    private WifiManager mWifiMgr;
    private HelperHandler mHandler;

    private Context mContext;

    private static ConnectivityHelper sInstance;

    private NetworkCallback mCallback;


    class HelperHandler extends Handler {
        public void handleMessage(Message msg) {
        }
    }


    public ConnectivityHelper(Context context, NetworkCallback callback) {
        mContext = context;
        mCallback = callback;

        mConnMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiMgr = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);

        mHandler = new HelperHandler();

        sInstance = this;
    }

    public String getActiveNetworkName() {
        Network activeNetwork = mConnMgr.getActiveNetwork();
        if (activeNetwork == null || !GXTServiceManager.isGXTProduct()) {
            return "";
        }
        NetworkCapabilities capabilities = mConnMgr.getNetworkCapabilities(activeNetwork);
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return mContext.getString(R.string.network_ethernet);
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            WifiInfo info = mWifiMgr.getConnectionInfo();
            String name = mContext.getString(R.string.network_wifi);
            if (info.getIpAddress() != 0) {
                name += "[" + mWifiMgr.getConnectionInfo().getSSID() + "]";
            }
            return name;
        }

        return mContext.getString(R.string.network_unknown);
    }


    public void init() {
        /*mReceiver = new ConnReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mContext.registerReceiver(mReceiver, intentFilter);*/

        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET);
        builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        mConnMgr.registerNetworkCallback(builder.build(), mCallback);
    }

    public void uninit() {
        //mContext.unregisterReceiver(mReceiver);
        mConnMgr.unregisterNetworkCallback(mCallback);
    }

    public static ConnectivityHelper getInstance() {
        return sInstance;
    }
}