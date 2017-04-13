package com.github.shadowsocks.constant;

/**
 * Created by victor on 2017/4/6.
 */

public class State {
    public static final int CONNECTING = 1;
    public static final int CONNECTED = 2;
    public static final int STOPPING = 3;
    public static final int STOPPED = 4;

    public static boolean isAvailable(int state) {
        return state != CONNECTED && state != CONNECTING;
    }
}
