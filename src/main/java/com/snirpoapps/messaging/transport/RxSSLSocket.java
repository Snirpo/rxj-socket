package com.snirpoapps.messaging.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

public class RxSSLSocket {
    private static final Logger LOGGER = LoggerFactory.getLogger(RxSSLSocket.class);
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private final SSLContext sslContext;

    private AsynchronousSocketChannel socketChannel;

    private SSLEngine sslEngine;

    private ByteBuffer outgoingData;
    private ByteBuffer incomingData;

    public RxSSLSocket(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    private void initEngine() {
        this.sslEngine = sslContext.createSSLEngine();
        this.sslEngine.setUseClientMode(true);
    }

    private void initBuffers() {
        SSLSession sslSession = this.sslEngine.getSession();
        outgoingData = ByteBuffer.allocateDirect(sslSession.getPacketBufferSize());
        incomingData = ByteBuffer.allocateDirect(sslSession.getPacketBufferSize());
    }

    private Mono<ByteBuffer> doRead() {
        return Mono.defer(() -> {
            if (incomingData.position() != 0) {
                return doUnwrap();
            }
            return readFromSocket().then(doUnwrap());
        });
    }

    private Mono<ByteBuffer> doUnwrap() {
        return Mono.defer(() -> {
            ByteBuffer buffer = ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());

            incomingData.flip();
            SSLEngineResult result;
            try {
                result = sslEngine.unwrap(incomingData, buffer);
            } catch (SSLException e) {
                return Mono.error(e);
            }
            incomingData.compact();

            LOGGER.debug("READ " + result);
            switch (result.getStatus()) {
                case OK:
                    return Mono.just(buffer);
                case BUFFER_OVERFLOW:
                    // Will occur when peerAppData's capacity is smaller than the data derived from incomingData's unwrap.
                    return doUnwrap();
                case BUFFER_UNDERFLOW:
                    // Will occur either when no data was read from the peer or when the incomingData buffer was too small to hold all peer's data.
                    if (sslEngine.getSession().getPacketBufferSize() < incomingData.limit()) {
                        return Mono.empty();
                    }
                    incomingData.flip();
                    incomingData = ByteBuffer.allocateDirect(sslEngine.getSession().getPacketBufferSize()).put(incomingData);
                    return doRead();
                case CLOSED:
                    return disconnect().then(Mono.empty());
            }
            return Mono.error(new IllegalStateException("Invalid SSL status: " + result.getStatus()));
        });
    }

    private Mono<Void> readFromSocket() {
        return Mono.create(emitter -> {
            LOGGER.debug("READ_FROM_SOCKET");
            socketChannel.read(incomingData, socketChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
                @Override
                public void completed(Integer count, AsynchronousSocketChannel channel) {
                    if (count < 0) {
                        try {
                            sslEngine.closeInbound();
                        } catch (SSLException e) {
                            // ignore
                            //log.error("This engine was forced to close inbound, without having received the proper SSL/TLS close notification message from the peer, due to end of stream.");
                        }
                        sslEngine.closeOutbound();
                    }
                    //LOGGER.debug("INCOMING: " + new String(incomingData.array(), Charset.forName("UTF-8")));
                    emitter.success();
                }

                @Override
                public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                    emitter.error(exc);
                }
            });
        });
    }

    public Mono<Void> connect(final String hostname, final int port) {
        return Mono.create(subscriber -> {
            LOGGER.debug("CONNECT");

            initEngine();
            initBuffers();
            try {
                socketChannel = AsynchronousSocketChannel.open();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            //subscriber.setCancellable(socketChannel::close);
            //try to connect to the server side
            socketChannel.connect(new InetSocketAddress(hostname, port), socketChannel, new CompletionHandler<Void, AsynchronousSocketChannel>() {
                @Override
                public void completed(Void result, AsynchronousSocketChannel channel) {
                    LOGGER.debug("CONNECTED");
                    try {
                        sslEngine.beginHandshake();
                    } catch (SSLException e) {
                        subscriber.error(e);
                    }
                    subscriber.success();
                }

                @Override
                public void failed(Throwable exception, AsynchronousSocketChannel channel) {
                    subscriber.error(exception);
                }
            });
        });
    }

    private Mono<Void> doHandshake() {
        return Mono.defer(() -> {
            SSLEngineResult.HandshakeStatus status = sslEngine.getHandshakeStatus();
            LOGGER.debug("HANDSHAKE " + status);
            switch (status) {
                case NEED_WRAP:
                    return send(EMPTY_BUFFER);
                case NEED_UNWRAP:
                    return doRead().ignoreElement();
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

    public Mono<Void> disconnect() {
        return Mono.defer(() -> {
            LOGGER.debug("DISCONNECT");
            sslEngine.closeOutbound();
            return doHandshake()
                    .then(disposeSocketChannel());
        });
    }

    private Mono<Void> disposeSocketChannel() {
        return Mono.fromRunnable(() -> {
            try {
                socketChannel.close();
            } catch (IOException e) {
                // ignore
            }
            socketChannel = null;
        });
    }

    public Mono<Void> send(final ByteBuffer buffer) {
        return Mono.defer(() -> {
            outgoingData.clear();
            SSLEngineResult result = null;
            try {
                result = sslEngine.wrap(buffer, outgoingData);
            } catch (SSLException e) {
                return Mono.error(e);
            }
            LOGGER.debug("WRITE " + result);

            switch (result.getStatus()) {
                case OK:
                    outgoingData.flip();
                    return writeToSocket(outgoingData);
                case BUFFER_OVERFLOW:
                    // Will occur if there is not enough space in outgoingData buffer to write all the data that would be generated by the method wrap.
                    // Since outgoingData is set to session's packet size we should not get to this point because SSLEngine is supposed
                    // to produce messages smaller or equal to that, but a general handling would be the following:
                    outgoingData = ByteBuffer.allocateDirect(sslEngine.getSession().getPacketBufferSize());
                    return send(buffer);
                case BUFFER_UNDERFLOW:
                    return Mono.error(new SSLException("Buffer underflow occurred after a wrap."));
                case CLOSED:
                    return disconnect();
            }
            return Mono.error(new IllegalStateException("Invalid SSL status: " + result.getStatus()));
        }).repeat(() -> !buffer.hasRemaining()).then();

    }

    private Mono<Void> writeToSocket(ByteBuffer buffer) {
        return Mono.create(emitter -> {
            //LOGGER.debug("OUTGOING: " + new String(buffer.array(), Charset.forName("UTF-8")));
            socketChannel.write(buffer, socketChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
                @Override
                public void completed(Integer result, AsynchronousSocketChannel attachment) {
                    emitter.success();
                }

                @Override
                public void failed(Throwable exception, AsynchronousSocketChannel attachment) {
                    emitter.error(exception);
                }
            });
        }).repeat(() -> !buffer.hasRemaining()).then();
    }

    public Flux<ByteBuffer> open(String hostname, int port) {
        return this.connect(hostname, port).thenMany(
                doHandshake()
                        .then(doRead())
                        .repeat(() -> sslEngine.isInboundDone() && sslEngine.isOutboundDone())
                        .doOnError(err -> LOGGER.error("ERROR", err))
                //.onErrorResumeNext(disconnect().toFlowable())
        );
    }

    private Mono<Void> runDelegatedTasks() {
        return Mono.defer(() -> {
            Mono<Void> out = Mono.empty();
            Runnable task;
            while ((task = sslEngine.getDelegatedTask()) != null) {
                out = out.mergeWith(Mono.fromRunnable(task)).then();
            }
            return out;
        });
    }
}
