package com.snirpoapps.messaging.transport;

import com.snirpoapps.messaging.wireformat.WireFormat;
import io.reactivex.Completable;
import io.reactivex.Observable;

/**
 * Created by cprin on 14-9-2015.
 */
public abstract class AbstractTransport<T> {
    private WireFormat wireFormat;

    public AbstractTransport(WireFormat wireFormat) {
        this.wireFormat = wireFormat;
    }

    protected WireFormat getWireFormat() {
        return wireFormat;
    }

    public abstract Completable connect(final String hostname, final int port);

    public abstract Observable<ConnectionState> connection();

    public abstract Observable<T> messages();

    public abstract Completable disconnect();

    public abstract Completable send(T message);
}
