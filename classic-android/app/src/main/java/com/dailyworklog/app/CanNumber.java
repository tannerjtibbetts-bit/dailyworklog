package com.dailyworklog.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the sea can's six-digit number in the text read from a photo, e.g. "BPPU 410018 5" -> "410018".
 * Only answers when it's clear: if nothing fits, or two different numbers fit, it gives null and the
 * subject is left for the person to type. Plain Java, no Android parts.
 */
final class CanNumber {
    private CanNumber() {}

    /** Owner code (3 letters + U, J or Z), six digits, maybe the check digit. OCR often reads 0 as O and 1 as I or L. */
    private static final Pattern WITH_PREFIX =
            Pattern.compile("(?<![A-Z0-9])([A-Z]{3}[UJZ])\\s*([0-9OIL]{6})(?:\\s*[-\\s]?\\s*([0-9OIL])(?![0-9OIL]))?(?![0-9])");
    /** Six digits standing alone (maybe followed by a separate check digit), not part of 30,480 or a longer number. */
    private static final Pattern BARE =
            Pattern.compile("(?<![0-9.,:/A-Z])([0-9]{6})(?:\\s*[-\\s]\\s*([0-9]))?(?![0-9.,:/A-Z])");

    /** The six digits, or null if there's no clear sea can number. */
    static String find(List<String> lines) {
        if (lines == null || lines.isEmpty()) return null;
        List<String> texts = new ArrayList<>();
        StringBuilder all = new StringBuilder();
        for (String l : lines) {
            if (l == null) continue;
            String u = l.toUpperCase(Locale.ROOT).trim();
            texts.add(u);
            all.append(u).append(' ');
        }
        // The owner code and the number are sometimes read as separate lines, so look at all the text joined too.
        texts.add(all.toString());

        Set<String> checked = new LinkedHashSet<>();
        Set<String> prefixed = new LinkedHashSet<>();
        for (String t : texts) {
            Matcher m = WITH_PREFIX.matcher(t);
            while (m.find()) {
                String digits = digits(m.group(2));
                prefixed.add(digits);
                if (m.group(3) != null && checkDigit(m.group(1) + digits) == Integer.parseInt(digits(m.group(3)))) {
                    checked.add(digits);
                }
            }
        }
        if (checked.size() == 1) return checked.iterator().next();
        if (checked.size() > 1) return null;
        if (prefixed.size() == 1) return prefixed.iterator().next();
        if (prefixed.size() > 1) return null;

        // No owner code read: a lone six-digit number, if there's exactly one.
        Set<String> bare = new LinkedHashSet<>();
        for (int i = 0; i < texts.size() - 1; i++) {
            Matcher m = BARE.matcher(texts.get(i));
            while (m.find()) bare.add(m.group(1));
        }
        return bare.size() == 1 ? bare.iterator().next() : null;
    }

    private static String digits(String s) {
        return s.replace('O', '0').replace('I', '1').replace('L', '1');
    }

    /** ISO 6346 check digit for "BPPU410018" (4 letters + 6 digits), or -1. */
    static int checkDigit(String code) {
        if (code.length() != 10) return -1;
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = code.charAt(i);
            int v;
            if (c >= '0' && c <= '9') v = c - '0';
            else if (c >= 'A' && c <= 'Z') {
                v = 10 + (c - 'A');
                v += (v - 1) / 10;  // values skip 11, 22 and 33
            } else return -1;
            sum += v << i;
        }
        return sum % 11 % 10;
    }
}
