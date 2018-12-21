package com.snirpoapps.messaging.transport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.spi.AsynchronousChannelProvider;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TLSSocketChannel extends AsynchronousSocketChannel {

    private final AsynchronousSocketChannel socketChannel;

    public static TLSSocketChannel open() {
        try {
            return new TLSSocketChannel(AsynchronousSocketChannel.open());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private TLSSocketChannel(AsynchronousSocketChannel socketChannel) {
        super(AsynchronousChannelProvider.provider());
        this.socketChannel = socketChannel;
    }

    @Override
    public AsynchronousSocketChannel bind(SocketAddress local) throws IOException {
        return this.socketChannel.bind(local);
    }

    @Override
    public <T> AsynchronousSocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        return this.socketChannel.setOption(name, value);
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        return this.socketChannel.getOption(name);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return this.socketChannel.supportedOptions();
    }

    @Override
    public AsynchronousSocketChannel shutdownInput() throws IOException {
        return this.socketChannel.shutdownInput();
    }

    @Override
    public AsynchronousSocketChannel shutdownOutput() throws IOException {
        return this.socketChannel.shutdownOutput();
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        return this.socketChannel.getRemoteAddress();
    }

    @Override
    public <A> void connect(SocketAddress remote, A attachment, CompletionHandler<Void, ? super A> handler) {
        this.socketChannel.connect(remote, attachment, handler);
    }

    @Override
    public Future<Void> connect(SocketAddress remote) {
        return this.socketChannel.connect(remote);
    }

    @Override
    public <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        this.socketChannel.read(dst, timeout, unit, attachment, handler);
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        return this.socketChannel.read(dst);
    }

    @Override
    public <A> void read(ByteBuffer[] dsts, int offset, int length, long timeout, TimeUnit unit, A attachment, CompletionHandler<Long, ? super A> handler) {
        this.socketChannel.read(dsts, offset, length, timeout, unit, attachment, handler);
    }

    @Override
    public <A> void write(ByteBuffer src, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        this.socketChannel.write(src, timeout, unit, attachment, handler);
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        return this.socketChannel.write(src);
    }

    @Override
    public <A> void write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit, A attachment, CompletionHandler<Long, ? super A> handler) {
        this.socketChannel.write(srcs, offset, length, timeout, unit, attachment, handler);
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return this.socketChannel.getLocalAddress();
    }

    @Override
    public boolean isOpen() {
        return this.socketChannel.isOpen();
    }

    @Override
    public void close() throws IOException {
        this.socketChannel.close();
    }
}
