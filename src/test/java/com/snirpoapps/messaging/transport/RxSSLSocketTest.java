package com.snirpoapps.messaging.transport;

import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

public class RxSSLSocketTest {

    @Test
    public void connect() throws NoSuchAlgorithmException, InterruptedException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
        //System.setProperty("https.protocols", "TLSv1.2");
        //System.setProperty("javax.net.debug", "all");


        RxSocket rxSocket = RxSocket.create()
                .port(443)
                .bufferSize(2048)
                .hostname("tls-v1-2.badssl.com");

        RxSSLSocket.create()
                .rxSocket(rxSocket)
                .sslContext(SSLContext.getDefault())
                .connect()
                .switchMap(connection -> connection.read(1).repeat())
                .doOnNext(b -> System.out.println(StandardCharsets.UTF_8.decode(b)))
                .blockLast();
    }

}