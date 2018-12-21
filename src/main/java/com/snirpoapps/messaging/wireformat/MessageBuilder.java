package com.snirpoapps.messaging.wireformat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface MessageBuilder<T extends Message> {
	T decode(InputStream inputStream) throws IOException;

	void encode(T message, OutputStream outputStream) throws IOException;
}
