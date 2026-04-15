package com.dbna.validator.dto;

import java.util.List;

/**
 * Response returned by the UBL validation endpoint.
 *
 * @param valid        {@code true} when no ERROR or FATAL issues were found
 * @param documentType Root-element local name, e.g. {@code "Invoice"}
 * @param ublVersion   Detected (or requested) UBL version, e.g. {@code "2.1"}
 * @param errors       Ordered list of validation issues; empty when valid
 */
public record ValidationResponse(
        boolean valid,
        String documentType,
        String ublVersion,
        List<ValidationError> errors
) {

    /** Convenience factory for an invalid response with a single error. */
    public static ValidationResponse invalid(String documentType,
                                             String ublVersion,
                                             ValidationError error) {
        return new ValidationResponse(false, documentType, ublVersion, List.of(error));
    }
}

