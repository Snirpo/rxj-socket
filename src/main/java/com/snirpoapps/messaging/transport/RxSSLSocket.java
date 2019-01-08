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
    private RxSocket rxSocket;

    private RxSSLSocket() {
    }

    public static RxSSLSocket create() {
        return new RxSSLSocket();
    }

    public RxSSLSocket rxSocket(RxSocket rxSocket) {
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

        private final RxSocket.Connection connection;
        private final SSLEngine sslEngine;
        private ByteBuffer incomingData;
        private ByteBuffer outgoingData;

        public Connection(RxSocket.Connection connection, SSLContext sslContext) {
            this.connection = connection;

            this.sslEngine = sslContext.createSSLEngine();
            this.sslEngine.setUseClientMode(true);

            this.incomingData = ByteBuffer.allocateDirect(sslEngine.getSession().getApplicationBufferSize());
            this.outgoingData = ByteBuffer.allocateDirect(sslEngine.getSession().getApplicationBufferSize());
        }

        public Mono<ByteBuffer> read(int numBytes) {
            return doHandshake()
                    .then(connection.read(numBytes))
                    .flatMap(this::doUnwrap);
        }

        private Mono<ByteBuffer> doUnwrap(ByteBuffer in) {
            SSLEngineResult result;
            try {
                result = sslEngine.unwrap(in, incomingData);
            } catch (SSLException e) {
                throw new RuntimeException(e);
            }

            LOGGER.debug("READ " + result);
            switch (result.getStatus()) {
                case OK:
                    return Mono.just(incomingData);
                case BUFFER_OVERFLOW:
                    // Will occur when peerAppData's capacity is smaller than the data derived from incomingData's unwrap.
                    this.incomingData = ByteBuffer.allocateDirect(sslEngine.getSession().getApplicationBufferSize());
                    return doUnwrap(in);
                case BUFFER_UNDERFLOW:
                    // Will occur either when no data was read from the peer or when the incomingData buffer was too small to hold all peer's data.
                    return read(sslEngine.getSession().getPacketBufferSize());
                case CLOSED:
                    // TODO
            }
            return Mono.error(new IllegalStateException("Invalid SSL status: " + result.getStatus()));
        }

        private Mono<Void> doHandshake() {
            return Mono.defer(() -> {
                SSLEngineResult.HandshakeStatus status = sslEngine.getHandshakeStatus();
                LOGGER.debug("HANDSHAKE " + status);
                switch (status) {
                    case NEED_WRAP:
                        return write(EMPTY_BUFFER);
                    case NEED_UNWRAP:
                        return read(sslEngine.getSession().getPacketBufferSize()).ignoreElement();
                    case NEED_TASK:
                        return runDelegatedTasks();
                    case FINISHED:
                    case NOT_HANDSHAKING:
                        return Mono.empty();
                }
                return Mono.error(new IllegalStateException("Invalid handshake status: " + status));
            }).repeat(() -> SSLEngineResult.HandshakeStatus.FINISHED.equals(sslEngine.getHandshakeStatus()) ||
                    SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING.equals(sslEngine.getHandshakeStatus())).then();
        }

        private Mono<Void> runDelegatedTasks() {
            Mono<Void> out = Mono.empty();
            Runnable task;
            while ((task = sslEngine.getDelegatedTask()) != null) {
                out = out.mergeWith(Mono.fromRunnable(task)).then();
            }
            return out;
        }

        private Mono<Void> write(ByteBuffer buffer) {
            return Mono.defer(() -> {
                outgoingData.clear();
                SSLEngineResult result;
                try {
                    result = sslEngine.wrap(buffer, outgoingData);
                } catch (SSLException e) {
                    return Mono.error(e);
                }
                LOGGER.debug("WRITE " + result);

                switch (result.getStatus()) {
                    case OK:
                        outgoingData.flip();
                        return connection.write(outgoingData);
                    case BUFFER_OVERFLOW:
                        // Will occur if there is not enough space in outgoingData buffer to write all the data that would be generated by the method wrap.
                        // Since outgoingData is set to session's packet size we should not get to this point because SSLEngine is supposed
                        // to produce messages smaller or equal to that, but a general handling would be the following:
                        outgoingData = ByteBuffer.allocateDirect(sslEngine.getSession().getPacketBufferSize());
                        return write(buffer);
                    case BUFFER_UNDERFLOW:
                        return Mono.error(new SSLException("Buffer underflow occurred after a wrap."));
                    case CLOSED:
                        // TODO
                }
                return Mono.error(new IllegalStateException("Invalid SSL status: " + result.getStatus()));
            }).repeat(() -> !buffer.hasRemaining()).then();
        }
    }
}
