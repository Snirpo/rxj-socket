package com.snirpoapps.messaging.transport;

import lombok.Builder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.time.Duration;

public class RxSocketChannel {
    private final Mono<AsynchronousSocketChannel> socketChannel$;

    @Builder
    private RxSocketChannel(String hostname, int port, int timeout) {
        this.socketChannel$ = Flux.<AsynchronousSocketChannel>create(subscriber -> {
            AsynchronousSocketChannel socketChannel;

            try {
                socketChannel = AsynchronousSocketChannel.open();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            subscriber.onDispose(() -> {
                try {
                    socketChannel.close();
                } catch (Exception e) {
                    // ignore
                }
            });

            socketChannel.connect(new InetSocketAddress(hostname, port), null, new CompletionHandler<Void, AsynchronousSocketChannel>() {
                @Override
                public void completed(Void result, AsynchronousSocketChannel channel) {
                    subscriber.next(socketChannel);
                }

                @Override
                public void failed(Throwable exception, AsynchronousSocketChannel channel) {
                    subscriber.error(exception);
                }
            });
        }).cache(1, Duration.ofMillis(timeout)).next();
    }

    public Mono<ByteBuffer> read(ByteBuffer buffer) {
        return socketChannel$
                .flatMap(socketChannel -> {
                    return Mono.create(emitter -> {
                        socketChannel.read(buffer, null, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
                            @Override
                            public void completed(Integer count, AsynchronousSocketChannel channel) {
                                if (count < 0) {
                                    emitter.error(new IOException("Unexpected end of stream"));
                                    return;
                                }
                                buffer.flip();
                                emitter.success(buffer);
                            }

                            @Override
                            public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                                emitter.error(exc);
                            }
                        });
                    });
                });
    }

    public Mono<Void> write(ByteBuffer buffer) {
        return this.socketChannel$
                .flatMap(socketChannel -> {
                    return Mono.create(emitter -> {
                        socketChannel.write(buffer, null, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
                            @Override
                            public void completed(Integer result, AsynchronousSocketChannel attachment) {
                                emitter.success();
                            }

                            @Override
                            public void failed(Throwable exception, AsynchronousSocketChannel attachment) {
                                emitter.error(exception);
                            }
                        });
                    }).repeat(buffer::hasRemaining).then();
                });
    }
}
