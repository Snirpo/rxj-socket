package com.snirpoapps.messaging.messageconverter;

public class SimpleMessageConverter implements MessageConverter {

	@Override
	public Object fromMessage(byte[] message) throws MessageConversionException {
		return message;
	}

	@Override
	public byte[] toMessage(Object obj) throws MessageConversionException {
		return (byte[]) obj;
	}

}
