package com.dbna.validator.controller;

import com.dbna.validator.dto.ValidationResponse;
import com.dbna.validator.service.UblValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing UBL document validation endpoints.
 *
 * <p>Swagger UI available at {@code /swagger-ui.html} when the application is running.
 */
@RestController
@RequestMapping("/api/v1/ubl")
@Tag(name = "UBL Validation", description = "Validate Universal Business Language (UBL) XML documents")
public class UblValidationController {

    private final UblValidationService validationService;

    public UblValidationController(UblValidationService validationService) {
        this.validationService = validationService;
    }

    /**
     * Validates a UBL XML document against the appropriate XSD schema.
     *
     * <p>The HTTP response is always {@code 200 OK}; use the {@code valid} field
     * in the JSON body to determine whether the document passed validation.
     *
     * @param xmlContent  Raw UBL XML document body
     * @param version     Optional UBL version override ({@code 2.1}, {@code 2.2}, {@code 2.3}).
     *                    When omitted the version is auto-detected from the
     *                    {@code UBLVersionID} element.
     * @return            A {@link ValidationResponse} with validation outcome and error details
     */
    @Operation(
            summary = "Validate a UBL XML document",
            description = """
                    Submits a UBL 2.3 XML document for three-stage validation:
                    1. XML well-formedness check
                    2. UBL document-type detection
                    3. XSD schema validation using bundled ph-ubl 2.3 schemas
                    
                    Supported document types include Invoice, CreditNote, Order, DespatchAdvice, and all other standard UBL 2.3 document types.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "The UBL XML document to validate",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_XML_VALUE,
                            examples = @ExampleObject(
                                    name = "Minimal UBL 2.3 Invoice",
                                    externalValue = "/examples/ubl23-invoice-example.xml"
                            )
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Validation completed (check the `valid` field for the outcome)",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = ValidationResponse.class)
                            )
                    )
            }
    )
    @PostMapping(
            value = "/validate",
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE, MediaType.TEXT_PLAIN_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ValidationResponse> validate(
            @RequestBody String xmlContent,
            @Parameter(description = "UBL version override. Only 2.3 is supported; omit to auto-detect.")
            @RequestParam(required = false) String version
    ) {
        ValidationResponse response = validationService.validate(xmlContent, version);
        return ResponseEntity.ok(response);
    }
}

