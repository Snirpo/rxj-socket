package com.snirpoapps.messaging.transport;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

public class RxSocketTest {

    private static final byte[] HTTP_MESSAGE = (
            "GET / HTTP/1.1\r\n" +
                    "Host: www.httpvshttps.com\r\n" +
                    "Connection: keep-alive\r\n" +
                    "Cache-Control: max-age=0\r\n" +
                    "\r\n"
    ).getBytes(StandardCharsets.UTF_8);

    @Test
    public void connect() throws NoSuchAlgorithmException, InterruptedException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
        //System.setProperty("https.protocols", "TLSv1.2");
        System.setProperty("javax.net.debug", "all");

        RxSocket.builder()
                .port(80)
                .bufferSize(16384)
                .hostname("www.httpvshttps.com")
                .build()
                .connect()
                .flatMapMany(connection -> {
                    return connection.write(ByteBuffer.wrap(HTTP_MESSAGE))
                            .thenMany(connection.read().repeat())
                            .doOnNext(b -> System.out.println(StandardCharsets.UTF_8.decode(b)));
                }).take(Duration.ofMillis(2000)).blockLast();
    }

}