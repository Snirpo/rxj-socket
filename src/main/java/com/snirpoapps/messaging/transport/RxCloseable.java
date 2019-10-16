package com.snirpoapps.messaging.transport;

import reactor.core.publisher.Mono;

public interface RxCloseable {
    Mono<Void> close();
}
