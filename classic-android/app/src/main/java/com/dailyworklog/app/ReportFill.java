package com.dailyworklog.app;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Filling in a report form: answers worked out from Manage Crew, your profile and earlier reports, the average
 * of the ratings, and details typed into a report saved back to the person. Plain Java (tested on the JVM).
 */
final class ReportFill {
    private ReportFill() {}

    /** "Nov 13, 2026" */
    static String day(long ms) {
        return new SimpleDateFormat("MMM d, yyyy", Locale.US).format(new Date(ms));
    }

    private static Pattern words(String alternatives) {
        return Pattern.compile("\\b(" + alternatives + ")\\b");
    }

    private static final Pattern ME = words("supervisor|foreman|evaluator|evaluated by|reviewed by|completed by|issued by|manager|observer|prepared by");
    private static final Pattern PHONE = words("phone|cell");
    private static final Pattern EMAIL = words("email|e-mail");
    private static final Pattern POSITION = words("position|title");
    private static final List<String> DATES = java.util.Arrays.asList("date", "today's date", "todays date", "date of report",
            "report date", "review date", "evaluation date", "date completed", "date issued");
    static final Pattern EMPLOYEE_NO = Pattern.compile("employee\\s*(#|no\\.?|number|id)|emp\\.?\\s*#|badge|payroll|clock\\s*(#|number)|prnnr");
    static final Pattern BP = Pattern.compile("\\bbp\\b");
    static final Pattern DISN = Pattern.compile("disn");
    private static final List<String> NAME_IS = java.util.Arrays.asList("name", "full name", "employee", "apprentice");
    private static final String[] NAME_HAS = {"employee name", "apprentice name", "apprentice's name", "worker name",
            "worker's name", "name of employee", "name of apprentice", "name of worker"};
    static final Pattern TRADE = words("trade|craft");
    static final Pattern UNION = words("union|local");
    private static final Pattern LEVEL = words("level|classification");
    private static final Pattern PROJECT = words("project|job name|job site");
    private static final Pattern LOCATION = words("location|area");
    private static final Pattern GROUP = words("group|crew");
    private static final Pattern TERM = words("term");
    private static final Pattern CARRY = words("employer|company|department|shift");
    private static final Pattern ANY_PERSON = words("employer|company");

    private static boolean has(Pattern p, String l) {
        return p.matcher(l).find();
    }

    /**
     * The answer worked out for a question, or "" (rules in order; the first that matches decides).
     * earlier = the reports already filled in (any form), used for Employer / Term and the like.
     */
    static String suggest(Store store, Models.Project p, Models.ReportForm form, Models.FilledReport r,
                          Models.FormQuestion q, List<Models.FilledReport> earlier, long now) {
        if (!Models.FormQuestion.TEXT.equals(q.kind) && !Models.FormQuestion.LONG.equals(q.kind)) return "";
        String l = q.label.toLowerCase(Locale.ROOT).trim();
        String person = r.person;
        if (has(ME, l)) {
            if (has(PHONE, l)) return store.contactPhone.isEmpty() ? "" : SmsHelper.formatPhone(store.contactPhone);
            if (has(EMAIL, l)) return store.userEmail;
            if (has(POSITION, l)) return store.workPosition;
            return store.userName;
        }
        if (DATES.contains(l)) return day(now);
        if (EMPLOYEE_NO.matcher(l).find()) return p == null ? "" : p.employeeNumberOf(person);
        if (BP.matcher(l).find()) return p == null ? "" : p.bpOf(person);
        if (DISN.matcher(l).find()) return p == null ? "" : p.disnOf(person);
        if (NAME_IS.contains(l)) return person;
        for (String w : NAME_HAS) if (l.contains(w)) return person;
        if (has(TRADE, l)) return p == null ? "" : p.tradeOf(person);
        if (has(UNION, l)) return p == null ? "" : p.localOf(person);
        if (has(LEVEL, l)) return p == null ? "" : p.levelOf(person);
        if (has(PHONE, l)) return p == null || p.phoneOf(person).isEmpty() ? "" : SmsHelper.formatPhone(p.phoneOf(person));
        if (has(PROJECT, l)) return p == null ? "" : p.name;
        if (has(LOCATION, l)) return p != null && !p.location.isEmpty() ? p.location : store.workLocation;
        if (has(GROUP, l)) return p == null ? "" : p.groupName(p.roleOf(person));
        if (has(TERM, l)) return p == null ? "" : p.termOf(person);  // empty when they have none (no carry-over)
        if (has(CARRY, l)) {
            String same = carried(form, r, q, earlier, true);
            if (!same.isEmpty() || !has(ANY_PERSON, l)) return same;
            return carried(form, r, q, earlier, false);
        }
        return "";
    }

    /** The latest answer to a question with the same label in an earlier report of this form (this person, or anyone). */
    private static String carried(Models.ReportForm form, Models.FilledReport r, Models.FormQuestion q,
                                  List<Models.FilledReport> earlier, boolean samePerson) {
        List<Models.FilledReport> list = new ArrayList<>();
        for (Models.FilledReport x : earlier) {
            if (x == r || !x.formId.equals(form.id)) continue;
            if (samePerson && !x.person.equals(r.person)) continue;
            list.add(x);
        }
        list.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        for (Models.FilledReport x : list) {
            for (Models.FormQuestion fq : form.questions) {
                if (!fq.label.equalsIgnoreCase(q.label)) continue;
                String v = x.answer(fq.id).trim();
                if (!v.isEmpty()) return v;
            }
        }
        return "";
    }

    /** Fills in every empty text answer it can (never overwrites one). */
    static void autoFill(Store store, Models.Project p, Models.ReportForm form, Models.FilledReport r,
                         List<Models.FilledReport> earlier, long now) {
        for (Models.FormQuestion q : form.questions) {
            if (!r.answer(q.id).trim().isEmpty()) continue;
            String v = suggest(store, p, form, r, q, earlier, now);
            if (!v.trim().isEmpty()) r.answers.put(q.id, v.trim());
        }
    }

    /** Sets an answer; an Employee #, trade or union local typed in is saved to the person when they had none. */
    static void setAnswer(Models.Project p, Models.FormQuestion q, Models.FilledReport r, String text, long now) {
        r.answers.put(q.id, text);
        r.updatedAt = now;
        if (p == null || r.person.isEmpty() || text.trim().isEmpty()) return;
        if (!Models.FormQuestion.TEXT.equals(q.kind) && !Models.FormQuestion.LONG.equals(q.kind)) return;
        String l = q.label.toLowerCase(Locale.ROOT);
        if (has(ME, l)) return;  // about you, not the person
        String v = text.trim();
        if (EMPLOYEE_NO.matcher(l).find()) {
            if (p.employeeNumberOf(r.person).isEmpty()) p.employeeNumbers.put(r.person, v);
        } else if (BP.matcher(l).find()) {
            if (p.bpOf(r.person).isEmpty()) p.bpNumbers.put(r.person, v);
        } else if (DISN.matcher(l).find()) {
            if (p.disnOf(r.person).isEmpty()) p.disnNumbers.put(r.person, v);
        } else if (DATES.contains(l.trim()) || NAME_IS.contains(l.trim())) {
            return;
        } else if (has(TRADE, l)) {
            if (p.tradeOf(r.person).isEmpty()) p.trades.put(r.person, v);
        } else if (has(UNION, l)) {
            if (p.localOf(r.person).isEmpty()) p.unionLocals.put(r.person, v);
        }
    }

    /** The value of a rating answer: its choice's number, or the answer itself if it's a number. */
    static Double ratingValue(Models.FormQuestion q, String answer) {
        String a = answer == null ? "" : answer.trim();
        if (a.isEmpty()) return null;
        for (Models.Choice c : q.choices) {
            if (c.label.equalsIgnoreCase(a) || c.display().equalsIgnoreCase(a)) {
                a = c.value;
                break;
            }
        }
        try {
            return a.isEmpty() ? null : Double.parseDouble(a);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The average of the numeric rating answers, to 1 decimal ("2.5"), or "" when there are none. */
    static String average(Models.ReportForm form, Models.FilledReport r) {
        double sum = 0;
        int n = 0;
        for (Models.FormQuestion q : form.questions) {
            if (!Models.FormQuestion.RATING.equals(q.kind)) continue;
            Double v = ratingValue(q, r.answer(q.id));
            if (v == null) continue;
            sum += v;
            n++;
        }
        return n == 0 ? "" : String.format(Locale.US, "%.1f", sum / n);
    }

    /** The questions still to ask: empty, and not worked out (the average). */
    static List<Models.FormQuestion> missing(Models.ReportForm form, Models.FilledReport r) {
        List<Models.FormQuestion> out = new ArrayList<>();
        for (Models.FormQuestion q : form.questions) {
            if (Models.FormQuestion.AVERAGE.equals(q.kind)) continue;
            if (r.answer(q.id).trim().isEmpty()) out.add(q);
        }
        return out;
    }

    /** Who a report can be for: apprentices on the crew (apprentice reports) or everyone (discipline), A-Z. */
    static List<String> people(Models.Project p, String group) {
        List<String> out = new ArrayList<>();
        for (String name : p.crew) {
            if (Models.ReportForm.APPRENTICE.equals(group) && !Models.Project.APPRENTICE.equals(p.levelOf(name))) continue;
            out.add(name);
        }
        Collections.sort(out, String::compareToIgnoreCase);
        return out;
    }

    /** "<form name> - <person> <yyyy-MM-dd>.pdf" */
    static String pdfName(Models.FilledReport r, String form) {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(r.createdAt));
        return Fmt.fileSafe(form + " - " + r.person + " " + date) + ".pdf";
    }
}
