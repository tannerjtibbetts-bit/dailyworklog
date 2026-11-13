package com.dailyworklog.app;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Fitting answers into the form's lines (the PDF and "Place the answers" use the same steps as the new app). */
final class Fit {
    private Fit() {}

    /** Text in lines no wider than width, breaking between words (a very long word is split). */
    static List<String> wrap(String text, double width, ToDoubleFunction<String> measure) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            String line = "";
            for (String word : para.split("\\s+")) {
                if (word.isEmpty()) continue;
                String tryLine = line.isEmpty() ? word : line + " " + word;
                if (measure.applyAsDouble(tryLine) <= width) {
                    line = tryLine;
                    continue;
                }
                if (!line.isEmpty()) out.add(line);
                String rest = word;
                while (measure.applyAsDouble(rest) > width && rest.length() > 1) {
                    int n = rest.length() - 1;
                    while (n > 1 && measure.applyAsDouble(rest.substring(0, n)) > width) n--;
                    out.add(rest.substring(0, n));
                    rest = rest.substring(n);
                }
                line = rest;
            }
            if (!line.isEmpty()) out.add(line);
        }
        return out;
    }

    /** The size to write a one-line answer at so it fits width: size, or smaller (not under half). */
    static double oneLine(String text, double width, double size, ToDoubleFunction<String> measureAt1) {
        double w = measureAt1.applyAsDouble(text) * size;
        if (w <= width || w == 0) return size;
        return Math.max(size * 0.5, size * width / w);
    }

    static final class Rows {
        final double size;
        final List<String> lines;

        Rows(double size, List<String> lines) {
            this.size = size;
            this.lines = lines;
        }
    }

    /** A long answer on rows ruled lines of width: the size (from size down to 55% of it) and the lines. */
    static Rows rows(String text, double width, int rows, double size, ToDoubleFunction<String> measureAt1) {
        double s = size;
        while (true) {
            final double at = s;
            List<String> lines = wrap(text, width, t -> measureAt1.applyAsDouble(t) * at);
            if (lines.size() <= Math.max(1, rows) || s <= size * 0.55) return new Rows(s, lines);
            s *= 0.92;
        }
    }
}
