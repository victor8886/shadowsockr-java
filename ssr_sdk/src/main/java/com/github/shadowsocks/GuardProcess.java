package com.github.shadowsocks;

import com.github.shadowsocks.utils.Callback;

import java.io.IOException;
import java.lang.*;
import java.lang.System;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Created by victor on 2017/4/7.
 */

public class GuardProcess {
    private volatile Thread guardThread;
    private volatile boolean isDestroyed;
    private volatile Process process;
    private volatile boolean isRestart = false;
    private List<String> cmd;

    public GuardProcess(List<String> cmd) {
        this.cmd = cmd;
    }
    public GuardProcess start(final Callback restartCallback) {
        final Semaphore semaphore = new Semaphore(1);
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        guardThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Callback callback = null;
                    while (!isDestroyed) {
                        long startTime = System.currentTimeMillis();
                        process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                        if (callback == null) {
                            callback = restartCallback;
                        } else {
                            callback.callback();
                        }
                        semaphore.release();
                        process.waitFor();
                        synchronized (this) {
                            if (isRestart) {
                                isRestart = false;
                            } else {
                                if (System.currentTimeMillis() - startTime < 1000) {
                                    isDestroyed = true;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    process.destroy();
                } finally {
                    semaphore.release();
                }
            }
        },"GuardThread-" + cmd);
        guardThread .start();
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return this;
    }

    public void destroy() {
        isDestroyed = true;
        guardThread.interrupt();
        process.destroy();
        try {
            guardThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public synchronized void restart() {
        isRestart = true;
        process.destroy();
    }

    public int waitFor() throws InterruptedException {
        guardThread.join();
        return 0;
    }
}
