package com.snirpoapps.messaging.messageconverter;

/**
 * Created by cprin on 21-11-2015.
 */
public class MessageConversionException extends Exception {
	private static final long serialVersionUID = -7873926714354928392L;

	public MessageConversionException() {
	}

	public MessageConversionException(String detailMessage) {
		super(detailMessage);
	}

	public MessageConversionException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

	public MessageConversionException(Throwable throwable) {
		super(throwable);
	}
}
