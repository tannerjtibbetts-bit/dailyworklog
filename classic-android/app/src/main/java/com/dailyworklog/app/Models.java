package com.dailyworklog.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Plain data classes. Everything is saved as one JSON file by {@link Store}. */
public final class Models {
    private Models() {}

    static String newId() {
        return UUID.randomUUID().toString();
    }

    static JSONArray toArray(List<String> list) {
        JSONArray a = new JSONArray();
        for (String s : list) a.put(s);
        return a;
    }

    static List<String> fromArray(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "");
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    public static class Photo {
        public String file = "";
        public String addedBy = "";
        public long addedAt;

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("file", file);
            o.put("addedBy", addedBy);
            o.put("addedAt", addedAt);
            return o;
        }

        static Photo fromJson(JSONObject o) {
            Photo p = new Photo();
            p.file = o.optString("file", "");
            p.addedBy = o.optString("addedBy", "");
            p.addedAt = o.optLong("addedAt", 0L);
            return p;
        }
    }

    public static class Task {
        public String id = newId();
        public String title = "";
        public String notes = "";
        public boolean done;
        public String completedBy = "";
        public long completedAt;
        public String createdBy = "";
        public long createdAt = System.currentTimeMillis();
        public boolean photosRequired;
        /** Load securement confirmed. Only printed on reports when true. */
        public boolean loadSecured;
        public String securedBy = "";
        public long securedAt;
        /** Marked to carry over to the next shift's checklist; shown on the report. */
        public boolean nextShift;
        /** Who gets a text when this field is completed (blank: nobody). */
        public String doneTextName = "", doneTextPhone = "";
        /** SOUTH, NORTH, UNZONED or "" (not set). Reports list South, then North, then Unzoned. */
        public String side = "";

        public static final String SOUTH = "south", NORTH = "north", UNZONED = "unzoned";

        public static String sideLabel(String side) {
            return SOUTH.equals(side) ? "South" : NORTH.equals(side) ? "North" : UNZONED.equals(side) ? "Unzoned" : "";
        }

        /** South first, then North, then Unzoned, then fields with no area (checklist and report order). */
        public static int areaRank(String side) {
            return SOUTH.equals(side) ? 0 : NORTH.equals(side) ? 1 : UNZONED.equals(side) ? 2 : 3;
        }

        /**
         * Fields grouped for the report: South, North, Unzoned, then any without an area.
         * Order within each group is kept. Returns one group with no heading if no areas are set.
         */
        public static List<Map.Entry<String, List<Task>>> reportGroups(List<Task> tasks) {
            List<Task> south = new ArrayList<>(), north = new ArrayList<>(), unzoned = new ArrayList<>(), other = new ArrayList<>();
            for (Task t : tasks) {
                (SOUTH.equals(t.side) ? south : NORTH.equals(t.side) ? north : UNZONED.equals(t.side) ? unzoned : other).add(t);
            }
            List<Map.Entry<String, List<Task>>> out = new ArrayList<>();
            if (south.isEmpty() && north.isEmpty() && unzoned.isEmpty()) {
                out.add(new java.util.AbstractMap.SimpleEntry<>("", other));
                return out;
            }
            if (!south.isEmpty()) out.add(new java.util.AbstractMap.SimpleEntry<>("South", south));
            if (!north.isEmpty()) out.add(new java.util.AbstractMap.SimpleEntry<>("North", north));
            if (!unzoned.isEmpty()) out.add(new java.util.AbstractMap.SimpleEntry<>("Unzoned", unzoned));
            if (!other.isEmpty()) out.add(new java.util.AbstractMap.SimpleEntry<>("Other", other));
            return out;
        }
        public List<String> assignees = new ArrayList<>();
        public List<Photo> photos = new ArrayList<>();

        /** Same field with a clean slate: for templates, copies and duplicates. */
        Task freshCopy() {
            Task t = new Task();
            t.title = title;
            t.photosRequired = photosRequired;
            t.assignees = new ArrayList<>(assignees);
            t.side = side;
            t.doneTextName = doneTextName;
            t.doneTextPhone = doneTextPhone;
            return t;
        }

        /** Unfinished field rolled into the next day: keeps the notes too. */
        Task carryCopy() {
            Task t = freshCopy();
            t.notes = notes;
            return t;
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("title", title);
            o.put("notes", notes);
            o.put("done", done);
            o.put("completedBy", completedBy);
            o.put("completedAt", completedAt);
            o.put("createdBy", createdBy);
            o.put("createdAt", createdAt);
            o.put("photosRequired", photosRequired);
            o.put("loadSecured", loadSecured);
            o.put("securedBy", securedBy);
            o.put("securedAt", securedAt);
            o.put("nextShift", nextShift);
            o.put("side", side);
            o.put("doneTextName", doneTextName);
            o.put("doneTextPhone", doneTextPhone);
            o.put("assignees", toArray(assignees));
            JSONArray ph = new JSONArray();
            for (Photo p : photos) ph.put(p.toJson());
            o.put("photos", ph);
            return o;
        }

        static Task fromJson(JSONObject o) {
            Task t = new Task();
            t.id = o.optString("id", newId());
            t.title = o.optString("title", "");
            t.notes = o.optString("notes", "");
            t.done = o.optBoolean("done", false);
            t.completedBy = o.optString("completedBy", "");
            t.completedAt = o.optLong("completedAt", 0L);
            t.createdBy = o.optString("createdBy", "");
            t.createdAt = o.optLong("createdAt", 0L);
            t.photosRequired = o.optBoolean("photosRequired", false);
            t.loadSecured = o.optBoolean("loadSecured", false);
            t.securedBy = o.optString("securedBy", "");
            t.securedAt = o.optLong("securedAt", 0L);
            t.nextShift = o.optBoolean("nextShift", false);
            t.side = o.optString("side", "");
            t.doneTextName = o.optString("doneTextName", "");
            t.doneTextPhone = o.optString("doneTextPhone", "");
            t.assignees = fromArray(o.optJSONArray("assignees"));
            JSONArray ph = o.optJSONArray("photos");
            if (ph != null) {
                for (int i = 0; i < ph.length(); i++) {
                    JSONObject p = ph.optJSONObject(i);
                    if (p != null) t.photos.add(Photo.fromJson(p));
                }
            }
            return t;
        }
    }

    /** A link used, note sent or template used, that can be included in the day's report. */
    public static class Extra {
        public static final String LINK = "link", NOTIFY = "notify", TEMPLATE = "template", NOTE = "note";
        public String id = newId();
        public String type = LINK;
        /** id of the link, sent note or template it came from */
        public String refId = "";
        public String title = "";
        public String detail = "";
        public String by = "";
        public long at;
        public boolean include;
        public List<Photo> photos = new ArrayList<>();

        public String label() {
            switch (type) {
                case NOTIFY: return "Note sent to " + title;
                case TEMPLATE: return "Template used: " + title;
                case NOTE: return title.isEmpty() || "Me".equals(title) ? "Note" : "Note by " + title;
                default: return "Link used: " + title;
            }
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("type", type);
            o.put("refId", refId);
            o.put("title", title);
            o.put("detail", detail);
            o.put("by", by);
            o.put("at", at);
            o.put("include", include);
            JSONArray ph = new JSONArray();
            for (Photo p : photos) ph.put(p.toJson());
            o.put("photos", ph);
            return o;
        }

        static Extra fromJson(JSONObject o) {
            Extra e = new Extra();
            e.id = o.optString("id", newId());
            e.type = o.optString("type", LINK);
            e.refId = o.optString("refId", "");
            e.title = o.optString("title", "");
            e.detail = o.optString("detail", "");
            e.by = o.optString("by", "");
            e.at = o.optLong("at", 0L);
            e.include = o.optBoolean("include", false);
            JSONArray ph = o.optJSONArray("photos");
            if (ph != null) {
                for (int i = 0; i < ph.length(); i++) {
                    JSONObject p = ph.optJSONObject(i);
                    if (p != null) e.photos.add(Photo.fromJson(p));
                }
            }
            return e;
        }
    }

    public static class DailyLog {
        public String id = newId();
        /** ISO date, yyyy-MM-dd. */
        public String date = "";
        /** Custom name; empty means "Daily work log dd/MM/yyyy". */
        public String name = "";
        public long createdAt = System.currentTimeMillis();
        public List<String> crew = new ArrayList<>();
        public List<Task> tasks = new ArrayList<>();
        public List<Extra> extras = new ArrayList<>();

        public Extra findExtra(String refId) {
            for (Extra e : extras) if (e.refId.equals(refId)) return e;
            return null;
        }

        public List<Extra> includedExtras() {
            List<Extra> out = new ArrayList<>();
            for (Extra e : extras) if (e.include) out.add(e);
            out.sort((a, b) -> Long.compare(a.at, b.at));
            return out;
        }

        public String displayTitle() {
            return name.isEmpty() ? "Daily work log " + Fmt.logDate(date) : name;
        }

        public int doneCount() {
            int n = 0;
            for (Task t : tasks) if (t.done) n++;
            return n;
        }

        public Task findTask(String id) {
            if (id == null) return null;
            for (Task t : tasks) if (t.id.equals(id)) return t;
            return null;
        }

        /** Crew on the checklist, or everyone assigned to a field if no crew is set. */
        public List<String> people() {
            if (!crew.isEmpty()) return crew;
            Set<String> s = new LinkedHashSet<>();
            for (Task t : tasks) s.addAll(t.assignees);
            return new ArrayList<>(s);
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("date", date);
            o.put("name", name);
            o.put("createdAt", createdAt);
            o.put("crew", toArray(crew));
            JSONArray a = new JSONArray();
            for (Task t : tasks) a.put(t.toJson());
            o.put("tasks", a);
            JSONArray x = new JSONArray();
            for (Extra e : extras) x.put(e.toJson());
            o.put("extras", x);
            return o;
        }

        static DailyLog fromJson(JSONObject o) {
            DailyLog l = new DailyLog();
            l.id = o.optString("id", newId());
            l.date = o.optString("date", "");
            l.name = o.optString("name", "");
            l.createdAt = o.optLong("createdAt", 0L);
            l.crew = fromArray(o.optJSONArray("crew"));
            JSONArray a = o.optJSONArray("tasks");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject t = a.optJSONObject(i);
                    if (t != null) l.tasks.add(Task.fromJson(t));
                }
            }
            JSONArray x = o.optJSONArray("extras");
            if (x != null) {
                for (int i = 0; i < x.length(); i++) {
                    JSONObject e = x.optJSONObject(i);
                    if (e != null) l.extras.add(Extra.fromJson(e));
                }
            }
            return l;
        }
    }

    public static class Template {
        public String id = newId();
        public String name = "";
        public List<Task> tasks = new ArrayList<>();

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            JSONArray a = new JSONArray();
            for (Task t : tasks) a.put(t.toJson());
            o.put("tasks", a);
            return o;
        }

        static Template fromJson(JSONObject o) {
            Template t = new Template();
            t.id = o.optString("id", newId());
            t.name = o.optString("name", "");
            JSONArray a = o.optJSONArray("tasks");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject x = a.optJSONObject(i);
                    if (x != null) t.tasks.add(Task.fromJson(x));
                }
            }
            return t;
        }
    }

    /** A saved web link (tool portals, procedures, etc.). Shared across all projects. */
    public static class Link {
        public String id = newId();
        public String title = "";
        public String url = "";

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("title", title);
            o.put("url", url);
            return o;
        }

        static Link fromJson(JSONObject o) {
            Link l = new Link();
            l.id = o.optString("id", newId());
            l.title = o.optString("title", "");
            l.url = o.optString("url", "");
            return l;
        }
    }

    /** A note (and photos) sent to crew from the Notify tab. */
    public static class Notice {
        public String id = newId();
        public List<String> to = new ArrayList<>();
        public String note = "";
        public long sentAt;
        public List<Photo> photos = new ArrayList<>();

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("to", toArray(to));
            o.put("note", note);
            o.put("sentAt", sentAt);
            JSONArray ph = new JSONArray();
            for (Photo p : photos) ph.put(p.toJson());
            o.put("photos", ph);
            return o;
        }

        static Notice fromJson(JSONObject o) {
            Notice n = new Notice();
            n.id = o.optString("id", newId());
            n.to = fromArray(o.optJSONArray("to"));
            n.note = o.optString("note", "");
            n.sentAt = o.optLong("sentAt", 0L);
            JSONArray ph = o.optJSONArray("photos");
            if (ph != null) {
                for (int i = 0; i < ph.length(); i++) {
                    JSONObject p = ph.optJSONObject(i);
                    if (p != null) n.photos.add(Photo.fromJson(p));
                }
            }
            return n;
        }
    }

    /** A note with optional photos from the Notes tab. Can be ticked into a day's report. */
    public static class Note {
        public String id = newId();
        public String text = "";
        public String by = "";
        public long createdAt = System.currentTimeMillis();
        public long updatedAt = createdAt;
        public List<Photo> photos = new ArrayList<>();

        public boolean isEmpty() {
            return text.trim().isEmpty() && photos.isEmpty();
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("text", text);
            o.put("by", by);
            o.put("createdAt", createdAt);
            o.put("updatedAt", updatedAt);
            JSONArray ph = new JSONArray();
            for (Photo p : photos) ph.put(p.toJson());
            o.put("photos", ph);
            return o;
        }

        static Note fromJson(JSONObject o) {
            Note n = new Note();
            n.id = o.optString("id", newId());
            n.text = o.optString("text", "");
            n.by = o.optString("by", "");
            n.createdAt = o.optLong("createdAt", 0L);
            n.updatedAt = o.optLong("updatedAt", n.createdAt);
            JSONArray ph = o.optJSONArray("photos");
            if (ph != null) {
                for (int i = 0; i < ph.length(); i++) {
                    JSONObject p = ph.optJSONObject(i);
                    if (p != null) n.photos.add(Photo.fromJson(p));
                }
            }
            return n;
        }
    }

    /** Weekend overtime sign-up sheet: one row per crew member, Yes/No for Fri, Sat, Sun. */
    public static class Overtime {
        public static final String YES = "Yes", NO = "No";
        /** ISO date of the Friday this sheet covers. */
        public String weekOf = "";
        public boolean include;
        public List<OtRow> rows = new ArrayList<>();
        /** Free-text notes shown under the table and printed with it. */
        public String notes = "";

        public static class OtRow {
            public String name = "", fri = "", sat = "", sun = "";
            /** Hours willing to work, 1 to 10 (blank if not given). */
            public String hours = "";

            JSONObject toJson() throws JSONException {
                JSONObject o = new JSONObject();
                o.put("name", name);
                o.put("fri", fri);
                o.put("sat", sat);
                o.put("sun", sun);
                o.put("hours", hours);
                return o;
            }

            static OtRow fromJson(JSONObject o) {
                OtRow r = new OtRow();
                r.name = o.optString("name", "");
                r.fri = o.optString("fri", "");
                r.sat = o.optString("sat", "");
                r.sun = o.optString("sun", "");
                r.hours = o.optString("hours", "");
                return r;
            }
        }

        /** Rows that have someone picked, in order. */
        public List<OtRow> filled() {
            List<OtRow> out = new ArrayList<>();
            for (OtRow r : rows) if (!r.name.isEmpty()) out.add(r);
            return out;
        }

        public int yesCount(int day) {
            int n = 0;
            for (OtRow r : filled()) {
                String v = day == 0 ? r.fri : day == 1 ? r.sat : r.sun;
                if (YES.equals(v)) n++;
            }
            return n;
        }

        /** Drops rows with no name and no answers. */
        public void removeEmptyRows() {
            rows.removeIf(Overtime::isBlank);
        }

        /** Before showing the tab: set the weekend and, only when there are no rows, one per crew member. */
        public void prepare(int crewSize, String friday) {
            if (weekOf.isEmpty()) weekOf = friday;
            if (rows.isEmpty()) for (int i = 0; i < Math.max(1, crewSize); i++) rows.add(new OtRow());
        }

        /** True when the row has no name, answers or hours. */
        public static boolean isBlank(OtRow r) {
            return r.name.isEmpty() && r.fri.isEmpty() && r.sat.isEmpty() && r.sun.isEmpty() && r.hours.isEmpty();
        }

        /** Fresh weekend: keep the names, clear the Yes/No answers and hours. */
        public void startWeekend(String friday) {
            weekOf = friday;
            for (OtRow r : rows) {
                r.fri = "";
                r.sat = "";
                r.sun = "";
                r.hours = "";
            }
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("weekOf", weekOf);
            o.put("include", include);
            o.put("notes", notes);
            JSONArray a = new JSONArray();
            for (OtRow r : rows) a.put(r.toJson());
            o.put("rows", a);
            return o;
        }

        static Overtime fromJson(JSONObject o) {
            Overtime t = new Overtime();
            if (o == null) return t;
            t.weekOf = o.optString("weekOf", "");
            t.include = o.optBoolean("include", false);
            t.notes = o.optString("notes", "");
            JSONArray a = o.optJSONArray("rows");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject r = a.optJSONObject(i);
                    if (r != null) t.rows.add(OtRow.fromJson(r));
                }
            }
            return t;
        }
    }

    /** Attendance sheet: who is here today (Yes, No, AWOL, Vacation and so on). */
    public static class Attendance {
        public static final String YES = "Yes", NO = "No", AWOL = "AWOL";
        public static final String[] STATUSES = {"Yes", "No", "AWOL", "Vacation", "Off", "Training", "D72", "Step up", "Late", "Left early"};
        /** ISO date this sheet is for ("" until first shown). */
        public String date = "";
        public boolean include;
        public String notes = "";
        public List<AttRow> rows = new ArrayList<>();

        public static class AttRow {
            public String name = "";
            /** One of STATUSES, or "" when not answered. */
            public String status = "";
            /** Short free-text note for this person today. */
            public String comment = "";

            JSONObject toJson() throws JSONException {
                JSONObject o = new JSONObject();
                o.put("name", name);
                o.put("status", status);
                o.put("comment", comment);
                return o;
            }

            static AttRow fromJson(JSONObject o) {
                AttRow r = new AttRow();
                r.name = o.optString("name", "");
                r.status = o.optString("status", "");
                r.comment = o.optString("comment", "");
                return r;
            }

            public boolean isBlank() {
                return name.isEmpty() && status.isEmpty() && comment.isEmpty();
            }
        }

        /** Rows that have someone picked, in order. */
        public List<AttRow> filled() {
            List<AttRow> out = new ArrayList<>();
            for (AttRow r : rows) if (!r.name.isEmpty()) out.add(r);
            return out;
        }

        public int count(String status) {
            int n = 0;
            for (AttRow r : filled()) if (r.status.equals(status)) n++;
            return n;
        }

        /** e.g. "Yes 5 · Vacation 1 · AWOL 1" in STATUSES order; "" when nothing answered. */
        public String summary() {
            List<String> parts = new ArrayList<>();
            for (String s : STATUSES) {
                int n = count(s);
                if (n > 0) parts.add(s + " " + n);
            }
            return String.join(" \u00B7 ", parts);
        }

        /** Drops rows with no name, status or comment. */
        public void removeEmptyRows() {
            rows.removeIf(AttRow::isBlank);
        }

        /** New day: keep the names and notes, clear every status and comment. */
        public void startDay(String iso) {
            date = iso;
            for (AttRow r : rows) {
                r.status = "";
                r.comment = "";
            }
        }

        /** Before showing the tab: set the date and, only when there are no rows, one per crew member with names. */
        public void prepare(List<String> crew, String today) {
            if (date.isEmpty()) date = today;
            if (!rows.isEmpty()) return;
            for (String name : crew) {
                AttRow r = new AttRow();
                r.name = name;
                rows.add(r);
            }
            if (rows.isEmpty()) rows.add(new AttRow());
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("date", date);
            o.put("include", include);
            o.put("notes", notes);
            JSONArray a = new JSONArray();
            for (AttRow r : rows) a.put(r.toJson());
            o.put("rows", a);
            return o;
        }

        static Attendance fromJson(JSONObject o) {
            Attendance t = new Attendance();
            if (o == null) return t;
            t.date = o.optString("date", "");
            t.include = o.optBoolean("include", false);
            t.notes = o.optString("notes", "");
            JSONArray a = o.optJSONArray("rows");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject r = a.optJSONObject(i);
                    if (r != null) t.rows.add(AttRow.fromJson(r));
                }
            }
            return t;
        }
    }

    /** One sea can on the Sea Can Movements tab: where it was (letter, row, level) and where it is now. */
    public static class SeaCanMove {
        public String can = "", fromLetter = "", fromRow = "", fromLevel = "", toLetter = "", toRow = "", toLevel = "";
        /** As typed (e.g. "14" or "4.5"). */
        public String weight = "";

        public boolean isEmpty() {
            return can.trim().isEmpty() && fromLetter.isEmpty() && fromRow.isEmpty() && fromLevel.isEmpty()
                    && toLetter.isEmpty() && toRow.isEmpty() && toLevel.isEmpty() && weight.trim().isEmpty();
        }

        /** "D33 L3": letter and row, then the level. */
        public static String location(String letter, String row, String level) {
            String place = letter + row;
            if (level.isEmpty()) return place;
            return place.isEmpty() ? "L" + level : place + " L" + level;
        }

        public String fromLocation() {
            return location(fromLetter, fromRow, fromLevel);
        }

        public String toLocation() {
            return location(toLetter, toRow, toLevel);
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("can", can);
            o.put("fromLetter", fromLetter);
            o.put("fromRow", fromRow);
            o.put("fromLevel", fromLevel);
            o.put("toLetter", toLetter);
            o.put("toRow", toRow);
            o.put("toLevel", toLevel);
            o.put("weight", weight);
            return o;
        }

        static SeaCanMove fromJson(JSONObject o) {
            SeaCanMove m = new SeaCanMove();
            m.can = o.optString("can", "");
            m.fromLetter = o.optString("fromLetter", "");
            m.fromRow = o.optString("fromRow", "");
            m.fromLevel = o.optString("fromLevel", "");
            m.toLetter = o.optString("toLetter", "");
            m.toRow = o.optString("toRow", "");
            m.toLevel = o.optString("toLevel", "");
            m.weight = o.optString("weight", "");
            return m;
        }
    }

    public static class Project {
        public String id = newId();
        public String name = "";
        public String location = "";
        public long createdAt = System.currentTimeMillis();
        public List<String> crew = new ArrayList<>();
        /** Crew phone numbers by name, used for texting. */
        public Map<String, String> phones = new LinkedHashMap<>();
        public List<DailyLog> logs = new ArrayList<>();
        public List<Template> templates = new ArrayList<>();
        public List<Notice> notices = new ArrayList<>();
        /** Notes tab, newest first. */
        public List<Note> notes = new ArrayList<>();
        /** Who gets the emailed report. */
        public List<String> emails = new ArrayList<>();
        public Overtime overtime = new Overtime();
        public Attendance attendance = new Attendance();
        /** Crew, Techs or Supervision, by name. Anyone not listed is Crew. */
        public Map<String, String> roles = new LinkedHashMap<>();
        /** People who get a text when assigned to a field. Nobody does unless ticked. */
        public Set<String> assignTexts = new LinkedHashSet<>();
        /** Sea Can Movements tab, one line per sea can. */
        public List<SeaCanMove> seaCans = new ArrayList<>();
        /** Print the sea can list on the daily report. */
        public boolean seaCanInclude;

        /** Sea cans that have a number, in order (what exports and reports show). */
        public List<SeaCanMove> filledSeaCans() {
            List<SeaCanMove> out = new ArrayList<>();
            for (SeaCanMove m : seaCans) if (!m.can.trim().isEmpty()) out.add(m);
            return out;
        }

        public static final String CREW = "crew", TECH = "tech", MGMT = "mgmt";
        public static final String[] ROLES = {CREW, TECH, MGMT};

        public static String roleLabel(String role) {
            return TECH.equals(role) ? "Techs" : MGMT.equals(role) ? "Supervision" : "Crew";
        }

        /** Renames someone everywhere they appear: crew, checklists, fields, notes sent, overtime and attendance. */
        public void renamePerson(String from, String to) {
            if (from.equals(to)) return;
            replaceIn(crew, from, to);
            String ph = phones.remove(from);
            if (ph != null) phones.put(to, ph);
            String r = roles.remove(from);
            if (r != null) roles.put(to, r);
            if (assignTexts.remove(from)) assignTexts.add(to);
            for (DailyLog l : logs) {
                replaceIn(l.crew, from, to);
                for (Task t : l.tasks) replaceIn(t.assignees, from, to);
            }
            for (Template x : templates) for (Task t : x.tasks) replaceIn(t.assignees, from, to);
            for (Notice n : notices) replaceIn(n.to, from, to);
            for (Overtime.OtRow row : overtime.rows) if (row.name.equals(from)) row.name = to;
            for (Attendance.AttRow row : attendance.rows) if (row.name.equals(from)) row.name = to;
        }

        private static void replaceIn(List<String> list, String from, String to) {
            int i = list.indexOf(from);
            if (i < 0) return;
            if (list.contains(to)) list.remove(i);
            else list.set(i, to);
        }

        public String roleOf(String name) {
            String r = roles.get(name);
            return r == null ? CREW : r;
        }

        public String phoneOf(String name) {
            String p = phones.get(name);
            return p == null ? "" : p;
        }

        public Note findNote(String id) {
            if (id == null) return null;
            for (Note n : notes) if (n.id.equals(id)) return n;
            return null;
        }

        /** Keeps report entries for this note in step with its latest text and photos. */
        public void syncNote(Note n) {
            for (DailyLog l : logs) {
                Extra e = l.findExtra(n.id);
                if (e == null) continue;
                e.title = n.by;
                e.detail = n.text;
                e.photos = new ArrayList<>(n.photos);
            }
        }

        /** Takes a deleted item off every checklist's report list. */
        public void forgetExtras(String refId) {
            for (DailyLog l : logs) l.extras.removeIf(e -> e.refId.equals(refId));
        }

        public DailyLog findLog(String id) {
            if (id == null) return null;
            for (DailyLog l : logs) if (l.id.equals(id)) return l;
            return null;
        }

        /** The checklist for today's date (newest if there are several), else the most recent one. */
        public DailyLog todayLog(String todayIso) {
            DailyLog best = null;
            for (DailyLog l : logs) {
                if (!l.date.equals(todayIso)) continue;
                if (best == null || l.createdAt > best.createdAt) best = l;
            }
            return best != null ? best : latestLog();
        }

        /**
         * Notes that something was used, on the given log (or today's).
         * Keeps one entry per item per checklist; using it again updates the time.
         */
        public Extra record(DailyLog target, String todayIso, String type, String refId, String title,
                            String detail, List<Photo> photos, String by, long at) {
            DailyLog l = target != null ? target : todayLog(todayIso);
            if (l == null) return null;
            Extra e = l.findExtra(refId);
            if (e == null) {
                e = new Extra();
                e.refId = refId;
                l.extras.add(e);
            }
            e.type = type;
            e.title = title;
            e.detail = detail;
            e.by = by;
            e.at = at;
            e.photos = photos == null ? new ArrayList<>() : new ArrayList<>(photos);
            return e;
        }

        /** Most recent log by date (ties broken by creation time). */
        public DailyLog latestLog() {
            DailyLog best = null;
            for (DailyLog l : logs) {
                if (best == null) { best = l; continue; }
                int c = l.date.compareTo(best.date);
                if (c > 0 || (c == 0 && l.createdAt > best.createdAt)) best = l;
            }
            return best;
        }

        /** Copy a checklist's fields (not its progress or photos) into a new log. */
        public DailyLog duplicate(DailyLog src, String isoDate, String me) {
            DailyLog l = new DailyLog();
            l.date = isoDate;
            l.crew = new ArrayList<>(src.crew);
            for (Task t : src.tasks) {
                Task c = t.freshCopy();
                c.createdBy = me;
                l.tasks.add(c);
            }
            logs.add(l);
            return l;
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("location", location);
            o.put("createdAt", createdAt);
            o.put("crew", toArray(crew));
            JSONArray a = new JSONArray();
            for (DailyLog l : logs) a.put(l.toJson());
            o.put("logs", a);
            JSONArray t = new JSONArray();
            for (Template x : templates) t.put(x.toJson());
            o.put("templates", t);
            JSONObject ph = new JSONObject();
            for (Map.Entry<String, String> e : phones.entrySet()) ph.put(e.getKey(), e.getValue());
            o.put("phones", ph);
            JSONArray n = new JSONArray();
            for (Notice x : notices) n.put(x.toJson());
            o.put("notices", n);
            o.put("emails", toArray(emails));
            o.put("overtime", overtime.toJson());
            o.put("attendance", attendance.toJson());
            JSONObject ro = new JSONObject();
            for (Map.Entry<String, String> e : roles.entrySet()) ro.put(e.getKey(), e.getValue());
            o.put("roles", ro);
            o.put("assignTexts", toArray(new ArrayList<>(assignTexts)));
            JSONArray nt = new JSONArray();
            for (Note x : notes) nt.put(x.toJson());
            o.put("notes", nt);
            JSONArray sc = new JSONArray();
            for (SeaCanMove m : seaCans) sc.put(m.toJson());
            o.put("seaCans", sc);
            o.put("seaCanInclude", seaCanInclude);
            return o;
        }

        static Project fromJson(JSONObject o) {
            Project p = new Project();
            p.id = o.optString("id", newId());
            p.name = o.optString("name", "");
            p.location = o.optString("location", "");
            p.createdAt = o.optLong("createdAt", 0L);
            p.crew = fromArray(o.optJSONArray("crew"));
            JSONArray a = o.optJSONArray("logs");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject l = a.optJSONObject(i);
                    if (l != null) p.logs.add(DailyLog.fromJson(l));
                }
            }
            JSONArray t = o.optJSONArray("templates");
            if (t != null) {
                for (int i = 0; i < t.length(); i++) {
                    JSONObject x = t.optJSONObject(i);
                    if (x != null) p.templates.add(Template.fromJson(x));
                }
            }
            JSONObject ph = o.optJSONObject("phones");
            if (ph != null) {
                Iterator<String> keys = ph.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    String v = ph.optString(k, "");
                    if (!v.isEmpty()) p.phones.put(k, v);
                }
            }
            p.emails = fromArray(o.optJSONArray("emails"));
            p.overtime = Overtime.fromJson(o.optJSONObject("overtime"));
            p.attendance = Attendance.fromJson(o.optJSONObject("attendance"));
            JSONObject ro = o.optJSONObject("roles");
            if (ro != null) {
                Iterator<String> rk = ro.keys();
                while (rk.hasNext()) {
                    String k = rk.next();
                    p.roles.put(k, ro.optString(k, CREW));
                }
            }
            p.assignTexts.addAll(fromArray(o.optJSONArray("assignTexts")));
            JSONArray nt = o.optJSONArray("notes");
            if (nt != null) {
                for (int i = 0; i < nt.length(); i++) {
                    JSONObject x = nt.optJSONObject(i);
                    if (x != null) p.notes.add(Note.fromJson(x));
                }
            }
            p.seaCanInclude = o.optBoolean("seaCanInclude", false);
            JSONArray sc = o.optJSONArray("seaCans");
            if (sc != null) {
                for (int i = 0; i < sc.length(); i++) {
                    JSONObject x = sc.optJSONObject(i);
                    if (x != null) p.seaCans.add(SeaCanMove.fromJson(x));
                }
            }
            JSONArray n = o.optJSONArray("notices");
            if (n != null) {
                for (int i = 0; i < n.length(); i++) {
                    JSONObject x = n.optJSONObject(i);
                    if (x != null) p.notices.add(Notice.fromJson(x));
                }
            }
            return p;
        }
    }
}
