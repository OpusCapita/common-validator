package com.dbna.validator.service;

import com.dbna.validator.config.XmlSecurityConfig;
import com.dbna.validator.dto.ValidationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UblValidationService}.
 * Uses the real ph-ubl schema resolution (no mocks).
 */
class UblValidationServiceTest {

    private UblValidationService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new UblValidationService(new XmlSecurityConfig().documentBuilderFactory());
    }

    // -------------------------------------------------------------------------
    // Valid document tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Valid UBL 2.3 Invoice should pass validation")
    void validUbl23Invoice_shouldPass() throws IOException {
        String xml = readResource("/ubl23-invoice-valid.xml");

        ValidationResponse response = service.validate(xml, null);

        assertThat(response.valid()).isTrue();
        assertThat(response.documentType()).isEqualTo("Invoice");
        assertThat(response.ublVersion()).isEqualTo("2.3");
        assertThat(response.errors()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Malformed XML tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Non-XML content should return a FATAL parse error")
    void nonXmlContent_shouldReturnFatalError() {
        String content = "not xml at all";

        ValidationResponse response = service.validate(content, null);

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).isNotEmpty();
        assertThat(response.errors().getFirst().severity()).isEqualTo("FATAL");
    }

    @Test
    @DisplayName("Unclosed tag should return a FATAL parse error")
    void unclosedTag_shouldReturnFatalError() throws IOException {
        String brokenXml = readResource("/ubl-invoice-unclosed-tag.xml");

        ValidationResponse response = service.validate(brokenXml, null);

        assertThat(response.valid()).isFalse();
        assertThat(response.errors().getFirst().severity()).isEqualTo("FATAL");
    }

    // -------------------------------------------------------------------------
    // Unknown document type
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Unknown UBL document type should report an error")
    void unknownDocumentType_shouldReturnError() throws IOException {
        String xml = readResource("/ubl-unknown-document-type.xml");

        ValidationResponse response = service.validate(xml, "2.1");

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).isNotEmpty();
    }

    // -------------------------------------------------------------------------
    // Version override
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Explicit version hint should override auto-detection")
    void versionHint_shouldOverrideAutoDetect() throws IOException {
        String xml = readResource("/ubl23-invoice-valid.xml");

        ValidationResponse response = service.validate(xml, "2.3");

        assertThat(response.ublVersion()).isEqualTo("2.3");
    }

    @Test
    @DisplayName("Unsupported version should return an error")
    void unsupportedVersion_shouldReturnError() throws IOException {
        String xml = readResource("/ubl23-invoice-valid.xml");

        ValidationResponse response = service.validate(xml, "2.1");

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).isNotEmpty();
        assertThat(response.errors().getFirst().severity()).isEqualTo("ERROR");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String readResource(String path) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Test resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
