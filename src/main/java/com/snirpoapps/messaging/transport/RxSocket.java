package com.snirpoapps.messaging.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

public class RxSocket {
    private static final Logger LOGGER = LoggerFactory.getLogger(RxSocket.class);

    private int bufferSize = 2048;
    private String hostname;
    private int port;

    private RxSocket() {
    }

    public RxSocket bufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
        return this;
    }

    public RxSocket hostname(String hostname) {
        this.hostname = hostname;
        return this;
    }

    public RxSocket port(int port) {
        this.port = port;
        return this;
    }

    public Flux<Connection> connect() {
        final String hostname = this.hostname;
        final int port = this.port;
        final int bufferSize = this.bufferSize;

        return RxSocketChannel.create()
                .hostname(hostname)
                .port(port)
                .connect()
                .map(connection -> new Connection(connection, bufferSize));
    }

    public static RxSocket create() {
        return new RxSocket();
    }

    public class Connection {
        private final RxSocketChannel.Connection connection;
        private final ByteBuffer outgoingData;
        private ByteBuffer incomingData;

        private Connection(RxSocketChannel.Connection connection, int bufferSize) {
            this.connection = connection;
            this.outgoingData = ByteBuffer.allocateDirect(bufferSize);
            this.incomingData = ByteBuffer.allocateDirect(bufferSize);
        }

        public Mono<ByteBuffer> read() {
            return Mono.defer(() -> {
                incomingData.clear();
                return connection.read(incomingData);
            });
        }

        public Mono<Void> write(ByteBuffer buffer) {
            return Mono.defer(() -> {
                outgoingData.clear();
                while (buffer.hasRemaining() && outgoingData.hasRemaining()) {
                    outgoingData.put(buffer.get());
                }
                outgoingData.flip();
                return connection.write(outgoingData);
            }).repeat(buffer::hasRemaining).then();
        }
    }
}
