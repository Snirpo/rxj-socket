package com.snirpoapps.messaging.transport;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

public interface RxConnectable<TCloseable extends RxCloseable> {
    Mono<TCloseable> connect();

    default <T> Flux<T> use(Function<TCloseable, Flux<T>> connectionFlux) {
        return Flux.usingWhen(
                connect(),
                connectionFlux,
                TCloseable::close
        );
    }
}
