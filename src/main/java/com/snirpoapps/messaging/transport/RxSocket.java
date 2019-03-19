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
                    subscriber.next(new Connection(socketChannel, bufferSize));
                }

                @Override
                public void failed(Throwable exception, AsynchronousSocketChannel channel) {
                    subscriber.error(exception);
                }
            });
        });
    }

    public static RxSocket create() {
        return new RxSocket();
    }

    public class Connection {
        private final AsynchronousSocketChannel socketChannel;
        private final ByteBuffer outgoingData;
        private ByteBuffer incomingData;

        private Connection(AsynchronousSocketChannel socketChannel, int bufferSize) {
            this.socketChannel = socketChannel;
            this.outgoingData = ByteBuffer.allocateDirect(bufferSize);
            this.incomingData = ByteBuffer.allocateDirect(bufferSize);
        }

        public Mono<ByteBuffer> read() {
            return read(-1);
        }

        public Mono<ByteBuffer> read(int numBytes) {
            return Mono.create(emitter -> {
                // Return from buffer if there are enough bytes to be read
                if (incomingData.position() != 0) {
                    if (incomingData.hasRemaining()) {
                        emitter.success(incomingData);
                        return;
                    }

                    // Move remaining bytes to beginning of buffer
                    incomingData.compact();
                }

                // dynamic increase buffer size
                if (incomingData.remaining() < numBytes) {
                    incomingData = ByteBuffer.allocateDirect(incomingData.position() + numBytes).put(incomingData);
                }

                socketChannel.read(incomingData, null, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
                    @Override
                    public void completed(Integer count, AsynchronousSocketChannel channel) {
                        if (count < 0) {
                            emitter.error(new IOException("Unexpected end of stream"));
                            return;
                        }
                        //LOGGER.debug("INCOMING: " + new String(incomingData.array(), Charset.forName("UTF-8")));
                        incomingData.flip();
                        emitter.success(incomingData);
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
                //LOGGER.debug("OUTGOING: " + new String(buffer.array(), Charset.forName("UTF-8")));
                //TODO: should use outgoing buffer
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
