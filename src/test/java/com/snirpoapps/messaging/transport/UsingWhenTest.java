package com.snirpoapps.messaging.transport;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class UsingWhenTest {
    @Test
    public void test() {
        Flux<String> resource = Flux.just("resource").log("resource");

        Disposable dis = Mono.usingWhen(
                resource,
                it -> Mono.just("child").log("child"),
                it -> Mono.just("complete").log("complete"),
                it -> Mono.just("error").log("error"),
                it -> Mono.just("cancel").log("cancel")
        ).log("parent").subscribe();
        dis.dispose();
    }
}
