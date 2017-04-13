package com.github.shadowsocks.database;

import android.util.Base64;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;

import java.util.Date;
import java.util.Locale;

/**
 * Created by victor on 2017/4/6.
 */

public class Profile {
    private int enMethod = Base64.URL_SAFE | Base64.NO_WRAP;
    @DatabaseField(generatedId = true)
    public int id =0;

    @DatabaseField
    public String name = "Untitled";

    @DatabaseField
    public String host = "";

    @DatabaseField
    public int localPort = 1080;

    @DatabaseField
    public int remotePort = 8388;

    @DatabaseField
    public String password = "";

    @DatabaseField
    public String protocol = "origin";

    @DatabaseField
    public String protocol_param = "";

    @DatabaseField
    public String obfs = "plain";

    @DatabaseField
    public String obfs_param = "";

    @DatabaseField
    public String method = "aes-256-cfb";

    @DatabaseField
    public String route = "all";

    @DatabaseField
    public boolean proxyApps = false;

    @DatabaseField
    public boolean bypass = false;

    @DatabaseField
    public boolean udpdns = false;

    @DatabaseField
    public String dns = "8.8.8.8:53";

    @DatabaseField
    public String china_dns = "114.114.114.114:53,223.5.5.5:53";

    @DatabaseField
    public boolean ipv6 = false;

    @DatabaseField(dataType = DataType.LONG_STRING)
    public String individual = "";

    @DatabaseField
    public long tx = 0;

    @DatabaseField
    public long rx = 0;

    @DatabaseField
    public Date date = new java.util.Date();

    @DatabaseField
    public long userOrder = 0;

    @Override
    public String toString() {
        String en_password = Base64.encodeToString(password.getBytes(), enMethod);
        String en_obfs_param = Base64.encodeToString(obfs_param.getBytes(), enMethod);
        String en_protocol_param = Base64.encodeToString(protocol_param.getBytes(), enMethod);
        String en_name = Base64.encodeToString(name.getBytes(), enMethod);
        byte[] bytes = ("ssr://"+String.format(Locale.ENGLISH,
                "%s:%d:%s:%s:%s:%s/?obfsparam=%s&protoparam=%s&remarks=%s",
                host,remotePort,protocol,method,obfs,en_password,en_obfs_param,
                en_protocol_param,en_name)).getBytes();
        return Base64.encodeToString(bytes,enMethod);
    }

    public boolean isMethodUnsafe () {
        return "table".equalsIgnoreCase(method) || "rc4".equalsIgnoreCase(method);
    }
}
