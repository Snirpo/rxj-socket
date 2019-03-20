package com.snirpoapps.messaging.transport;

import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

public class RxSSLSocketTest {

    private static final byte[] HTTP_MESSAGE = (
            "GET / HTTP/1.1\r\n" +
                    "Host: tls-v1-2.badssl.com:1012\r\n" +
                    "Connection: keep-alive\r\n" +
                    "Cache-Control: max-age=0\r\n" +
                    "\r\n"
    ).getBytes(StandardCharsets.UTF_8);

    @Test
    public void connect() throws NoSuchAlgorithmException, InterruptedException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
        //System.setProperty("https.protocols", "TLSv1.2");
        //System.setProperty("javax.net.debug", "all");

        RxSSLSocket.create()
                .hostname("tls-v1-2.badssl.com")
                .port(1012)
                .sslContext(SSLContext.getDefault())
                .connect()
                .switchMap(connection -> {
                    ByteBuffer buffer = ByteBuffer.wrap(HTTP_MESSAGE);
                    return connection.write(buffer)
                            .then(connection.read());
                    //return connection.read(1).repeat();
                })
                .doOnNext(b -> System.out.println(StandardCharsets.UTF_8.decode(b)))
                .blockLast();
    }

}