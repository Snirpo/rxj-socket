package com.snirpoapps.messaging.transport;

import lombok.Builder;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;

public class RxSocket {
    private final ByteBuffer outgoingData;
    private final ByteBuffer incomingData;
    private final RxSocketChannel socketChannel;

    @Builder
    private RxSocket(String hostname, int port, int bufferSize) {
        if (bufferSize == 0) bufferSize = 2048;

        this.outgoingData = ByteBuffer.allocateDirect(bufferSize);
        this.incomingData = ByteBuffer.allocateDirect(bufferSize);

        this.socketChannel = RxSocketChannel.builder()
                .hostname(hostname)
                .port(port)
                .timeout(5000)
                .build();
    }

    public Mono<ByteBuffer> read() {
        return Mono.defer(() -> {
            incomingData.clear();
            return socketChannel.read(incomingData);
        });
    }

    public Mono<Void> write(ByteBuffer buffer) {
        return Mono.defer(() -> {
            outgoingData.clear();
            while (buffer.hasRemaining() && outgoingData.hasRemaining()) {
                outgoingData.put(buffer.get());
            }
            outgoingData.flip();
            return socketChannel.write(outgoingData);
        }).repeat(buffer::hasRemaining).then();
    }
}
