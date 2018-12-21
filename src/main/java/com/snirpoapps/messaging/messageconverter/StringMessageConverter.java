package com.snirpoapps.messaging.messageconverter;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StringMessageConverter implements MessageConverter {

	private Charset charset = StandardCharsets.UTF_8;

	public StringMessageConverter() {
	}

	public StringMessageConverter(Charset charset) {
		this.charset = charset;
	}

	@Override
	public Object fromMessage(byte[] message) throws MessageConversionException {
		return new String(message, charset);
	}

	@Override
	public byte[] toMessage(Object obj) throws MessageConversionException {
		return ((String) obj).getBytes(charset);
	}

}
