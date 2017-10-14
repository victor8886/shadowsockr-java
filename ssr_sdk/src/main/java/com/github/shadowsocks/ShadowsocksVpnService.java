package com.github.shadowsocks;

import android.content.Intent;
import android.net.VpnService;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import com.github.shadowsocks.base.BaseService;
import com.github.shadowsocks.constant.Action;
import com.github.shadowsocks.constant.ConfigUtils;
import com.github.shadowsocks.constant.Route;
import com.github.shadowsocks.constant.State;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.utils.Callback;
import com.github.shadowsocks.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.lang.*;
import java.util.ArrayList;
import java.util.Locale;

/**
 * vpn服务
 */
public class ShadowsocksVpnService extends BaseService implements Callback {
    private final int VPN_MTU = 1500;
    private final String PRIVATE_VLAN = "26.26.26.%s";
    private final String PRIVATE_VLAN6 = "fdfe:dcba:9876::%s";
    private ParcelFileDescriptor conn;
    private ShadowsocksVpnThread vpnThread;
    private GuardProcess sslocalProcess;
    private GuardProcess sstunnelProcess;
    private GuardProcess pdnsdProcess;
    private GuardProcess tun2socksPrcess;
    private int mFd;

    public ShadowsocksVpnService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        String action = intent.getAction();
        if (VpnService.SERVICE_INTERFACE == action) {
            return super.onBind(intent);
        } else if (Action.SERVICE == action) {
            return (IBinder) binder;
        }
        return null;
    }

    @Override
    public void onRevoke() {
        stopRunner(true, null);
    }

    @Override
    protected void stopRunner(boolean stopService, String msg) {
        if (vpnThread != null) {
            vpnThread.stopThread();
            vpnThread = null;
        }
        changeState(State.STOPPING, null);
        killProcesses();
        if (conn != null) {
            try {
                conn.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            conn = null;
        }
        super.stopRunner(stopService,msg);
    }

    private void killProcesses() {
        if (sslocalProcess != null) {
            sslocalProcess.destroy();
            sslocalProcess = null;
        }
        if (sstunnelProcess != null) {
            sstunnelProcess.destroy();
            sstunnelProcess = null;
        }
        if (tun2socksPrcess != null) {
            tun2socksPrcess.destroy();
            tun2socksPrcess = null;
        }
        if (pdnsdProcess != null) {
            pdnsdProcess.destroy();
            pdnsdProcess = null;
        }
    }

    @Override
    protected void startRunner(Profile profile) {
        if (VpnService.prepare(this) != null) {
            Intent i = new Intent(this, ShadowsocksRunnerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            stopRunner(true,null);
            return;
        }
        super.startRunner(profile);
    }

    @Override
    public void connect() {
        vpnThread = new ShadowsocksVpnThread(this);
        vpnThread.start();
        killProcesses();
        //判断serveraddress
        if (!Utils.isNumeric(profile.host)) {
            String addr = Utils.resolve(profile.host,true);
            if (addr == null) {
               return;
            }
            profile.host = addr;
        }
        handleConnection();
        changeState(State.CONNECTED, null);

    }

    private void handleConnection() {
        int fd = startvpn();
        if (!sendFd(fd)) {
            Log.e("vpn", "sendFd failed");
            return;
        }
        startShadowsocksDaemon();
        if (profile.udpdns) {
            startShadowsocksUDPDaemon();
        }
        if (!profile.udpdns) {
            startDnsDaemon();
            startDnsTunel();
        }

    }

    private void startShadowsocksDaemon() {
        String conf = String.format(Locale.ENGLISH, ConfigUtils.SHADOWSOCKS,
                profile.host, profile.remotePort,
                profile.localPort,
                ConfigUtils.escapedJson(profile.password),
                profile.method, 600, profile.protocol, profile.obfs,
                ConfigUtils.escapedJson(profile.obfs_param),
                ConfigUtils.escapedJson(profile.protocol_param));

        Utils.printToFile(conf, new File(getApplicationInfo().dataDir + "/ss-local-vpn.conf"));
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(getApplicationInfo().dataDir + "/ss-local");
        cmd.add("-V");
        cmd.add("-t");
        cmd.add("127.0.0.1");
        cmd.add("-t");
        cmd.add("600");
        cmd.add("-P");
        cmd.add(getApplicationInfo().dataDir);
        cmd.add("-c");
        cmd.add(getApplicationInfo().dataDir+"/ss-local-vpn.conf");
        if (profile.udpdns) {
            cmd.add("-u");
        }
        if (profile.route != Route.ALL) {
            cmd.add("--acl");
            cmd.add(getApplicationInfo().dataDir+'/'+profile.route+".acl");
        }
        sslocalProcess = new GuardProcess(cmd).start(null);
    }

    private void startDnsTunel() {
        String conf = String.format(Locale.ENGLISH,ConfigUtils.SHADOWSOCKS,
                profile.host,
                profile.remotePort,
                profile.localPort+63,
                ConfigUtils.escapedJson(profile.password),
                profile.method,600,
                profile.protocol,profile.obfs,
                ConfigUtils.escapedJson(profile.obfs_param),
                ConfigUtils.escapedJson(profile.protocol_param));
        Utils.printToFile(conf,new File(getApplicationInfo().dataDir+
        "/ss-tunnel-vpn.conf"));

        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(getApplicationInfo().dataDir + "/ss-tunnel");
        cmd.add("-V");
        cmd.add("-u");
        cmd.add("-t");
        cmd.add("10");
        cmd.add("-b");
        cmd.add("127.0.0.1");
        cmd.add("-P");
        cmd.add(getApplicationInfo().dataDir);
        cmd.add("-c");
        cmd.add(getApplicationInfo().dataDir + "/ss-tunnel-vpn.conf");
        cmd.add("-L");
        if (profile.route == Route.CHINALIST) {
            cmd.add(profile.china_dns.split(",")[0]);
        } else {
            cmd.add(profile.dns.split(",")[0]);
        }
        sstunnelProcess = new GuardProcess(cmd).start(null);

    }

    private void startDnsDaemon() {
        String reject = profile.ipv6 ? "224.0.0.0/3" : "224.0.0.0/3, ::/0";
        String protect = "protect = \"" +protectPath+"\";";

        String china_dns_setting = "";

        String black_list = getString(R.string.black_list);
        String[] china_dnss = profile.china_dns.split(",");

        for (int i = 0; i < china_dnss.length; i++) {
            china_dns_setting += String.format(Locale.ENGLISH, ConfigUtils.REMOTE_SERVER,
                    china_dnss[i].split(":")[0],
                    Integer.decode(china_dnss[i].split(":")[1]),
                    black_list, reject);
        }
        String conf =null;
        switch (profile.route) {
            case Route.BYPASS_CHN:
            case Route.BYPASS_LAN_CHN:
            case Route.GFWLIST:
                conf = String.format(Locale.ENGLISH,ConfigUtils.PDNSD_DIRECT,
                        protect,getApplicationInfo().dataDir,
                        "0.0.0.0",profile.localPort+53,china_dns_setting,
                        profile.localPort+63,reject);
                break;
            case Route.CHINALIST:
                conf = String.format(Locale.ENGLISH, protect, getApplicationInfo().dataDir,
                        "0.0.0.0", profile.localPort + 53,
                        china_dns_setting,
                        profile.localPort + 63,
                        reject);
                break;
            default:
                conf = String.format(Locale.ENGLISH, protect, getApplicationInfo().dataDir,
                        "0.0.0.0", profile.localPort + 53, profile.localPort + 63, reject);
                break;
        }
        Utils.printToFile(conf,new File(getApplicationInfo().dataDir+"/pdnsd-vpn.conf"));
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(getApplicationInfo().dataDir+"/pdnsd");
        cmd.add("-c");
        cmd.add(getApplicationInfo().dataDir+"/pdnsd-vpn.conf");
        pdnsdProcess = new GuardProcess(cmd).start(null);
    }

    private void startShadowsocksUDPDaemon() {
        String conf = String.format(Locale.ENGLISH,ConfigUtils.SHADOWSOCKS,
                profile.host,
                profile.remotePort,
                profile.protocol,
                profile.obfs,
                ConfigUtils.escapedJson(profile.obfs_param),
                ConfigUtils.escapedJson(profile.protocol_param));
        Utils.printToFile(conf,new File(getApplicationInfo().dataDir+"/ss-local-udp-vpn.conf"));
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(getApplicationInfo().dataDir+"/ss-local");
        cmd.add("-V");
        cmd.add("-U");
        cmd.add("-b");
        cmd.add("127.0.0.1");
        cmd.add("-t");
        cmd.add("600");
        cmd.add("-P");
        cmd.add(getApplicationInfo().dataDir);
        cmd.add("-c");
        cmd.add(getApplicationInfo().dataDir+"/ss-local-udp-vpn.conf");
        sstunnelProcess = new GuardProcess(cmd).start(null);
    }



    private int startvpn() {
        Builder builder = new Builder();
        builder.setSession(profile.name)
                .setMtu(VPN_MTU)
                .addAddress(String.format(Locale.ENGLISH, PRIVATE_VLAN, "1"), 24);
        if (profile.ipv6) {
            builder.addAddress(String.format(Locale.ENGLISH, PRIVATE_VLAN6, "1"), 126)
                    .addRoute("::", 0);
        }
        /*if (profile.route == Route.ALL || profile.route == Route.BYPASS_CHN) {
            //builder.addRoute("0.0.0.0", 0);
        } else {
            *//*String[] stringArray = getResources().getStringArray(R.array.bypass_private_route);
            for (int i = 0; i < stringArray.length; i++) {
                String[] strings = stringArray[i].split("/");
                builder.addRoute(strings[0], Integer.decode(strings[1]));
            }*//*
        }*/
        builder.addRoute("0.0.0.0", 0);
        builder.addRoute(profile.dns.split(",")[0].split(":")[0], 32);
        conn = builder.establish();
        if (conn == null) {
            throw new NullPointerException();
        }
        mFd = conn.getFd();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(getApplicationInfo().dataDir+"/tun2socks");
        cmd.add("--netif-ipaddr");
        cmd.add(String.format(Locale.ENGLISH, PRIVATE_VLAN, "2"));
        cmd.add("--netif-netmask");
        cmd.add("255.255.255.0");
        cmd.add("--socks-server-addr");
        cmd.add("127.0.0.1:" + profile.localPort);
        cmd.add("--tunfd");
        cmd.add(mFd +"");
        cmd.add("--tunmtu");
        cmd.add(VPN_MTU + "");
        cmd.add("--sock-path");
        cmd.add(getApplicationInfo().dataDir + "/sock_path");
        cmd.add("--loglevel");
        cmd.add("3");
        if (profile.ipv6) {
            cmd.add("--netif-ip6addr");
            cmd.add(String.format(Locale.ENGLISH,PRIVATE_VLAN6,"2"));
        }
        if (profile.udpdns) {
            cmd.add("--enable-udprelay");
        } else {
            cmd.add("--dnsgw");
            cmd.add(String.format(Locale.ENGLISH, "%s:%d", String.format(Locale.ENGLISH,
                    PRIVATE_VLAN, "1"), profile.localPort + 53));
        }
        tun2socksPrcess = new GuardProcess(cmd).start(null);
        return mFd;
    }

    private boolean sendFd(int fd) {
        if (-1 != fd) {
            int tries = 1;
            while (tries < 5) {
                try {
                    Thread.sleep(1000* tries);
                    if (System.sendfd(fd, getApplicationInfo().dataDir + "/sock_path") != -1) {
                        return true;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                tries +=1;
            }
        }
        return false;
    }

    @Override
    public void callback() {
        sendFd(mFd);
    }
}
