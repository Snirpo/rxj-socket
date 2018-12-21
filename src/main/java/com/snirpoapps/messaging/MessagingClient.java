package com.snirpoapps.messaging;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;

public interface MessagingClient {
	Completable connect(String hostname, int port, final String username, final String password);

	Completable disconnect();

	<T> Completable send(String destination, T body);

	<T, R> Single<R> sendAndReceive(final String destination, final T body);

	public <T> Observable<T> subscribe(final String destination);
}
