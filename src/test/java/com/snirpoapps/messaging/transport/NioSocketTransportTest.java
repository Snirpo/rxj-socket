package com.snirpoapps.messaging.transport;

import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.SSLContext;

import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.*;

public class NioSocketTransportTest {

    @Test
    public void connect() throws NoSuchAlgorithmException, InterruptedException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
        //System.setProperty("https.protocols", "TLSv1.2");
        //System.setProperty("javax.net.debug", "all");

        NioSocketTransport socketTransport = new NioSocketTransport(SSLContext.getDefault(), null);
        socketTransport.open("tls-v1-2.badssl.com", 1012).blockingForEach(System.out::println);

        Thread.sleep(2000);
    }

}