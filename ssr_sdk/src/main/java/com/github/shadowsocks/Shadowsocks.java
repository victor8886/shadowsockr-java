package com.github.shadowsocks;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.constant.State;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.interfaces.SetProfile;
import com.github.shadowsocks.utils.SS_SDK;

public class Shadowsocks extends ServiceBoundContext {
    private static SetProfile setProfile;
    private final int REQUEST_CONNECT = 1;
    private boolean serviceStarted;
    int state = State.STOPPED;
    SS_SDK app = SS_SDK.getInstance();
    Profile currentProfile = new Profile();
    Profile mProfile;
    //boolean vpnState;
    Handler handler = new Handler();

    public static void setProfile(SetProfile set) {
        setProfile = set;
    }

    //因为vpn是单独的进程，所以这里用到aidl进行进程间的通信
    private IShadowsocksServiceCallback.Stub callback = new IShadowsocksServiceCallback.Stub() {
        @Override
        public void stateChanged(final int s, String profileName, String msg) throws RemoteException {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    switch (s) {
                        case State.CONNECTING:
                            break;
                        case State.CONNECTED:
                            if (state == State.CONNECTING) {
                                //connecting
                            }
                            changeSwitch(true);
                            finish();
                            break;
                        case State.STOPPED:
                            //stopped
                            changeSwitch(false);
                            finish();
                            break;
                        case State.STOPPING:
                            //stopping
                            break;
                    }
                    state = s;
                    app.setState(state);
                }
            });
        }

        @Override
        public void trafficUpdated(long txRate, long rxRate, long txTotal, long rxTotal) throws RemoteException {
            //refresh tx rx
        }
    };

    private void changeSwitch(boolean b) {
        serviceStarted = b;
    }

    private void cancelStart() {
        changeSwitch(false);
    }

    public void attachService() {
        attachService(callback);
    }

    public void switchVpn() {
        if (serviceStarted) {
            serviceStop();
        } else if (bgService != null) {
            prepareStartService();
        } else {
            changeSwitch(false);
        }
    }

    private void prepareStartService() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, REQUEST_CONNECT);
        } else {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onActivityResult(REQUEST_CONNECT, RESULT_OK, null);
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //vpn的开启需要activity的回调，因此这里耍个小聪明，
        // 建立一个像素的activity，这样就可以在任何地方开启vpn了，比如服务里面
        Window window = getWindow();
        window.setGravity(Gravity.LEFT | Gravity.TOP);
        WindowManager.LayoutParams params = window.getAttributes();
        params.x = 0;
        params.y = 0;
        params.height = 1;
        params.width = 1;
        window.setAttributes(params);
        handler.post(new Runnable() {
            @Override
            public void run() {
                attachService();
            }
        });
    }

    public void refreshProfile() {
       /* Profile profile = app.currentProfile();
        if (profile == null) {
            profile = app.profileManager.getFirstProfile();
        }
        if (profile == null) {
            app.profileManager.createDefault();
        }
        mProfile = profile;*/
        app.profileId(mProfile.id);
        app.profileManager.updateProfile(mProfile);
    }

    private boolean updateCurrentProfile() {
        if (mProfile == null || currentProfile.id != mProfile.id) {
            Profile profile = app.currentProfile();
            if (profile == null) {
                profile = app.profileManager.getFirstProfile();//profile =  app.profileManager.createDefault();
            }
            if (profile == null) {
                profile = app.profileManager.createDefault();
            }
            updatePreferenceScreen(profile);
            if (serviceStarted) {
                serviceLoad();
            }
            refreshProfile();
            Log.d("profile id ", "id = " + currentProfile.id);
            return true;
        } else {
            refreshProfile();
            Log.d("profile id ", "id = " + currentProfile.id);
            return false;
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCurrentProfile();
        updateState();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                switchVpn();
            }
        }, 1000);
    }

    private void updatePreferenceScreen(Profile profile) {
        /*profile.host = "182.61.100.95";
        profile.localPort = 1080;
        profile.remotePort = 10369;
        profile.password = "5X8BAfwLsPmC";
        profile.udpdns = false;
        profile.ipv6 = false;
        profile.bypass = false;*/
        if (setProfile != null) {
            setProfile.set(profile);
        }
        //profile.protocol = "auth_sha1_v2";
        //profile.obfs = "http_simple";
        mProfile = profile;

    }

    @Override
    protected void onStart() {
        super.onStart();
        registerCallback();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterCallback();
    }

    private boolean isDestroyed;

    @Override
    public boolean isDestroyed() {
        return isDestroyed;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
        detachService();
        handler.removeCallbacksAndMessages(null);
    }

    private void recovery() {
        if (serviceStarted) {
            serviceStop();
            app.copyAssets(this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (resultCode) {
            case RESULT_OK:
                serviceLoad();
                break;
            default:
                cancelStart();
        }

    }

    private void serviceStop() {
        if (bgService != null) {
            try {
                bgService.use(-1);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void serviceLoad() {
        try {
            //Log.e("app profileid","app profile id="+app.profileId());
            bgService.use(app.profileId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        if (app.isVpnEnabled()) {
            changeSwitch(false);
        }
    }

    @Override
    public void onServiceConnected() {
        updateState();
    }

    private void updateState() {
        //更新vpn状态
        if (bgService != null) {
            try {
                switch (bgService.getState()) {
                    case State.CONNECTING:
                        serviceStarted = false;
                        break;
                    case State.CONNECTED:
                        serviceStarted = true;
                        break;
                    case State.STOPPED:
                        serviceStarted = false;
                        break;
                    default:
                        serviceStarted = false;
                }
                state = bgService.getState();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onServiceDisconnected() {

    }

    @Override
    public void binderDied() {
        detachService();
        recovery();
        attachService();
    }
}

/*class Typefaces{
    private final Hashtable<String, Typeface> cache = new Hashtable<>();
    public  synchronized Typeface get(Context c, String assetPath) {
        if (!cache.contains(assetPath)) {
            cache.put(assetPath, Typeface.createFromAsset(c.getAssets(), assetPath));
            return null;
        }
        return cache.get(assetPath);
    }
}*/
