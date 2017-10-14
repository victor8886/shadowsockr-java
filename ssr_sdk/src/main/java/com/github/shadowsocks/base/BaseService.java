package com.github.shadowsocks.base;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Handler;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.text.TextUtils;

import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.constant.Action;
import com.github.shadowsocks.constant.State;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.utils.SS_SDK;

/**
 * Created by victor on 2017/4/6.
 */

public abstract class BaseService extends VpnService {
    private volatile int state = State.STOPPED;
    protected Profile profile = null;
    final RemoteCallbackList<IShadowsocksServiceCallback> callbacks = new RemoteCallbackList<IShadowsocksServiceCallback>();
    int callbackCount = 0;
    Handler handler = null;
    Handler restartHandler = null;
    protected String protectPath = null;
    boolean closeReceiverRegistered ;
    //实现aidl的接口，以供跨进程的数据传输，其实就是设置shadowsocks的参数，以及获取vpn的状态
    protected IShadowsocksService binder = new IShadowsocksService.Stub() {
        @Override
        public int getState(){
            return state;
        }

        @Override
        public String getProfileName() throws RemoteException {
            if (profile != null) {
                return profile.name;
            }
            return null;
        }

        @Override
        public void registerCallback(IShadowsocksServiceCallback cb) throws RemoteException {
            if (cb != null && callbacks.register(cb)) {
                callbackCount += 1;
            }
        }

        @Override
        public void unregisterCallback(IShadowsocksServiceCallback cb) throws RemoteException {
            if (cb != null && callbacks.unregister(cb)) {
                callbackCount -=1;
            }
        }

        @Override
        public synchronized void use(int profileId) throws RemoteException {
            if (profileId < 0) {
                stopRunner(true, null);
            } else {
                Profile profile = SS_SDK.getInstance().profileManager.getProfile(profileId);
                if (profile == null) {
                    stopRunner(true,null);
                } else {
                    switch (state) {
                        case State.STOPPED:
                            if (checkProfile(profile)) {
                                startRunner(profile);
                            }
                            break;
                        case State.CONNECTED:
                            if (profileId != BaseService.this.profile.id && checkProfile(profile)) {
                                stopRunner(false, null);
                                startRunner(profile);
                            }
                    }
                }
            }
        }

        @Override
        public void useSync(int profileId) throws RemoteException {
            use(profileId);
        }
    };

    protected void startRunner(Profile profile) {
        this.profile = profile;
        startService(new Intent(this, getClass()));
        if (!closeReceiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SHUTDOWN);
            filter.addAction(Action.CLOSE);
            registerReceiver(closeReciver, filter);
            closeReceiverRegistered = true;
        }
        changeState(State.CONNECTING,null);
        try {
            connect();
        } catch (Exception e) {
            stopRunner(true, e.toString());
        }
    }

    private boolean checkProfile(Profile profile) {
        if (TextUtils.isEmpty(profile.host) || TextUtils.isEmpty(profile.password)) {
            stopRunner(true, "proxy_empty");
            return false;
        }
        return true;
    }

    public abstract void connect();

    @Override
    public void onCreate() {
        handler = new Handler(getMainLooper());
        restartHandler = new Handler(getMainLooper());
        protectPath = getApplicationInfo().dataDir + "/protect_path";
        new Thread(){
            @Override
            public void run() {
                SS_SDK.getInstance().updateAssets(BaseService.this);
            }
        }.start();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }

    private BroadcastReceiver closeReciver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopRunner(true, null);
        }
    };

    protected void stopRunner(boolean stopService, String msg) {
        if (closeReceiverRegistered) {
            unregisterReceiver(closeReciver);
            closeReceiverRegistered = false;
        }
        changeState(State.STOPPED, msg);
        if(stopService){
            stopSelf();
        }
        profile = null;
    }


    protected void changeState(final int s, final String msg) {
        Handler handler = new Handler(getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (state != s || msg !=null) {
                    if (callbackCount > 0) {
                        int n = callbacks.beginBroadcast();
                        for (int i = 0; i < n; i++) {
                            try {
                                callbacks.getBroadcastItem(i).stateChanged(s,binder.getProfileName(), msg);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                        callbacks.finishBroadcast();
                    }
                    state = s;
                }
            }
        });
    }
}
