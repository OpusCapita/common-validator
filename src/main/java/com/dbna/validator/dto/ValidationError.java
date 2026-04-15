package com.dbna.validator.dto;

/**
 * A single validation issue reported during UBL document validation.
 *
 * @param severity    {@code FATAL}, {@code ERROR}, or {@code WARNING}
 * @param message     Human-readable description of the issue
 * @param lineNumber  1-based line number in the source document, or -1 if unknown
 * @param columnNumber 1-based column number in the source document, or -1 if unknown
 */
public record ValidationError(
        String severity,
        String message,
        int lineNumber,
        int columnNumber
) {

    /** Convenience factory – used when position information is unavailable. */
    public static ValidationError of(String severity, String message) {
        return new ValidationError(severity, message, -1, -1);
    }
}

