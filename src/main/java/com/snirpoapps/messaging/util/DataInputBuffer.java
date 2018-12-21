package com.snirpoapps.messaging.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Optimized ByteArrayInputStream that can be used more than once
 * 
 * 
 */
public final class DataInputBuffer {
	private byte[] buf;
	private int pos;

	public DataInputBuffer(byte buf[]) {
		this.buf = buf;
		this.pos = 0;
	}

	public int size() {
		return pos;
	}

	public int read(byte b[], int off, int len) {
		if (b == null) {
			throw new NullPointerException();
		}
		if (pos >= buf.length) {
			return -1;
		}
		if (pos + len > buf.length) {
			len = buf.length - pos;
		}
		if (len <= 0) {
			return 0;
		}
		System.arraycopy(buf, pos, b, off, len);
		pos += len;
		return len;
	}

	public int available() {
		return buf.length - pos;
	}

	public void readFully(byte[] b) {
		read(b, 0, b.length);
	}

	public void readFully(byte[] b, int off, int len) {
		read(b, off, len);
	}

	public byte[] readRemaining() {
		byte[] b = new byte[available()];
		readFully(b);
		return b;
	}

	public int skipBytes(int n) {
		if (pos + n > buf.length) {
			n = buf.length - pos;
		}
		if (n < 0) {
			return 0;
		}
		pos += n;
		return n;
	}

	public boolean readBoolean() throws EOFException {
		return readUnsignedByte() != 0;
	}

	public byte readByte() throws EOFException {
		return (byte) readUnsignedByte();
	}

	public int readUnsignedByte() throws EOFException {
		ensureEnoughBuffer(1);
		return read();
	}

	public short readShort() throws EOFException {
		return (short) readUnsignedShort();
	}

	public int readUnsignedShort() throws EOFException {
		ensureEnoughBuffer(2);
		return (read() << 8) + (read() << 0);
	}

	public char readChar() throws EOFException {
		return (char) readUnsignedShort();
	}

	public int readInt() throws EOFException {
		ensureEnoughBuffer(4);
		return (read() << 24) + (read() << 16) + (read() << 8) + (read() << 0);
	}

	public long readLong() throws EOFException {
		ensureEnoughBuffer(8);
		return ((long) read() << 56) + ((long) read() << 48) + ((long) read() << 40) + ((long) read() << 32)
				+ ((long) read() << 24) + (read() << 16) + (read() << 8) + (read() << 0);
	}

	public float readFloat() throws EOFException {
		return Float.intBitsToFloat(readInt());
	}

	public double readDouble() throws EOFException {
		return Double.longBitsToDouble(readLong());
	}

	public String readUTF8() throws EOFException {
		int length = readUnsignedShort();
		ensureEnoughBuffer(length);
		int start = pos;
		pos += length;
		return new String(buf, start, length, StandardCharsets.UTF_8);
	}

	private int read() {
		return (buf[pos++] & 0xFF);
	}

	private void ensureEnoughBuffer(int count) throws EOFException {
		if (pos + count > buf.length)
			throw new EOFException();
	}

	public static DataInputBuffer fromStream(InputStream inputStream, int length) throws IOException {
		byte[] buffer = new byte[length];
		if (length < 0)
			throw new IndexOutOfBoundsException();
		int n = 0;
		while (n < length) {
			int count = inputStream.read(buffer, n, length - n);
			if (count < 0)
				throw new EOFException();
			n += count;
		}
		return new DataInputBuffer(buffer);
	}
}
