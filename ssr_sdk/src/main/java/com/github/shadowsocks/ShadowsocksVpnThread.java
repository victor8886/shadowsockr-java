package com.github.shadowsocks;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.*;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by victor on 2017/4/7.
 */

public class ShadowsocksVpnThread extends Thread {
    public  Method getInt ;
    private ShadowsocksVpnService vpnService ;
    String PATH ;
    public volatile boolean isRunning = true;
    public LocalServerSocket serverSocket;

    public ShadowsocksVpnThread(ShadowsocksVpnService vpnService) {
        this.vpnService = vpnService;
        PATH = vpnService.getApplicationInfo().dataDir + "/protect_path";
        try {
            getInt = FileDescriptor.class.getDeclaredMethod("getInt$");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    private void closeServerSocket() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            serverSocket = null;
        }
    }

    public void stopThread() {
        isRunning = false;
        closeServerSocket();
    }

    @Override
    public void run() {
        new File(PATH).delete();
        try {
            LocalSocket localSocket = new LocalSocket();
            localSocket.bind(new LocalSocketAddress(PATH,LocalSocketAddress.Namespace.FILESYSTEM));
            serverSocket = new LocalServerSocket(localSocket.getFileDescriptor());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        while (isRunning) {
            try {
                final LocalSocket socket = serverSocket.accept();
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            InputStream input = socket.getInputStream();
                            OutputStream output = socket.getOutputStream();
                            input.read();
                            FileDescriptor[] fds = socket.getAncillaryFileDescriptors();
                            if (fds.length > 1) {
                                int fd = (int) getInt.invoke(fds[0]);
                                boolean ret = vpnService.protect(fd);
                                System.jniclose(fd);
                                if (ret) {
                                    output.write(0);
                                } else output.write(1);

                            }
                            input.close();
                            output.close();

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

        }

    }
}
