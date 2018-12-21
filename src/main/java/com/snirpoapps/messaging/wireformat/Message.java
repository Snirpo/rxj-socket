package com.snirpoapps.messaging.wireformat;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface Message {
	void decode(DataInputStream inputStream) throws IOException;

	void encode(DataOutputStream outputStream) throws IOException;
}
