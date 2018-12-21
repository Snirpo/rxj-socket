package com.snirpoapps.messaging;

import com.snirpoapps.messaging.messageconverter.MessageConverter;
import com.snirpoapps.messaging.messageconverter.SimpleMessageConverter;

public abstract class AbstractMessagingClient implements MessagingClient {
	private static final long DEFAULT_CONNECT_TIMEOUT = 10000;
	private static final long DEFAULT_MESSAGE_TIMEOUT = 10000;

	private long connectTimeout = DEFAULT_CONNECT_TIMEOUT;
	private long messageTimeout = DEFAULT_MESSAGE_TIMEOUT;

	private String replyTopic = "/reply";

	private MessageConverter messageConverter = new SimpleMessageConverter();

	protected long getConnectTimeout() {
		return connectTimeout;
	}

	protected long getMessageTimeout() {
		return messageTimeout;
	}

	protected String getReplyTopic() {
		return replyTopic;
	}

	protected MessageConverter getMessageConverter() {
		return messageConverter;
	}

	public void setConnectTimeout(long connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public void setMessageTimeout(long messageTimeout) {
		this.messageTimeout = messageTimeout;
	}

	public void setReplyTopic(String replyTopic) {
		this.replyTopic = replyTopic;
	}

	public void setMessageConverter(MessageConverter messageConverter) {
		this.messageConverter = messageConverter;
	}

}
