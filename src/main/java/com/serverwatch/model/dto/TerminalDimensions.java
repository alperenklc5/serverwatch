package com.serverwatch.model.dto;

/** PTY window size — columns (width) and rows (height). */
public record TerminalDimensions(int cols, int rows) {

    /** Default 80×24, the classic terminal size. */
    public static TerminalDimensions defaults() {
        return new TerminalDimensions(80, 24);
    }
}
