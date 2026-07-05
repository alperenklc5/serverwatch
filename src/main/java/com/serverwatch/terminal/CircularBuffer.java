package com.serverwatch.terminal;

/**
 * Thread-safe ring buffer that retains the last {@code maxSize} characters of terminal output.
 *
 * <p>When the buffer exceeds capacity the oldest content is discarded so that
 * recent output is always available for reconnecting clients.
 */
public class CircularBuffer {

    private final StringBuilder buffer;
    private final int maxSize;

    public CircularBuffer(int maxSize) {
        this.maxSize = maxSize;
        this.buffer = new StringBuilder(Math.min(maxSize, 65_536));
    }

    /**
     * Appends {@code data} to the buffer, evicting the oldest content if the
     * capacity would be exceeded.
     */
    public synchronized void append(String data) {
        if (data == null || data.isEmpty()) return;
        buffer.append(data);
        if (buffer.length() > maxSize) {
            buffer.delete(0, buffer.length() - maxSize);
        }
    }

    /** Returns all currently buffered content as a single string. */
    public synchronized String getAll() {
        return buffer.toString();
    }

    /** Clears the buffer. */
    public synchronized void clear() {
        buffer.setLength(0);
    }

    /** Returns the current number of buffered characters. */
    public synchronized int size() {
        return buffer.length();
    }
}
