package com.github.shadowsocks.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.github.shadowsocks.BuildConfig;
import com.github.shadowsocks.Shadowsocks;
import com.github.shadowsocks.System;
import com.github.shadowsocks.constant.Executable;
import com.github.shadowsocks.constant.Key;
import com.github.shadowsocks.constant.Route;
import com.github.shadowsocks.database.DBHelper;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.interfaces.SetProfile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;

import eu.chainfire.libsuperuser.Shell;

/**
 * Created by victor on 2017/4/6.
 */
public class SS_SDK implements SetProfile {
    private static SS_SDK ourInstance = new SS_SDK();
    private ArrayList<String> EXECUTABLES = new ArrayList<>();
    private SharedPreferences settings;
    private SharedPreferences.Editor editor;
    public ProfileManager profileManager;
    public Shadowsocks shadowsocks;
    private  String host;
    private int state;
    private int remotePort;
    private String password;
    private String protocol = "origin";
    private VpnCallback stateCallback;

    public void setStateCallback(VpnCallback callback) {
        stateCallback = callback;
    }

    public static SS_SDK getInstance() {
        return ourInstance;
    }

    private SS_SDK() {
    }

    public int getVPNstate() {
        return state;
    }

    public void setState(int state) {
        if (stateCallback != null) {
            stateCallback.callback(state);
        }
        this.state = state;
    }

    public static void init(Context context) {
        //初始化shadowsocks运行环境，添加so运行的参数
        ourInstance.EXECUTABLES.add(Executable.PDNSD);
        ourInstance.EXECUTABLES.add(Executable.REDSOCKS);
        ourInstance.EXECUTABLES.add(Executable.SS_TUNNEL);
        ourInstance.EXECUTABLES.add(Executable.SS_LOCAL);
        ourInstance.EXECUTABLES.add(Executable.TUN2SOCKS);
        ourInstance.EXECUTABLES.add(Executable.KCPTUN);
        //获取shadowsocks配置文件管理类
        ourInstance.settings = PreferenceManager.getDefaultSharedPreferences(context);
        ourInstance.editor = ourInstance.settings.edit();
        ourInstance.profileManager = new ProfileManager(new DBHelper(context));
    }


    public boolean isNatEnabled() {
        return settings.getBoolean(Key.isNAT, false);
    }

    public boolean isVpnEnabled() {
        return !isNatEnabled();
    }

    public int profileId() {
        return settings.getInt(Key.id, -1);
    }

    public void profileId(int i) {
        editor.putInt(Key.id, i).apply();
    }

    public Profile currentProfile() {
        return profileManager.getProfile(profileId());
    }
    //切换shadowsock配置文件
    public Profile switchProfile(int id) {
        profileId(id);
        Profile profile = profileManager.getProfile(id);
        if (null == profile) {
            profile = profileManager.createProfile(profile);
        }
        return profile;
    }

    private void copyAssets(String path, Context context) {
        AssetManager assetManager = context.getAssets();
        String[] files = null;
        try {
            files = assetManager.list(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (files != null) {
            for (String s : files) {
                InputStream in = null;
                OutputStream out = null;
                try {
                    in = assetManager.open(!TextUtils.isEmpty(path)? path +"/"+s:s);
                    out = new FileOutputStream(context.getApplicationInfo().dataDir + '/' + s);
                    IOUtils.copy(in, out);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    //清理配置文件
    public void crashRecovery(Context context) {
        ArrayList<String> cmd = new ArrayList<>();
        String[] tasks = {"ss-local", "ss-tunnel", "pdnsd", "redsocks", "tun2socks", "kcptun"};
        for (int i = 0; i < tasks.length; i++) {
            cmd.add(String.format(Locale.ENGLISH, "killall %s", tasks[i]));
            cmd.add(String.format(Locale.ENGLISH, "rm -f %1$s/%2$s-nat.conf %1$s/%2$s-vpn.conf",
                    context.getApplicationInfo().dataDir, tasks[i]));
        }
        Shell.SH.run(cmd);
    }

    public void copyAssets(Context context) {
        crashRecovery(context);
        copyAssets(System.getABI(), context);
        copyAssets("acl", context);
        ArrayList<String> cmd = new ArrayList<>();
        for (int i = 0; i < EXECUTABLES.size(); i++) {
            String temp = "chmod 755 "+ context.getApplicationInfo().dataDir+"/"+EXECUTABLES.get(i);
            cmd.add(temp);
        }
        Shell.SH.run(cmd);
        cmd.clear();
        editor.putInt(Key.currentVersionCode, BuildConfig.VERSION_CODE).apply();
    }

    public void updateAssets(Context context) {
        if (settings.getInt(Key.currentVersionCode, -1) != BuildConfig.VERSION_CODE)
            copyAssets(context);
    }
    public void setProfile(String host,int remotePort,
                           String password,String protocol){
        this.host = host;
        this.remotePort = remotePort;
        this.password = password;
        this.protocol = protocol;
    }
    public void switchVpn(Context context){
        Shadowsocks.setProfile(this);
        context.startActivity(new Intent(context, Shadowsocks.class));
    }

    /**
     * 设置shadowsocksr的各项参数，各种参数请自己琢磨
     * @param profile
     */
    @Override
    public void set(Profile profile) {
        profile.host = host;
        profile.remotePort = remotePort;
        profile.password = password;
        profile.localPort = 1080;
        profile.udpdns = false;
        profile.ipv6 = false;
        profile.bypass = false;
        profile.route = Route.GFWLIST;
        profile.method = "aes-256-cfb";
        profile.protocol = protocol;//ssr 的协议类型，默认origin
    }
}
