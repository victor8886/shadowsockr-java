package com.github.shadowsocks.utils;

import android.content.Context;
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
import com.github.shadowsocks.database.DBHelper;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;

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
public class SS_SDK {
    private static SS_SDK ourInstance = new SS_SDK();
    private ArrayList<String> EXECUTABLES = new ArrayList<>();
    private SharedPreferences settings;
    private SharedPreferences.Editor editor;
    public ProfileManager profileManager;
    public static Shadowsocks shadowsocks;

    public static SS_SDK getInstance() {
        return ourInstance;
    }

    private SS_SDK() {
    }

    public static void init(Context context) {
        ourInstance.EXECUTABLES.add(Executable.PDNSD);
        ourInstance.EXECUTABLES.add(Executable.REDSOCKS);
        ourInstance.EXECUTABLES.add(Executable.SS_TUNNEL);
        ourInstance.EXECUTABLES.add(Executable.SS_LOCAL);
        ourInstance.EXECUTABLES.add(Executable.TUN2SOCKS);
        ourInstance.EXECUTABLES.add(Executable.KCPTUN);
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


}
