package com.quickcart.chat.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.RoundingMode;

/** Small helpers that keep tool output compact and safe to put in a prompt. */
final class ToolFormat {

    static final String RUPEE = "\u20B9";

    private ToolFormat() { }

    /** Money as "₹28.00". Values come from the API unchanged, no arithmetic. */
    static String money(JsonNode n) {
        if (n == null || !n.isNumber()) {
            return "n/a";
        }
        return RUPEE + n.decimalValue().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** One line of plain text: control characters and '|' removed, whitespace collapsed, length capped. */
    static String clean(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\p{Cntrl}", " ").replace('|', '/').replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max - 3) + "..." : t;
    }

    /** Search words: letters, digits, spaces and hyphens only, so nothing can alter the query string. */
    static String searchText(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("[^\\p{L}\\p{N} \\-]", " ").replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max).trim() : t;
    }

    static String text(JsonNode node, String field, int max) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : clean(v.asText(), max);
    }

    static String stock(JsonNode product) {
        return product.path("inStock").asBoolean(false) ? "in stock" : "out of stock";
    }

    static int clamp(Integer value, int defaultValue, int max) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(1, Math.min(max, value));
    }
}