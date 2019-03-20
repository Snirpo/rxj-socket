package com.snirpoapps.messaging.transport;

import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

public class RxSSLSocketTest {

    private static final String HTTP_MESSAGE = "" +
            "GET / HTTP/1.1\n" +
            "Host: tls-v1-2.badssl.com:1012\n" +
            "Connection: keep-alive\n" +
            "Cache-Control: max-age=0\n" +
            "Upgrade-Insecure-Requests: 1\n" +
            "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.109 Safari/537.36\n" +
            "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8\n" +
            "Accept-Encoding: gzip, deflate, br\n" +
            "Accept-Language: en-US,en;q=0.9,nl-NL;q=0.8,nl;q=0.7\n";

    @Test
    public void connect() throws NoSuchAlgorithmException, InterruptedException {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
        //System.setProperty("https.protocols", "TLSv1.2");
        System.setProperty("javax.net.debug", "all");


        RxSocketChannel rxSocket = RxSocketChannel.create()
                .port(1012)
                .hostname("tls-v1-2.badssl.com");

        RxSSLSocket.create()
                .rxSocketChannel(rxSocket)
                .sslContext(SSLContext.getDefault())
                .connect()
                .switchMap(connection -> {
                    return connection.write(ByteBuffer.wrap(HTTP_MESSAGE.getBytes(StandardCharsets.UTF_8)))
                            .then(connection.read(1));
                    //return connection.read(1).repeat();
                })
                .doOnNext(b -> System.out.println(StandardCharsets.UTF_8.decode(b)))
                .blockLast();
    }

}