package com.snirpoapps.messaging.transport;

import org.junit.Test;

import javax.net.ssl.SSLContext;

import java.security.NoSuchAlgorithmException;

public class RxSSLSocketTest {

    @Test
    public void connect() throws NoSuchAlgorithmException, InterruptedException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
        //System.setProperty("https.protocols", "TLSv1.2");
        //System.setProperty("javax.net.debug", "all");

        RxSSLSocket socketTransport = new RxSSLSocket(SSLContext.getDefault());
        socketTransport.open("tls-v1-2.badssl.com", 1012).blockFirst();
    }

}