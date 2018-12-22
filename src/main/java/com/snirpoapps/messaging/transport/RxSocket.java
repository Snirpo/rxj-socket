package com.snirpoapps.messaging.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.function.Function;

public class RxSocket {
    private static final Logger LOGGER = LoggerFactory.getLogger(RxSocket.class);

    private int bufferSize = 2048;

    public RxSocket() {
    }

    public RxSocket bufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
        return this;
    }

    public void connect() {

    }

    public static RxSocket create() {
        return new RxSocket();
    }

    public class Connection {
        private final AsynchronousSocketChannel socketChannel;
        private final ByteBuffer outgoingData;
        private final ByteBuffer incomingData;

        private Connection(AsynchronousSocketChannel socketChannel) {
            this.socketChannel = socketChannel;
            this.outgoingData = ByteBuffer.allocateDirect(bufferSize);
            this.incomingData = ByteBuffer.allocateDirect(bufferSize);
        }

        public Flux<ByteBuffer> read() {
            return Mono.<ByteBuffer>create(emitter -> {
                LOGGER.debug("READ_FROM_SOCKET");
                socketChannel.read(incomingData, socketChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
                    @Override
                    public void completed(Integer count, AsynchronousSocketChannel channel) {
                        if (count < 0) {
                            emitter.success();
                        }
                        //LOGGER.debug("INCOMING: " + new String(incomingData.array(), Charset.forName("UTF-8")));
                        emitter.success(incomingData);
                    }

                    @Override
                    public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                        emitter.error(exc);
                    }
                });
            }).repeatWhen(Function.identity());
        }
    }
}
