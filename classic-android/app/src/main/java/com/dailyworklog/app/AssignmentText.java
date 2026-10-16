package com.dailyworklog.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds assignment texts ("Assigned to you: …", as this app sends them) in the lines read from a
 * screenshot, so the person who got the text can add it as a field. Tolerant of how text reading
 * splits and misreads lines.
 */
final class AssignmentText {
    final String title, logTitle, project, from, phone;
    final boolean photosRequired;

    AssignmentText(String title, String logTitle, String project, String from, String phone, boolean photosRequired) {
        this.title = title;
        this.logTitle = logTitle;
        this.project = project;
        this.from = from;
        this.phone = phone;
        this.photosRequired = photosRequired;
    }

    private static final Pattern START = Pattern.compile("assign\\w*\\s+to\\s+you\\s*[:;.,]?\\s*(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHOTOS = Pattern.compile("photos?\\s+required", Pattern.CASE_INSENSITIVE);
    private static final Pattern FROM = Pattern.compile("^from\\s*[:;]\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DASHES = Pattern.compile("^[-–—_=~.·•]{3,}");
    private static final Pattern TIME = Pattern.compile("^\\d{1,2}[:.]\\d{2}\\s*(am|pm)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAY = Pattern.compile("^(today|yesterday|mon|tue|wed|thu|fri|sat|sun)\\b.*\\d", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT = Pattern.compile("^(.*?)\\s*\\(([^()]*)\\)\\s*$");

    static List<AssignmentText> parse(List<String> lines) {
        List<AssignmentText> out = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) if (START.matcher(lines.get(i)).find()) starts.add(i);
        for (int s = 0; s < starts.size(); s++) {
            int i = starts.get(s);
            int next = s + 1 < starts.size() ? starts.get(s + 1) : lines.size();
            Matcher m = START.matcher(lines.get(i));
            //noinspection ResultOfMethodCallIgnored
            m.find();
            StringBuilder title = new StringBuilder(m.group(1).trim());
            for (int j = i + 1, added = 0; j < next && added < 4; j++, added++) {
                String l = lines.get(j).trim();
                if (isStop(l)) break;
                if (title.length() > 0) title.append(' ');
                title.append(l);
            }
            String t = title.toString().replaceAll("\\s+", " ").trim().replaceAll("[.,;:]+$", "").trim();
            if (t.isEmpty()) continue;

            boolean photos = false;
            for (int j = i + 1; j < Math.min(next, i + 9); j++) if (PHOTOS.matcher(lines.get(j)).find()) photos = true;

            String from = "", phone = "";
            for (int j = i + 1; j < Math.min(next, i + 11); j++) {
                Matcher f = FROM.matcher(lines.get(j).trim());
                if (!f.find()) continue;
                from = f.group(1).trim().replaceAll("\\s*\\([^()]*\\)\\s*$", "").trim();
                for (int k = j + 1; k < Math.min(lines.size(), j + 4); k++) {
                    String l = lines.get(k);
                    int at = l.toLowerCase(Locale.ROOT).indexOf("call or text");
                    if (at < 0) continue;
                    String rest = l.substring(at);
                    int a = rest.toLowerCase(Locale.ROOT).indexOf(" at ");
                    String num = (a >= 0 ? rest.substring(a + 4) : rest).replaceAll("[^0-9+]", "");
                    if (num.replace("+", "").length() >= 7) phone = num;
                    break;
                }
                break;
            }

            String logTitle = "", project = "";
            int stopBack = s > 0 ? starts.get(s - 1) : -1;
            for (int j = i - 1; j >= Math.max(stopBack + 1, i - 3); j--) {
                String l = lines.get(j).trim();
                if (l.isEmpty()) continue;
                Matcher p = PROJECT.matcher(l);
                if (p.find()) {
                    logTitle = p.group(1).trim();
                    project = p.group(2).trim();
                } else {
                    logTitle = l;
                }
                break;
            }
            out.add(new AssignmentText(t, logTitle, project, from, phone, photos));
        }
        return out;
    }

    private static boolean isStop(String l) {
        String low = l.toLowerCase(Locale.ROOT);
        return l.isEmpty() || PHOTOS.matcher(l).lookingAt() || DASHES.matcher(l).find() || FROM.matcher(l).find()
                || low.contains("call or text") || TIME.matcher(l).find() || START.matcher(l).find()
                || low.startsWith("daily work log") || DAY.matcher(l).find();
    }
}
