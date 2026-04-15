package com.dbna.validator.service;

import com.dbna.validator.dto.ValidationError;
import com.dbna.validator.dto.ValidationResponse;
import com.helger.ubl23.UBL23Marshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core service that orchestrates UBL 2.3 document validation in three stages:
 * <ol>
 *   <li>XML well-formedness (SAX parse)</li>
 *   <li>UBL version / document-type detection</li>
 *   <li>XSD schema validation via ph-ubl bundled schemas (lazily loaded)</li>
 * </ol>
 *
 * <p>Schemas are compiled on first use and cached per document-type name + version.
 * This avoids the large memory footprint of pre-loading all ~240 bundled schemas at startup.
 */
@Service
public class UblValidationService {

    private static final Logger log = LoggerFactory.getLogger(UblValidationService.class);

    private static final String SUPPORTED_VERSION = "2.3";

    /** Per-document-type schema cache for UBL 2.3 (lazily loaded). */
    private final Map<String, Optional<Schema>> ubl23Schemas = new ConcurrentHashMap<>();

    private final DocumentBuilderFactory documentBuilderFactory;

    public UblValidationService(DocumentBuilderFactory documentBuilderFactory) {
        this.documentBuilderFactory = documentBuilderFactory;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Validates a raw XML string as a UBL document.
     *
     * @param xmlContent  The raw XML content to validate
     * @param versionHint Optional caller-supplied version override (e.g. {@code "2.1"}).
     *                    Pass {@code null} to auto-detect from {@code UBLVersionID}.
     * @return A {@link ValidationResponse} describing the outcome
     */
    public ValidationResponse validate(String xmlContent, String versionHint) {
        // Stage 1 – well-formedness
        Document document;
        try {
            document = parseXml(xmlContent);
        } catch (SAXParseException e) {
            return ValidationResponse.invalid(
                    null, versionHint,
                    new ValidationError("FATAL", e.getMessage(), e.getLineNumber(), e.getColumnNumber())
            );
        } catch (Exception e) {
            return ValidationResponse.invalid(
                    null, versionHint,
                    ValidationError.of("FATAL", "XML parsing failed: " + e.getMessage())
            );
        }

        // Stage 2 – document-type and version detection
        String rootLocalName = document.getDocumentElement().getLocalName();
        String ublVersion = resolveVersion(document, versionHint);

        if (!SUPPORTED_VERSION.equals(ublVersion)) {
            return ValidationResponse.invalid(
                    rootLocalName, ublVersion,
                    ValidationError.of("ERROR",
                            "Unsupported UBL version '" + ublVersion + "'. Supported: " + SUPPORTED_VERSION)
            );
        }

        // Stage 3 – XSD schema validation
        Schema schema = resolveSchema(rootLocalName, ublVersion);
        if (schema == null) {
            return ValidationResponse.invalid(
                    rootLocalName, ublVersion,
                    ValidationError.of("ERROR",
                            "No UBL " + ublVersion + " schema found for document type '" + rootLocalName + "'")
            );
        }

        List<ValidationError> errors = validateAgainstSchema(document, schema);

        boolean valid = errors.stream()
                .noneMatch(e -> "ERROR".equals(e.severity()) || "FATAL".equals(e.severity()));

        return new ValidationResponse(valid, rootLocalName, ublVersion, errors);
    }

    // -------------------------------------------------------------------------
    // Stage 1 – XML parsing
    // -------------------------------------------------------------------------

    private Document parseXml(String xmlContent) throws Exception {
        DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
        // Suppress default error printing to stderr
        builder.setErrorHandler(new RethrowingErrorHandler());
        return builder.parse(new InputSource(new StringReader(xmlContent)));
    }

    // -------------------------------------------------------------------------
    // Stage 2 – version detection
    // -------------------------------------------------------------------------

    private String resolveVersion(Document document, String versionHint) {
        if (versionHint != null && !versionHint.isBlank()) {
            return versionHint.trim();
        }

        // Try the standard UBLVersionID element (any namespace)
        NodeList versionNodes = document.getElementsByTagNameNS("*", "UBLVersionID");
        if (versionNodes.getLength() > 0) {
            String version = versionNodes.item(0).getTextContent().trim();
            if (!version.isEmpty()) {
                log.debug("Detected UBL version from UBLVersionID element: {}", version);
                return version;
            }
        }

        log.debug("UBLVersionID absent, defaulting to 2.3");
        return SUPPORTED_VERSION;
    }

    // -------------------------------------------------------------------------
    // Stage 3 – schema lookup (lazy) and validation
    // -------------------------------------------------------------------------

    private Schema resolveSchema(String rootLocalName, String ublVersion) {
        return ubl23Schemas
                .computeIfAbsent(rootLocalName, name -> loadSchema(name, UBL23Marshaller.class, "2.3"))
                .orElse(null);
    }

    /**
     * Loads (compiles) the XSD {@link Schema} for {@code docTypeName} from the ph-ubl
     * marshaller factory method via reflection, e.g. {@code UBL21Marshaller.invoice()}.
     *
     * @return {@code Optional.empty()} when no factory method exists for the given name
     */
    private Optional<Schema> loadSchema(String docTypeName, Class<?> marshallerClass, String version) {
        // "Invoice" → "invoice"
        String methodName = Character.toLowerCase(docTypeName.charAt(0)) + docTypeName.substring(1);
        try {
            Method factory = marshallerClass.getMethod(methodName);
            Object marshaller = factory.invoke(null);
            Schema schema = (Schema) marshaller.getClass().getMethod("getSchema").invoke(marshaller);
            if (schema != null) {
                log.debug("Loaded UBL {} schema for '{}'", version, docTypeName);
            }
            return Optional.ofNullable(schema);
        } catch (NoSuchMethodException e) {
            log.debug("No UBL {} marshaller method for document type '{}'", version, docTypeName);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to load UBL {} schema for '{}': {}", version, docTypeName, e.getMessage());
            return Optional.empty();
        }
    }

    private List<ValidationError> validateAgainstSchema(Document document, Schema schema) {
        List<ValidationError> errors = new ArrayList<>();
        try {
            Validator validator = schema.newValidator();
            validator.setErrorHandler(new CollectingErrorHandler(errors));
            validator.validate(new DOMSource(document));
        } catch (SAXException e) {
            if (errors.isEmpty()) {
                errors.add(ValidationError.of("FATAL", e.getMessage()));
            }
        } catch (IOException e) {
            errors.add(ValidationError.of("ERROR", "Schema validation I/O error: " + e.getMessage()));
        }
        return errors;
    }

    // -------------------------------------------------------------------------
    // Inner helpers
    // -------------------------------------------------------------------------

    /** SAX ErrorHandler that re-throws every exception so callers see the root cause. */
    private static final class RethrowingErrorHandler implements ErrorHandler {
        @Override public void warning(SAXParseException e) { /* ignore warnings during parsing */ }
        @Override public void error(SAXParseException e) throws SAXException { throw e; }
        @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
    }

    /** SAX ErrorHandler that accumulates all issues into a {@link ValidationError} list. */
    private static final class CollectingErrorHandler implements ErrorHandler {
        private final List<ValidationError> errors;

        CollectingErrorHandler(List<ValidationError> errors) {
            this.errors = errors;
        }

        @Override
        public void warning(SAXParseException e) {
            errors.add(new ValidationError("WARNING", e.getMessage(), e.getLineNumber(), e.getColumnNumber()));
        }

        @Override
        public void error(SAXParseException e) {
            errors.add(new ValidationError("ERROR", e.getMessage(), e.getLineNumber(), e.getColumnNumber()));
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            errors.add(new ValidationError("FATAL", e.getMessage(), e.getLineNumber(), e.getColumnNumber()));
            throw e;
        }
    }
}

