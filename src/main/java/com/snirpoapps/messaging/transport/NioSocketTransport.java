package com.snirpoapps.messaging.transport;

import com.snirpoapps.messaging.wireformat.WireFormat;
import io.reactivex.*;
import io.reactivex.subjects.PublishSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

/**
 * Created by cprin on 5-2-2016.
 */
public class NioSocketTransport extends AbstractTransport<ByteBuffer> {
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
    private final SSLContext sslContext;

    private PublishSubject<Single<ByteBuffer>> events$ = PublishSubject.create();

    private Logger LOGGER = LoggerFactory.getLogger(NioSocketTransport.class);

    private AsynchronousSocketChannel socketChannel;

    private SSLEngine sslEngine;

    private ByteBuffer outgoingData;
    private ByteBuffer incomingData;

    public NioSocketTransport(SSLContext sslContext, WireFormat wireFormat) {
        super(wireFormat);
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

    private Maybe<ByteBuffer> doRead() {
        return Maybe.defer(() -> {
            if (incomingData.position() != 0) {
                return doUnwrap();
            }
            return readFromSocket().andThen(doUnwrap());
        });
    }

    private Maybe<ByteBuffer> doUnwrap() {
        return Maybe.defer(() -> {
            ByteBuffer buffer = ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());

            incomingData.flip();
            SSLEngineResult result = sslEngine.unwrap(incomingData, buffer);
            incomingData.compact();

            LOGGER.debug("READ " + result);
            switch (result.getStatus()) {
                case OK:
                    return Maybe.just(buffer);
                case BUFFER_OVERFLOW:
                    // Will occur when peerAppData's capacity is smaller than the data derived from incomingData's unwrap.
                    return doUnwrap();
                case BUFFER_UNDERFLOW:
                    // Will occur either when no data was read from the peer or when the incomingData buffer was too small to hold all peer's data.
                    if (sslEngine.getSession().getPacketBufferSize() < incomingData.limit()) {
                        return Maybe.empty();
                    }
                    incomingData.flip();
                    incomingData = ByteBuffer.allocateDirect(sslEngine.getSession().getPacketBufferSize()).put(incomingData);
                    return doRead();
                case CLOSED:
                    return disconnect().andThen(Maybe.empty());
            }
            return Maybe.error(new IllegalStateException("Invalid SSL status: " + result.getStatus()));
        });
    }

    private Completable readFromSocket() {
        return Completable.create(emitter -> {
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
                    emitter.onComplete();
                }

                @Override
                public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                    emitter.onError(exc);
                }
            });
        });
    }

    @Override
    public synchronized Completable connect(final String hostname, final int port) {
        return Completable.create(subscriber -> {
            LOGGER.debug("CONNECT");

            initEngine();
            initBuffers();
            socketChannel = AsynchronousSocketChannel.open();

            //subscriber.setCancellable(socketChannel::close);
            //try to connect to the server side
            socketChannel.connect(new InetSocketAddress(hostname, port), socketChannel, new CompletionHandler<Void, AsynchronousSocketChannel>() {
                @Override
                public void completed(Void result, AsynchronousSocketChannel channel) {
                    LOGGER.debug("CONNECTED");
                    try {
                        sslEngine.beginHandshake();
                    } catch (SSLException e) {
                        subscriber.onError(e);
                    }
                    subscriber.onComplete();
                }

                @Override
                public void failed(Throwable exception, AsynchronousSocketChannel channel) {
                    subscriber.onError(exception);
                }
            });
        });
    }

    private Completable doHandshake() {
        return Completable.defer(() -> {
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
                    return Completable.complete();
            }
            return Completable.error(new IllegalStateException("Invalid handshake status: " + status));
        }).repeatUntil(() -> SSLEngineResult.HandshakeStatus.FINISHED.equals(sslEngine.getHandshakeStatus()) ||
                SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING.equals(sslEngine.getHandshakeStatus()));
    }

    @Override
    public synchronized Completable disconnect() {
        return Completable.defer(() -> {
            LOGGER.debug("DISCONNECT");
            sslEngine.closeOutbound();
            return doHandshake()
                    .andThen(disposeSocketChannel());
        });
    }

    private Completable disposeSocketChannel() {
        return Completable.fromRunnable(() -> {
            try {
                socketChannel.close();
            } catch (IOException e) {
                // ignore
            }
            socketChannel = null;
        });
    }

    @Override
    public Completable send(final ByteBuffer buffer) {
        return Completable.defer(() -> {
            outgoingData.clear();
            SSLEngineResult result = sslEngine.wrap(buffer, outgoingData);
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
                    return Completable.error(new SSLException("Buffer underflow occurred after a wrap."));
                case CLOSED:
                    return disconnect();
            }
            return Completable.error(new IllegalStateException("Invalid SSL status: " + result.getStatus()));
        }).repeatUntil(() -> !buffer.hasRemaining());

    }

    private Completable writeToSocket(ByteBuffer buffer) {
        return Completable.create(emitter -> {
            //LOGGER.debug("OUTGOING: " + new String(buffer.array(), Charset.forName("UTF-8")));
            socketChannel.write(buffer, socketChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
                @Override
                public void completed(Integer result, AsynchronousSocketChannel attachment) {
                    emitter.onComplete();
                }

                @Override
                public void failed(Throwable exception, AsynchronousSocketChannel attachment) {
                    emitter.onError(exception);
                }
            });
        }).repeatUntil(() -> !buffer.hasRemaining());
    }

    public Flowable<ByteBuffer> open(String hostname, int port) {
        return this.connect(hostname, port).andThen(
                doHandshake()
                        .andThen(doRead())
                        .repeatUntil(() -> sslEngine.isInboundDone() && sslEngine.isOutboundDone())
                        .doOnError(err -> LOGGER.error("ERROR", err))
                        .onErrorResumeNext(disconnect().toFlowable())
        );
    }

    @Override
    public Observable<ByteBuffer> messages() {
        return null;
    }

    @Override
    public Observable<ConnectionState> connection() {
        throw new RuntimeException("Not supported");
    }

    private Completable runDelegatedTasks() {
        return Completable.defer(() -> {
            Completable out = Completable.complete();
            Runnable task;
            while ((task = sslEngine.getDelegatedTask()) != null) {
                out = out.mergeWith(Completable.fromRunnable(task));
            }
            return out;
        });
    }
}
