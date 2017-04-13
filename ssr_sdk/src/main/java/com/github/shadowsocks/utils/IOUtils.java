package com.github.shadowsocks.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by victor on 2017/4/6.
 */

public class IOUtils {
    public static void copy(InputStream in, OutputStream out) {
        try {
            byte[] bytes = new byte[1024];
            int len = 0;
            while ((len = in.read(bytes)) > 0) {
                out.write(bytes,0,len);
            }
            out.flush();
            in.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
