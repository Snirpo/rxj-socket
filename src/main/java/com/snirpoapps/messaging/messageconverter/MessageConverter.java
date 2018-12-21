package com.snirpoapps.messaging.messageconverter;

/**
 * Created by cprin on 8-9-2015.
 */
public interface MessageConverter {
	Object fromMessage(byte[] message) throws MessageConversionException;

	byte[] toMessage(Object obj) throws MessageConversionException;
}
