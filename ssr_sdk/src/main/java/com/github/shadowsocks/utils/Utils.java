package com.github.shadowsocks.utils;

import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;


/**
 * Created by victor on 2017/4/10.
 */

public class Utils {
    public static String resolve(String host, int addrType) {

            try {
                Lookup lookup = new Lookup(host, addrType);
                SimpleResolver resolver = new SimpleResolver("114.114.114.114");
                resolver.setTimeout(5);
                lookup.setResolver(resolver);
                Record[] result = lookup.run();
                if (result == null) {
                    return null;
                }
                for (Record r : result) {
                    switch (addrType) {
                        case Type.A:
                            return ((ARecord)r).getAddress().getHostAddress();
                        case Type.AAAA:
                            return ((AAAARecord)r).getAddress().getHostAddress();
                    }
                }
            } catch (TextParseException e) {
                e.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        return null;
    }

    public static String resolve(String host, boolean enableIPv6) {
        String addr = null;
        if (enableIPv6 && Utils.isIPv6Support()) {
             addr = resolve(host, Type.AAAA);
            if (addr != null) {
                return addr;
            }
        }
        addr = resolve(host, Type.A);
        if (addr != null) {
            return addr;
        }
        addr = resolve(host);
        return addr;
    }

    private static String resolve(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.getHostAddress();
        } catch (Exception e) {

        }
        return null;
    }

    private static boolean isIPv6Support() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if(inetAddress instanceof Inet6Address &&
                            !inetAddress.isLoopbackAddress()&&
                            inetAddress.isLinkLocalAddress()){
                        return true;
                    }
                }
            }

        } catch (Exception e) {
            return false;
        }

        return false;
    }

    private static Method isNumericMethod ;
    public static boolean isNumeric(String address) {
        try {
            isNumericMethod = InetAddress.class.getMethod("isNumeric", String.class);
            boolean isNum = (boolean) isNumericMethod.invoke(null, address);
            return isNum;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void printToFile(String conf, File file) {
        try {
            PrintWriter pw = new PrintWriter(file);
            pw.write(conf);
            pw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
