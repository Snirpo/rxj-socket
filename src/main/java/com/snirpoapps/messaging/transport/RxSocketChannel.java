package com.snirpoapps.messaging.transport;

import lombok.AllArgsConstructor;
import lombok.Builder;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.time.Duration;

public class RxSocketChannel {
    private final Flux<Connection> connection$;

    @Builder
    private RxSocketChannel(String hostname, int port, int timeout) {
        connection$ = Flux.<Connection, AsynchronousSocketChannel>using(
                () -> {
                    try {
                        return AsynchronousSocketChannel.open();
                    } catch (IOException e) {
                        throw Exceptions.propagate(e);
                    }
                },
                socketChannel -> Flux.push(emitter -> {
                    socketChannel.connect(new InetSocketAddress(hostname, port), null, new CompletionHandler<Void, AsynchronousSocketChannel>() {
                        @Override
                        public void completed(Void result, AsynchronousSocketChannel channel) {
                            emitter.next(new Connection(socketChannel));
                        }

                        @Override
                        public void failed(Throwable exception, AsynchronousSocketChannel channel) {
                            emitter.error(exception);
                        }
                    });
                }),
                socketChannel -> {
                    try {
                        socketChannel.close();
                    } catch (IOException e) {
                        throw Exceptions.propagate(e);
                    }
                }
        ).replay(1).refCount(1, Duration.ofMillis(3000));
    }

    public Flux<Connection> connect() {
        return connection$;
    }

    @AllArgsConstructor
    public static class Connection {
        private final AsynchronousSocketChannel socketChannel;

        public Mono<ByteBuffer> read(ByteBuffer buffer) {
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
        }

        public Mono<Void> write(ByteBuffer buffer) {
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
        }
    }
}
