package com.devflow.util;

public final class CsvUtils {

    private CsvUtils() {
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replace("\r", " ").replace("\n", " ");
        if (startsAsSpreadsheetFormula(text)) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains(";")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static boolean startsAsSpreadsheetFormula(String text) {
        int index = 0;
        while (index < text.length() && text.charAt(index) <= 0x20) {
            index++;
        }
        if (index == text.length()) {
            return false;
        }
        char first = text.charAt(index);
        return first == '=' || first == '+' || first == '-' || first == '@';
    }
}
