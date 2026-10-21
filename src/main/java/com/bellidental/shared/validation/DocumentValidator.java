package com.bellidental.shared.validation;

import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.exception.InvalidInputException;

public class DocumentValidator {
    
    private DocumentValidator() {
        // Utility class
    }

    public static void validate(DocumentType documentType, String documentNumber) {
        if (documentType == null || documentNumber == null || documentNumber.isBlank()) {
            return; // let standard validation handle @NotNull/@NotBlank
        }
        
        if (DocumentType.CI.equals(documentType)) {
            if (!EcuadorianIdValidator.isValid(documentNumber)) {
                throw new InvalidInputException("El número de cédula ecuatoriana es inválido.");
            }
        }
    }
}
