package com.snirpoapps.messaging.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.charset.StandardCharsets;

/**
 * Optimized ByteArrayOutputStream
 * 
 * 
 */
public final class DataOutputBuffer {
	private static final int DEFAULT_SIZE = 2048;
	private byte buf[];
	private int pos;

	/**
	 * Creates a new byte array output stream, with a buffer capacity of the
	 * specified size, in bytes.
	 * 
	 * @param size
	 *            the initial size.
	 * @exception IllegalArgumentException
	 *                if size is negative.
	 */
	public DataOutputBuffer(int size) {
		if (size < 0) {
			throw new IllegalArgumentException("Invalid size: " + size);
		}
		buf = new byte[size];
	}

	/**
	 * Creates a new byte array output stream.
	 */
	public DataOutputBuffer() {
		this(DEFAULT_SIZE);
	}

	/**
	 * Writes <code>len</code> bytes from the specified byte array starting at
	 * offset <code>off</code> to this byte array output stream.
	 * 
	 * @param b
	 *            the data.
	 * @param off
	 *            the start offset in the data.
	 * @param len
	 *            the number of bytes to write.
	 */
	public void write(byte b[], int off, int len) {
		if (len == 0) {
			return;
		}
		int newcount = pos + len;
		ensureEnoughBuffer(newcount);
		System.arraycopy(b, off, buf, pos, len);
		pos = newcount;
	}

	public void write(byte b[]) throws IOException {
		write(b, 0, b.length);
	}

	/**
	 * @return the underlying byte[] buffer
	 */
	public byte[] getData() {
		return buf;
	}

	/**
	 * reset the output stream
	 */
	public void reset() {
		pos = 0;
	}

	/**
	 * Set the current position for writing
	 * 
	 * @param offset
	 */
	public void position(int offset) {
		ensureEnoughBuffer(offset);
		pos = offset;
	}

	public int size() {
		return pos;
	}

	public void writeBoolean(boolean v) {
		ensureEnoughBuffer(1);
		buf[pos++] = (byte) (v ? 1 : 0);
	}

	public void writeByte(int v) {
		ensureEnoughBuffer(1);
		buf[pos++] = (byte) (v >>> 0);
	}

	public void writeShort(int v) {
		ensureEnoughBuffer(2);
		buf[pos++] = (byte) (v >>> 8);
		buf[pos++] = (byte) (v >>> 0);
	}

	public void writeChar(int v) {
		ensureEnoughBuffer(2);
		buf[pos++] = (byte) (v >>> 8);
		buf[pos++] = (byte) (v >>> 0);
	}

	public void writeInt(int v) {
		ensureEnoughBuffer(4);
		buf[pos++] = (byte) (v >>> 24);
		buf[pos++] = (byte) (v >>> 16);
		buf[pos++] = (byte) (v >>> 8);
		buf[pos++] = (byte) (v >>> 0);
	}

	public void writeLong(long v) {
		ensureEnoughBuffer(8);
		buf[pos++] = (byte) (v >>> 56);
		buf[pos++] = (byte) (v >>> 48);
		buf[pos++] = (byte) (v >>> 40);
		buf[pos++] = (byte) (v >>> 32);
		buf[pos++] = (byte) (v >>> 24);
		buf[pos++] = (byte) (v >>> 16);
		buf[pos++] = (byte) (v >>> 8);
		buf[pos++] = (byte) (v >>> 0);
	}

	public void writeFloat(float v) throws IOException {
		writeInt(Float.floatToIntBits(v));
	}

	public void writeDouble(double v) throws IOException {
		writeLong(Double.doubleToLongBits(v));
	}

	public void writeUTF8(String str) throws IOException {
		byte[] buffer = str.getBytes(StandardCharsets.UTF_8);
		if (buffer.length > 65535) {
			throw new UTFDataFormatException("encoded string too long: " + buffer.length + " bytes");
		}
		ensureEnoughBuffer(buffer.length + 2);
		writeShort(buffer.length);
		write(buffer);
	}

	public void toStream(OutputStream outputStream) throws IOException {
		outputStream.write(buf, 0, pos);
	}

	private void ensureEnoughBuffer(int count) {
		int newLength = pos + count;
		if (newLength > buf.length) {
			byte newbuf[] = new byte[Math.max(buf.length << 1, newLength)];
			System.arraycopy(buf, 0, newbuf, 0, pos);
			buf = newbuf;
		}
	}
}
