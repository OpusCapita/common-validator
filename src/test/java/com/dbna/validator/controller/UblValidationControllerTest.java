package com.dbna.validator.controller;

import com.dbna.validator.dto.ValidationError;
import com.dbna.validator.dto.ValidationResponse;
import com.dbna.validator.service.UblValidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UblValidationController.class)
class UblValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UblValidationService validationService;

    @Test
    @DisplayName("POST /api/v1/ubl/validate with valid document returns valid=true")
    void validate_validDocument_returnsValidTrue() throws Exception {
        ValidationResponse stubResponse =
                new ValidationResponse(true, "Invoice", "2.3", List.of());
        when(validationService.validate(any(), any())).thenReturn(stubResponse);

        mockMvc.perform(post("/api/v1/ubl/validate")
                        .contentType(MediaType.APPLICATION_XML_VALUE)
                        .content(readResource("/ubl-invoice-stub.xml")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.documentType").value("Invoice"))
                .andExpect(jsonPath("$.ublVersion").value("2.3"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/ubl/validate with invalid document returns valid=false and errors")
    void validate_invalidDocument_returnsErrors() throws Exception {
        ValidationError err = new ValidationError("ERROR", "Missing required element 'ID'", 5, 10);
        ValidationResponse stubResponse =
                new ValidationResponse(false, "Invoice", "2.3", List.of(err));
        when(validationService.validate(any(), any())).thenReturn(stubResponse);

        mockMvc.perform(post("/api/v1/ubl/validate")
                        .contentType(MediaType.APPLICATION_XML_VALUE)
                        .content(readResource("/ubl-invoice-stub.xml")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0].severity").value("ERROR"))
                .andExpect(jsonPath("$.errors[0].message").value("Missing required element 'ID'"))
                .andExpect(jsonPath("$.errors[0].lineNumber").value(5))
                .andExpect(jsonPath("$.errors[0].columnNumber").value(10));
    }

    @Test
    @DisplayName("POST /api/v1/ubl/validate with version query param passes it to service")
    void validate_withVersionParam_passedToService() throws Exception {
        ValidationResponse stubResponse =
                new ValidationResponse(true, "Invoice", "2.3", List.of());
        when(validationService.validate(any(), any())).thenReturn(stubResponse);

        mockMvc.perform(post("/api/v1/ubl/validate")
                        .contentType(MediaType.APPLICATION_XML_VALUE)
                        .content(readResource("/ubl-invoice-stub.xml"))
                        .param("version", "2.3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ublVersion").value("2.3"));
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

