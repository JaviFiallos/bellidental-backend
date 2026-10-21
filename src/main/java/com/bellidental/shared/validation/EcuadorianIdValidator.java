package com.bellidental.shared.validation;

/**
 * Validador para la cédula ecuatoriana.
 */
public class EcuadorianIdValidator {

    public static boolean isValid(String id) {
        if (id == null || id.length() != 10 || !id.matches("\\d{10}")) {
            return false;
        }

        int province = Integer.parseInt(id.substring(0, 2));
        if ((province < 1 || province > 24) && province != 30) {
            return false;
        }

        int thirdDigit = Character.getNumericValue(id.charAt(2));
        if (thirdDigit >= 6) {
            return false;
        }

        int[] coefficients = {2, 1, 2, 1, 2, 1, 2, 1, 2};
        int sum = 0;

        for (int i = 0; i < 9; i++) {
            int digit = Character.getNumericValue(id.charAt(i));
            int product = digit * coefficients[i];
            if (product >= 10) {
                product -= 9;
            }
            sum += product;
        }

        int expectedCheckDigit = (sum % 10 == 0) ? 0 : 10 - (sum % 10);
        int actualCheckDigit = Character.getNumericValue(id.charAt(9));

        return expectedCheckDigit == actualCheckDigit;
    }
}
