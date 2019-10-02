package com.snirpoapps.messaging.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;

public class RxSSLSocket {
    private static final Logger LOGGER = LoggerFactory.getLogger(RxSSLSocket.class);

    private SSLContext sslContext;
    private String hostname;
    private int port;

    private RxSSLSocket() {
    }

    public static RxSSLSocket create() {
        return new RxSSLSocket();
    }

    public RxSSLSocket sslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    public RxSSLSocket hostname(String hostname) {
        this.hostname = hostname;
        return this;
    }

    public RxSSLSocket port(int port) {
        this.port = port;
        return this;
    }

    public Flux<Connection> connect() {
        final SSLContext sslContext = this.sslContext;
        final String hostname = this.hostname;
        final int port = this.port;

        return RxSocketChannel.create()
                .hostname(hostname)
                .port(port)
                .connect()
                .map(c -> new Connection(c, sslContext));
    }

    public static class Connection implements IConnection {
        private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

        private final RxSocketChannel.Connection connection;
        private final SSLEngine sslEngine;
        private ByteBuffer incomingPacketData;
        private ByteBuffer incomingAppData;
        private ByteBuffer outgoingPacketData;
        private UnicastProcessor<ConnectableFlux<Void>> writeProcessor = UnicastProcessor.create();
        private FluxSink<ConnectableFlux<Void>> writeSink = writeProcessor.sink();
        private Flux<ByteBuffer> read$;

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

            writeProcessor.concatMap(it -> it.autoConnect(0)).subscribe(); // Handle write errors

            this.read$ = Mono.defer(() -> {
                if (!incomingPacketData.hasRemaining()) {
                    incomingPacketData.clear();
                    return connection.read(incomingPacketData);
                }
                return Mono.just(incomingPacketData);
            })
                    .map(data -> {
                        incomingAppData.clear();
                        try {
                            return sslEngine.unwrap(data, incomingAppData);
                        } catch (SSLException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .repeat()
                    .takeUntil(it -> SSLEngineResult.Status.CLOSED.equals(it.getStatus()))
                    .flatMap(it -> {
                        LOGGER.debug("UNWRAP " + it);
                        switch (it.getStatus()) {
                            case OK:
                                incomingAppData.flip();
                                return Mono.just(incomingAppData.asReadOnlyBuffer());
                            case BUFFER_OVERFLOW:
                                // Will occur when peerAppData's capacity is smaller than the data derived from incomingAppData's unwrap.
                                this.incomingAppData = ByteBuffer.allocateDirect(sslEngine.getSession().getApplicationBufferSize());
                                return Mono.empty();
                            case BUFFER_UNDERFLOW:
                                // Will occur either when no data was read from the peer or when the incomingAppData buffer was too small to hold all peer's data.
                                this.incomingPacketData = ByteBuffer.allocateDirect(sslEngine.getSession().getPacketBufferSize()).put(incomingPacketData);
                                return Mono.empty();
                            case CLOSED:
                                // TODO
                        }
                        return Mono.error(new IllegalStateException("Invalid SSL status: " + it.getStatus()));
                    }).publish().autoConnect(0);
        }

        @Override
        public Flux<ByteBuffer> read() {
            return read$.filter(ByteBuffer::hasRemaining);
        }

        @Override
        public Mono<Void> write(ByteBuffer buffer) {
            ConnectableFlux<Void> write$ = doWrite(buffer)
                    .repeat(buffer::hasRemaining)
                    .replay();
            writeSink.next(write$);
            return write$.then();
        }

        private Mono<Void> doWrite(ByteBuffer buffer) {
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
                        return connection.write(outgoingPacketData)
                                .then(waitForHandshake(result));
                    case BUFFER_OVERFLOW:
                        // Will occur if there is not enough space in outgoingPacketData buffer to write all the data that would be generated by the method wrap.
                        // Since outgoingPacketData is set to session's packet size we should not get to this point because SSLEngine is supposed
                        // to produce messages smaller or equal to that, but a general handling would be the following:
                        outgoingPacketData = ByteBuffer.allocateDirect(sslEngine.getSession().getPacketBufferSize());
                        return doWrite(buffer);
                    case BUFFER_UNDERFLOW:
                        return Mono.error(new SSLException("Buffer underflow occurred after a wrap."));
                    case CLOSED:
                        // TODO
                }
                return Mono.error(new IllegalStateException("Invalid SSL status: " + result.getStatus()));
            });
        }

        private Mono<Void> waitForHandshake(SSLEngineResult result) {
            switch (result.getHandshakeStatus()) {
                case NEED_WRAP:
                    return doWrite(EMPTY_BUFFER);
                case NEED_UNWRAP:
                    return read$.next().then();
                case NEED_TASK:
                    return runDelegatedTasks();
                case NOT_HANDSHAKING:
                case FINISHED:
                    return Mono.empty();
            }
            return Mono.error(new IllegalStateException("Invalid handshake status: " + result.getHandshakeStatus()));
        }

        private Mono<Void> runDelegatedTasks() {
            Runnable runnable = sslEngine.getDelegatedTask();
            LOGGER.debug("TASK");
            if (runnable != null) {
                return Mono.fromRunnable(runnable)
                        .then(runDelegatedTasks());
            }
            return Mono.empty();
        }
    }
}
