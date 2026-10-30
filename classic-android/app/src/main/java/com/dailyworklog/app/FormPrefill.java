package com.dailyworklog.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opens a Formstack form with your usual answers already filled in. Formstack accepts answers in
 * the link (?field123456=value), but only by each box's hidden number, so we read the form page,
 * find each box by its label, and build the link. You still check the form and tap Submit.
 */
final class FormPrefill {
    private FormPrefill() {}

    static boolean isFormstack(String url) {
        return url != null && url.toLowerCase(Locale.ROOT).contains("formstack.com/forms/");
    }

    /** One question on the form, found by words in its label. */
    static final class Question {
        final String id, label, hint;
        final String[] keys;

        Question(String id, String label, String hint, String... keys) {
            this.id = id;
            this.label = label;
            this.hint = hint;
            this.keys = keys;
        }
    }

    /**
     * The Observation & Coaching form's questions that usually get the same answer, in form order.
     * Name of Observer 1 (your name) and Observation Date (today) are filled in automatically.
     * For pick-one questions, typing part of the answer is enough (e.g. a gang number, or "Single").
     */
    static final Question[] QUESTIONS = {
            new Question("observation", "Single or Group Observation?", "e.g. Single", "single or group observation"),
            new Question("project", "Bruce Power Project", "e.g. FCFR", "bruce power project"),
            new Question("site", "Shoreline FCFR (site)", "e.g. B44 - Warehouse", "shoreline fcfr"),
            new Question("unit", "Unit Number", "e.g. Unit 5", "unit number"),
            new Question("location", "Location / Area of Work", "e.g. the area you work in", "location / area of work", "area of work"),
            new Question("workGroup", "Work Group Observed", "e.g. your trade", "work group observed"),
            new Question("position", "Position of Observer 1", "e.g. FM", "position of observer 1"),
            new Question("gang", "Superintendent and Gang ID", "Part of it is enough, like the gang number", "superintendent and gang id"),
            new Question("email", "Email for a copy", "Your work email", "email address for a copy"),
            new Question("interview", "Interview the worker?", "Yes or No", "interview the worker"),
            new Question("photos", "Upload picture of good compliance items?", "Yes or No", "upload picture of good compliance", "upload picture"),
            new Question("actions", "Actions assigned?", "None, 1, 2 or 3", "any actions assigned"),
            new Question("definitions", "See the definitions?", "Yes or No", "see the definitions"),
            new Question("rating", "Overall rating", "e.g. Positive Feedback", "overall rating"),
            new Question("supervisor", "Send a copy to the supervisor?", "Yes or No", "send a copy of this observation", "copy of this observation"),
    };

    /** Your usual answers, by question id. Blank ones are skipped. */
    static final class Details {
        final java.util.Map<String, String> answers = new java.util.LinkedHashMap<>();
        String name = "";

        String get(String id) {
            String v = answers.get(id);
            return v == null ? "" : v.trim();
        }

        boolean isEmpty() {
            for (String v : answers.values()) if (v != null && !v.trim().isEmpty()) return false;
            return true;
        }
    }

    static final class Result {
        final String url;
        final List<String> filled = new ArrayList<>(), missed = new ArrayList<>();

        Result(String url) {
            this.url = url;
        }
    }

    /** Downloads the form page (run off the main thread). */
    static String fetch(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) DailyWorkLog");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /** Builds the filled-in link from the form page. Anything that can't be matched is listed in missed. */
    static Result build(String url, String html, Details d, Date today) {
        String page = unescape(html);
        String lower = page.toLowerCase(Locale.ROOT);
        List<String> params = new ArrayList<>();
        Result r = new Result(url);

        for (Question q : QUESTIONS) {
            answer(r, params, page, lower, q.label, d.get(q.id), q.keys);
            if (q.id.equals("workGroup")) {  // the form asks for the date and your name next
                answer(r, params, page, lower, "Observation Date",
                        new SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(today), "observation date");
                name(r, params, lower, d.name);
            }
        }

        if (params.isEmpty()) return r;
        String base = url.contains("?") ? url + "&" : url + "?";
        Result out = new Result(base + String.join("&", params));
        out.filled.addAll(r.filled);
        out.missed.addAll(r.missed);
        return out;
    }

    // ----- one kind of box each -----

    /** Pick-one questions must match one of the form's options; anything else is typed in as is. */
    private static void answer(Result r, List<String> params, String page, String lower, String label,
                               String value, String... keys) {
        if (value == null || value.trim().isEmpty()) return;
        String id = fieldId(lower, keys);
        if (id == null) {
            r.missed.add(label);
            return;
        }
        List<String> options = options(page, id);
        String v = options.isEmpty() ? value.trim() : pick(options, value.trim());
        if (v == null) {
            r.missed.add(label);
            return;
        }
        params.add("field" + id + "=" + enc(v));
        r.filled.add(label);
    }

    /** Name boxes are usually split into first and last name. */
    private static void name(Result r, List<String> params, String lower, String name) {
        if (name == null || name.trim().isEmpty() || name.equals("Me")) return;
        String id = fieldId(lower, "name of observer 1");
        if (id == null) {
            r.missed.add("Name of Observer 1");
            return;
        }
        String n = name.trim();
        if (lower.contains("field" + id + "-first")) {
            int sp = n.lastIndexOf(' ');
            params.add("field" + id + "-first=" + enc(sp < 0 ? n : n.substring(0, sp)));
            if (sp > 0) params.add("field" + id + "-last=" + enc(n.substring(sp + 1)));
        } else {
            params.add("field" + id + "=" + enc(n));
        }
        r.filled.add("Name of Observer 1");
    }

    // ----- reading the page -----

    private static final Pattern FIELD = Pattern.compile("field(\\d{5,})");
    /** Markers Formstack puts around each question: fsCell123, label123, for="field123", field123. */
    private static final Pattern NEAR = Pattern.compile("(?:fscell|fslabel|label|for=\"field|field)(\\d{5,})");

    /** The box number nearest after its label (labels come just before their box). */
    static String fieldId(String lower, String... keys) {
        for (String key : keys) {
            int from = 0;
            while (true) {
                int at = lower.indexOf(key, from);
                if (at < 0) break;
                Matcher m = FIELD.matcher(lower);
                // Most pages wrap a question as <div id="fsCell123"><label for="field123">Label...
                // The closest marker before the label belongs to it (the one before that is the
                // previous question's).
                int backStart = Math.max(0, at - 400);
                String id = null;
                Matcher back = NEAR.matcher(lower.substring(backStart, at));
                while (back.find()) id = back.group(1);
                if (id == null && m.find(at) && m.start() - at < 3000) id = m.group(1);
                if (id != null) return id;
                from = at + key.length();
            }
        }
        return null;
    }

    private static final Pattern OPTION = Pattern.compile("<option[^>]*value=\"([^\"]*)\"[^>]*>(.*?)</option>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INPUT = Pattern.compile("<input[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_VALUE = Pattern.compile("value=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    /** Finds the form's own option that matches what you typed (exact first, then "contains"). */
    static String matchOption(String page, String id, String wanted) {
        return pick(options(page, id), wanted);
    }

    /** The choices of a drop-down list or radio buttons; empty for a typed-in box. */
    static List<String> options(String page, String id) {
        List<String> options = new ArrayList<>();
        // Drop-down list
        Matcher sel = Pattern.compile("<select[^>]*name=\"field" + id + "\"[^>]*>(.*?)</select>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(page);
        if (sel.find()) {
            Matcher o = OPTION.matcher(sel.group(1));
            while (o.find()) if (!o.group(1).trim().isEmpty()) options.add(o.group(1).trim());
        }
        // Radio buttons
        Matcher in = INPUT.matcher(page);
        while (in.find()) {
            String tag = in.group();
            if (!tag.contains("name=\"field" + id + "\"")) continue;
            Matcher v = ATTR_VALUE.matcher(tag);
            if (v.find() && !v.group(1).trim().isEmpty()) options.add(v.group(1).trim());
        }
        return options;
    }

    static String pick(List<String> options, String wanted) {
        String w = squash(wanted);
        for (String o : options) if (squash(o).equals(w)) return o;
        for (String o : options) if (squash(o).contains(w)) return o;
        // Every word you typed appears in the option (e.g. "millwright boilermaker").
        String[] parts = w.split("[ /&,-]+");
        for (String o : options) {
            boolean all = true;
            for (String part : parts) if (!part.isEmpty() && !squash(o).contains(part)) all = false;
            if (all) return o;
        }
        // Otherwise the first word that matches an option, e.g. "Millwright" from "millwright / boilermaker".
        for (String part : parts) {
            if (part.length() < 3) continue;
            for (String o : options) if (squash(o).contains(part)) return o;
        }
        return null;
    }

    private static String squash(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String unescape(String s) {
        return s.replace("&amp;", "&").replace("&#39;", "'").replace("&#039;", "'")
                .replace("&nbsp;", " ").replace("&#x2F;", "/").replace("\\/", "/");
    }

    private static String enc(String v) {
        try {
            return URLEncoder.encode(v, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception e) {
            return v;
        }
    }
}
