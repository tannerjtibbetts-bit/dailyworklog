package com.dailyworklog.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** "Paste crew numbers": rows copied from a crew list (Excel, a table, text) with names and Employee # / BP # / DISN #. */
final class CrewNumbers {
    private CrewNumbers() {}

    static final class Row {
        final String name, employeeNo, bp, disn;

        Row(String name, String employeeNo, String bp, String disn) {
            this.name = name;
            this.employeeNo = employeeNo;
            this.bp = bp;
            this.disn = disn;
        }

        @Override
        public String toString() {
            return name + "|" + employeeNo + "|" + bp + "|" + disn;
        }
    }

    static final class Applied {
        final List<String> updated = new ArrayList<>();
        /** Rows that match nobody on the crew. */
        final List<Row> missing = new ArrayList<>();
    }

    /** A line's cells: on tabs, else "|", else ",", else runs of 2+ spaces; trimmed. */
    static List<String> cells(String line) {
        String[] parts;
        if (line.contains("\t")) parts = line.split("\t", -1);
        else if (line.contains("|")) parts = line.split("\\|", -1);
        else if (line.contains(",")) parts = line.split(",", -1);
        else parts = line.trim().split("\\s{2,}", -1);
        List<String> out = new ArrayList<>();
        for (String p : parts) out.add(p.trim());
        return out;
    }

    private static int letters(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) n++;
        return n;
    }

    private static boolean hasDigit(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) return true;
        return false;
    }

    private static String get(List<String> cells, int i) {
        return i >= 0 && i < cells.size() ? cells.get(i) : "";
    }

    static List<Row> parse(String text) {
        List<Row> out = new ArrayList<>();
        int nameCol = -1, empCol = -1, bpCol = -1, disnCol = -1;
        boolean headed = false;
        for (String line : text.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            List<String> cells = cells(line);
            if (!headed) {
                int n = -1, e = -1, b = -1, d = -1;
                for (int i = 0; i < cells.size(); i++) {
                    String c = cells.get(i).toLowerCase(Locale.ROOT);
                    if (n < 0 && c.contains("name")) n = i;
                    if (e < 0 && (c.contains("prnnr") || c.contains("employee") || c.startsWith("emp"))) e = i;
                    if (b < 0 && c.startsWith("bp")) b = i;
                    if (d < 0 && c.contains("disn")) d = i;
                }
                boolean numberHeading = false;
                for (String c : cells) {
                    String l = c.toLowerCase(Locale.ROOT);
                    if (l.contains("prnnr") || l.contains("employee") || l.contains("emp") || l.contains("bp") || l.contains("disn")) numberHeading = true;
                }
                if (n >= 0 && numberHeading) {
                    headed = true;
                    nameCol = n;
                    empCol = e;
                    bpCol = b;
                    disnCol = d;
                    continue;
                }
            }
            String name, emp, bp, disn;
            if (headed) {
                name = get(cells, nameCol);
                emp = get(cells, empCol);
                bp = get(cells, bpCol);
                disn = get(cells, disnCol);
            } else {
                name = "";
                for (String c : cells) {
                    if (letters(c) >= 2 && !hasDigit(c) && c.contains(" ")) {
                        name = c;
                        break;
                    }
                }
                if (name.isEmpty()) {
                    for (String c : cells) {
                        if (letters(c) >= 2 && !hasDigit(c)) {
                            name = c;
                            break;
                        }
                    }
                }
                List<String> numbers = new ArrayList<>();
                for (String c : cells) if (c.matches("[0-9-]+") && hasDigit(c)) numbers.add(c);
                emp = numbers.size() > 0 ? numbers.get(0) : "";
                bp = numbers.size() > 1 ? numbers.get(1) : "";
                disn = numbers.size() > 2 ? numbers.get(2) : "";
            }
            name = name.replaceAll("\\s+", " ").trim();
            if (name.isEmpty()) continue;
            out.add(new Row(name, emp.trim(), bp.trim(), disn.trim()));
        }
        return out;
    }

    /** For matching names: lower case, no ".", "'" or "-", single spaces. */
    static String key(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[.'’-]", "").replaceAll("\\s+", " ").trim();
    }

    /** Puts the numbers on the matching crew members (non-empty numbers overwrite). */
    static Applied apply(Models.Project p, List<Row> rows) {
        Applied a = new Applied();
        for (Row r : rows) {
            String who = null;
            for (String c : p.crew) if (key(c).equals(key(r.name))) who = c;
            if (who == null) {
                a.missing.add(r);
                continue;
            }
            put(p, who, r);
            if (!a.updated.contains(who)) a.updated.add(who);
        }
        return a;
    }

    static void put(Models.Project p, String who, Row r) {
        if (!r.employeeNo.isEmpty()) p.employeeNumbers.put(who, r.employeeNo);
        if (!r.bp.isEmpty()) p.bpNumbers.put(who, r.bp);
        if (!r.disn.isEmpty()) p.disnNumbers.put(who, r.disn);
    }

    /** Adds the missing people to the crew (first group, no phone) with their numbers. */
    static void addMissing(Models.Project p, List<Row> missing) {
        for (Row r : missing) {
            if (!p.crew.contains(r.name)) p.crew.add(r.name);
            p.roles.put(r.name, p.groups.get(0).id);
            put(p, r.name, r);
        }
    }

    /** "Numbers added for 3 people." plus who isn't on the crew. */
    static String summary(Applied a) {
        String s = "Numbers added for " + Fmt.plural(a.updated.size(), "person", "people") + ".";
        if (!a.missing.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Row r : a.missing) names.add(r.name);
            s += "\n\nNot on this project's crew: " + String.join(", ", names) + ".";
        }
        return s;
    }
}
