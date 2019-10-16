package com.snirpoapps.messaging.transport;

import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;

public interface RxConnection extends RxCloseable {
    Mono<ByteBuffer> read();

    Mono<Void> write(ByteBuffer byteBuffer);
}
