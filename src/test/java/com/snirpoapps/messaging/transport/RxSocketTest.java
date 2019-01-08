package com.snirpoapps.messaging.transport;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

public class RxSocketTest {

    @Test
    public void connect() throws NoSuchAlgorithmException, InterruptedException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
        //System.setProperty("https.protocols", "TLSv1.2");
        //System.setProperty("javax.net.debug", "all");

        RxSocket.create()
                .port(9000)
                .bufferSize(2048)
                .hostname("localhost")
                .connect()
                .switchMap(connection -> connection.read(1).repeat())
                .doOnNext(b -> System.out.println(StandardCharsets.UTF_8.decode(b)))
                .take(1)
                .blockLast();
    }

}