package com.github.shadowsocks;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.constant.Action;

/**
 * Created by victor on 2017/4/11.
 */

public abstract class ServiceBoundContext extends Activity implements IBinder.DeathRecipient {
    private String TAG = "Activity";
    class ShadowsocksServiceConnection implements ServiceConnection {


        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.e(TAG, "onServiceConnected");
            binder = service;
            try {
                service.linkToDeath(ServiceBoundContext.this, 0);
                bgService = IShadowsocksService.Stub.asInterface(service);
                registerCallback();
                ServiceBoundContext.this.onServiceConnected();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            unregisterCallback();
            ServiceBoundContext.this.onServiceDisconnected();
            bgService = null;
            binder = null;
        }
    }

    protected void registerCallback() {
        if (bgService != null && callback != null && !callbackRegistered) {
            try {
                bgService.registerCallback((IShadowsocksServiceCallback) callback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            callbackRegistered = true;
        }
    }

    protected void unregisterCallback() {
        if (bgService != null && callback != null && callbackRegistered) {
            try {
                bgService.unregisterCallback((IShadowsocksServiceCallback) callback);

            } catch (RemoteException e) {
                e.printStackTrace();
            }
            callbackRegistered = false;
        }
    }

    public abstract void onServiceConnected();
    public abstract void onServiceDisconnected();
    @Override
    public void binderDied() {

    }

    private IShadowsocksServiceCallback.Stub callback;
    private ShadowsocksServiceConnection connection;
    private boolean callbackRegistered = false;

    protected IBinder binder;
    protected IShadowsocksService bgService;

    public void attachService(IShadowsocksServiceCallback.Stub callback) {
        Log.e(TAG, "attachService");
        this.callback = callback;
        if (bgService == null) {
            Intent intent = new Intent(this, ShadowsocksVpnService.class);
            intent.setAction(Action.SERVICE);
            connection = new ShadowsocksServiceConnection();
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
        }
    }

    public void detachService() {
        unregisterCallback();
        callback = null;
        if (connection != null) {
            unbindService(connection);
            connection = null;
        }
        if (binder != null) {
            binder.unlinkToDeath(this, 0);
            binder = null;
        }
        bgService = null;
    }
}
