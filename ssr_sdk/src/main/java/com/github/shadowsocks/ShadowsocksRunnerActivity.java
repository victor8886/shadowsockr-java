package com.github.shadowsocks;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.github.shadowsocks.utils.SS_SDK;

public class ShadowsocksRunnerActivity extends ServiceBoundContext {
    private final int REQUEST_CONNECT = 1;
    Handler handler = new Handler();
    BroadcastReceiver receiver;


    @Override
    public void onServiceConnected() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (bgService != null) {
                    startBackgroundService();
                }
            }
        },1000);
    }

    private void startBackgroundService() {
        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            startActivityForResult(prepare,REQUEST_CONNECT);
        } else {
            onActivityResult(REQUEST_CONNECT,RESULT_OK,null);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        boolean locked = km.inKeyguardRestrictedInputMode();
        if (locked) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
            receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction() == Intent.ACTION_USER_PRESENT) {
                        attachService(null);
                    }
                }
            };
            registerReceiver(receiver, filter);
        } else {
            attachService(null);
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachService();
        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (resultCode) {
            case RESULT_OK:
                if (bgService != null) {
                    try {
                        bgService.use(SS_SDK.getInstance().profileId());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
        }
        finish();
    }

    @Override
    public void onServiceDisconnected() {

    }
}
