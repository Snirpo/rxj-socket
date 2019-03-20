package com.snirpoapps.messaging.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;

public class RxSSLSocket {
    private static final Logger LOGGER = LoggerFactory.getLogger(RxSSLSocket.class);

    private SSLContext sslContext;
    private RxSocketChannel rxSocket;

    private RxSSLSocket() {
    }

    public static RxSSLSocket create() {
        return new RxSSLSocket();
    }

    public RxSSLSocket rxSocketChannel(RxSocketChannel rxSocket) {
        this.rxSocket = rxSocket;
        return this;
    }

    public RxSSLSocket sslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    public Flux<Connection> connect() {
        final SSLContext sslContext = this.sslContext;

        return rxSocket
                .connect()
                .map(c -> new Connection(c, sslContext));
    }

    public static class Connection {
        private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

        private final RxSocketChannel.Connection connection;
        private final SSLEngine sslEngine;
        private ByteBuffer incomingPacketData;
        private ByteBuffer incomingAppData;
        private ByteBuffer outgoingPacketData;

        public Connection(RxSocketChannel.Connection connection, SSLContext sslContext) {
            this.connection = connection;

            this.sslEngine = sslContext.createSSLEngine();
            this.sslEngine.setUseClientMode(true);

            try {
                sslEngine.beginHandshake();
            } catch (SSLException e) {
                throw new RuntimeException(e);
            }

            this.incomingAppData = ByteBuffer.allocateDirect(sslEngine.getSession().getApplicationBufferSize());
            this.incomingPacketData = ByteBuffer.allocateDirect(sslEngine.getSession().getPacketBufferSize());
            this.incomingPacketData.flip();

            this.outgoingPacketData = ByteBuffer.allocateDirect(sslEngine.getSession().getPacketBufferSize());
        }

        public Mono<ByteBuffer> read(int numBytes) {
            return doHandshake().then(doUnwrap());
        }

        private Mono<ByteBuffer> doRead() {
            LOGGER.debug("doRead");
            return connection.read(incomingPacketData);
        }

        private Mono<ByteBuffer> doUnwrap() {
            return Mono.defer(() -> {
                LOGGER.debug("doUnwrap");
                if (!incomingPacketData.hasRemaining()) {
                    incomingPacketData.clear();
                    return doRead().then(doUnwrap());
                }

                incomingAppData.clear();
                SSLEngineResult result;
                try {
                    result = sslEngine.unwrap(incomingPacketData, incomingAppData);
                } catch (SSLException e) {
                    throw new RuntimeException(e);
                }

                LOGGER.debug("UNWRAP " + result);
                switch (result.getStatus()) {
                    case OK:
                        incomingAppData.flip();
                        return Mono.just(incomingAppData);
                    case BUFFER_OVERFLOW:
                        // Will occur when peerAppData's capacity is smaller than the data derived from incomingAppData's unwrap.
                        this.incomingAppData = ByteBuffer.allocateDirect(sslEngine.getSession().getApplicationBufferSize());
                        return doUnwrap();
                    case BUFFER_UNDERFLOW:
                        // Will occur either when no data was read from the peer or when the incomingAppData buffer was too small to hold all peer's data.
                        this.incomingPacketData = ByteBuffer.allocateDirect(sslEngine.getSession().getPacketBufferSize()).put(incomingPacketData);
                        return doRead().then(doUnwrap());
                    case CLOSED:
                        // TODO
                }
                return Mono.error(new IllegalStateException("Invalid SSL status: " + result.getStatus()));
            });
        }

        private Mono<Void> doHandshake() {
            return Mono.defer(() -> {
                SSLEngineResult.HandshakeStatus status = sslEngine.getHandshakeStatus();
                LOGGER.debug("HANDSHAKE " + status);
                switch (status) {
                    case NEED_WRAP:
                        return doWrap(EMPTY_BUFFER)
                                .then(doHandshake());
                    case NEED_UNWRAP:
                        return doUnwrap()
                                .then(doHandshake());
                    case NEED_TASK:
                        return runDelegatedTasks()
                                .then(doHandshake());
                    case NOT_HANDSHAKING:
                    case FINISHED:
                        return Mono.empty();
                }
                return Mono.error(new IllegalStateException("Invalid handshake status: " + status));
            });
        }

        private Mono<Void> runDelegatedTasks() {
            Runnable runnable = sslEngine.getDelegatedTask();
            LOGGER.debug("TASK");
            if (runnable != null) {
                return Mono.fromRunnable(runnable).then(runDelegatedTasks());
            }
            return Mono.empty();
        }

        public Mono<Void> write(ByteBuffer buffer) {
            return doHandshake().then(doWrap(buffer));
        }

        private Mono<Void> doWrap(ByteBuffer buffer) {
            return Mono.defer(() -> {
                outgoingPacketData.clear();
                SSLEngineResult result;
                try {
                    result = sslEngine.wrap(buffer, outgoingPacketData);
                } catch (SSLException e) {
                    return Mono.error(e);
                }
                LOGGER.debug("WRAP " + result);

                switch (result.getStatus()) {
                    case OK:
                        outgoingPacketData.flip();
                        return connection.write(outgoingPacketData);
                    case BUFFER_OVERFLOW:
                        // Will occur if there is not enough space in outgoingPacketData buffer to write all the data that would be generated by the method wrap.
                        // Since outgoingPacketData is set to session's packet size we should not get to this point because SSLEngine is supposed
                        // to produce messages smaller or equal to that, but a general handling would be the following:
                        outgoingPacketData = ByteBuffer.allocateDirect(sslEngine.getSession().getPacketBufferSize());
                        return doWrap(buffer);
                    case BUFFER_UNDERFLOW:
                        return Mono.error(new SSLException("Buffer underflow occurred after a wrap."));
                    case CLOSED:
                        // TODO
                }
                return Mono.error(new IllegalStateException("Invalid SSL status: " + result.getStatus()));
            }).repeat(buffer::hasRemaining).then();
        }
    }
}
