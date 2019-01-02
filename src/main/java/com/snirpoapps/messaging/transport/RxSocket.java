package com.snirpoapps.messaging.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;

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

        public Mono<byte[]> readBytes(byte[] dst, int offset, int length) {
            return readBytesFromSocket(length).map(buffer -> {
                buffer.get(dst, offset, length);
                return dst;
            });
        }

        public Mono<byte[]> readBytes(byte[] dst) {
            return readBytesFromSocket(dst.length).map(buffer -> {
                buffer.get(dst);
                return dst;
            });
        }

        public Mono<byte[]> readBytes(int length) {
            return readBytesFromSocket(length).map(buffer -> {
                byte[] dst = new byte[length];
                buffer.get(dst);
                return dst;
            });
        }

        public Mono<String> readUTF8() {
            // Not efficient
            return readUnsignedShort()
                    .flatMap(this::readBytes)
                    .map(val -> new String(val, StandardCharsets.UTF_8));
        }

        public Mono<Short> readUnsignedByte() {
            return readByte().map(val -> (short) (val & 0xff));
        }

        public Mono<Byte> readByte() {
            return readBytesFromSocket(1).map(ByteBuffer::get);
        }

        public Mono<Integer> readUnsignedShort() {
            return readShort().map(val -> val & 0xffff);
        }

        public Mono<Short> readShort() {
            return readBytesFromSocket(2).map(ByteBuffer::getShort);
        }

        public Mono<Character> readChar() {
            return readBytesFromSocket(2).map(ByteBuffer::getChar);
        }

        public Mono<Long> readUnsignedInt() {
            return readInt().map(val -> (long) val & 0xffffffffL);
        }

        public Mono<Integer> readInt() {
            return readBytesFromSocket(4).map(ByteBuffer::getInt);
        }

        public Mono<Long> readLong() {
            return readBytesFromSocket(8).map(ByteBuffer::getLong);
        }

        public Mono<Float> readFloat() {
            return readBytesFromSocket(4).map(ByteBuffer::getFloat);
        }

        public Mono<Double> readDouble() {
            return readBytesFromSocket(8).map(ByteBuffer::getDouble);
        }

        private Mono<ByteBuffer> readBytesFromSocket(int numBytes) {
            return Mono.<ByteBuffer>create(emitter -> {
                if (incomingData.remaining() >= numBytes) {
                    emitter.success(incomingData);
                    return;
                }

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
            });
        }
    }
}
