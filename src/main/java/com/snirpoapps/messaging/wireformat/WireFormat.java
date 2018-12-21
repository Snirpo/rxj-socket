package com.snirpoapps.messaging.wireformat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface WireFormat {

	void marshal(Object command, OutputStream outputStream) throws IOException;

	Object unmarshal(InputStream inputStream) throws IOException;

}
