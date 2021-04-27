package io.prometheus.jmx;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Ip {
    public static void main(String[] args) throws UnknownHostException {
        InetAddress addr = InetAddress.getLocalHost();
        System.out.println(addr.getHostAddress());
    }
}
