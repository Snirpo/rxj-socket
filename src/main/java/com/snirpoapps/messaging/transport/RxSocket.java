package com.snirpoapps.messaging.transport;

import lombok.Builder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;

public class RxSocket implements RxConnectable<RxSocket.Connection> {
    private final Mono<Connection> connection$;

    @Builder
    private RxSocket(String hostname, int port, int bufferSize) {
        RxSocketChannel socketChannel = RxSocketChannel.builder()
                .hostname(hostname)
                .port(port)
                .build();

        this.connection$ = socketChannel.connect()
                .map(connection -> new Connection(connection, bufferSize));
    }

    @Override
    public Mono<Connection> connect() {
        return connection$;
    }

    public static class Connection implements RxConnection {
        private final RxSocketChannel.Connection connection;

        private final ByteBuffer outgoingData;
        private final ByteBuffer incomingData;

        public Connection(RxSocketChannel.Connection connection, int bufferSize) {
            if (bufferSize == 0) bufferSize = 2048;
            this.connection = connection;

            this.outgoingData = ByteBuffer.allocateDirect(bufferSize);
            this.incomingData = ByteBuffer.allocateDirect(bufferSize);
        }

        @Override
        public Flux<ByteBuffer> read() {
            return Mono.defer(() -> {
                incomingData.clear();
                return connection.read(incomingData);
            }).repeat();
        }

        @Override
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

        @Override
        public Mono<Void> close() {
            return connection.close();
        }
    }
}
