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

public class RxSocketChannel {
    private static final Logger LOGGER = LoggerFactory.getLogger(RxSocketChannel.class);

    private String hostname;
    private int port;

    private RxSocketChannel() {
    }

    public RxSocketChannel hostname(String hostname) {
        this.hostname = hostname;
        return this;
    }

    public RxSocketChannel port(int port) {
        this.port = port;
        return this;
    }

    public Flux<Connection> connect() {
        final String hostname = this.hostname;
        final int port = this.port;

        return Flux.<Connection>create(subscriber -> {
            AsynchronousSocketChannel socketChannel;

            try {
                socketChannel = AsynchronousSocketChannel.open();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            subscriber.onDispose(() -> {
                try {
                    socketChannel.close();
                } catch (IOException e) {
                    // ignore
                }
            });

            socketChannel.connect(new InetSocketAddress(hostname, port), null, new CompletionHandler<Void, AsynchronousSocketChannel>() {
                @Override
                public void completed(Void result, AsynchronousSocketChannel channel) {
                    subscriber.next(new Connection(socketChannel));
                }

                @Override
                public void failed(Throwable exception, AsynchronousSocketChannel channel) {
                    subscriber.error(exception);
                }
            });
        });
    }

    public static RxSocketChannel create() {
        return new RxSocketChannel();
    }

    public class Connection {
        private final AsynchronousSocketChannel socketChannel;

        private Connection(AsynchronousSocketChannel socketChannel) {
            this.socketChannel = socketChannel;
        }

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
