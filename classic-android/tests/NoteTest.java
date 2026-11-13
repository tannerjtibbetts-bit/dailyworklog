package com.dailyworklog.app;
public class NoteTest { public static void main(String[] a) throws Exception {
  Models.Project p = new Models.Project(); p.name="X";
  Models.DailyLog l = new Models.DailyLog(); l.date=Fmt.todayIso(); p.logs.add(l);
  Models.Note n = new Models.Note(); n.text="Pump 3 leaking"; n.by="Tanner";
  Models.Photo ph = new Models.Photo(); ph.file="a.jpg"; n.photos.add(ph); p.notes.add(n);
  Models.Extra e = p.record(null, Fmt.todayIso(), Models.Extra.NOTE, n.id, n.by, n.text, n.photos, "Tanner", 1L); e.include=true;
  n.text="Pump 3 leaking, replaced seal"; p.syncNote(n);
  Models.Project q = Models.Project.fromJson(p.toJson());
  Models.Note m = q.findNote(n.id);
  check(m!=null && m.text.equals("Pump 3 leaking, replaced seal") && m.photos.size()==1 && m.by.equals("Tanner"), "note roundtrip");
  Models.Extra x = q.logs.get(0).includedExtras().get(0);
  check(x.label().equals("Note by Tanner") && x.detail.equals(m.text) && x.photos.size()==1, "report entry "+x.label());
  q.forgetExtras(n.id); check(q.logs.get(0).extras.isEmpty(), "forget");
  org.json.JSONObject old = p.toJson(); old.remove("notes"); check(Models.Project.fromJson(old).notes.isEmpty(), "old data loads");
  System.out.println("ALL PASS");
 }
 static void check(boolean b,String s){ if(!b) throw new AssertionError(s); System.out.println("ok "+s); } }
class OtTest { public static void main(String[] a) throws Exception {
  Models.Project p = new Models.Project(); Models.Overtime ot = p.overtime; ot.notes="Need 6 for Saturday\nBring harnesses";
  for (int i=0;i<40;i++) ot.rows.add(new Models.Overtime.OtRow());
  ot.rows.get(0).name="Al"; ot.rows.get(3).sat="Yes"; ot.rows.get(7).hours="8";
  Models.Project q = Models.Project.fromJson(p.toJson());
  NoteTest.check(q.overtime.rows.size()==40 && q.overtime.notes.equals(ot.notes), "40 rows + notes saved");
  q.overtime.removeEmptyRows(); NoteTest.check(q.overtime.rows.size()==3, "remove empty rows keeps 3, got "+q.overtime.rows.size());
  q.overtime.startWeekend("2026-10-09"); NoteTest.check(q.overtime.notes.equals(ot.notes), "clear answers keeps notes");
  // Rows are only made when the list is empty, so a removed row stays gone.
  Models.Overtime o2 = new Models.Overtime(); o2.prepare(3, "2026-10-30");
  NoteTest.check(o2.rows.size()==3 && o2.weekOf.equals("2026-10-30"), "prepare makes one row per crew member");
  o2.rows.remove(1); o2.prepare(3, "2026-11-06"); NoteTest.check(o2.rows.size()==2 && o2.weekOf.equals("2026-10-30"), "prepare does not top up");
  o2.rows.clear(); o2.prepare(0, "x"); NoteTest.check(o2.rows.size()==1, "at least one row");
  // Confirmed column
  Models.Overtime c = new Models.Overtime();
  Models.Overtime.OtRow c1 = new Models.Overtime.OtRow(); c1.name = "Al"; c1.confirmed = "Yes"; c.rows.add(c1);
  Models.Overtime.OtRow c2 = new Models.Overtime.OtRow(); c2.name = "Bo"; c2.confirmed = "No"; c.rows.add(c2);
  Models.Overtime.OtRow c3 = new Models.Overtime.OtRow(); c3.confirmed = "Yes"; c.rows.add(c3);  // no name: not counted
  Models.Overtime.OtRow c4 = new Models.Overtime.OtRow(); c.rows.add(c4);
  Models.Overtime cq = Models.Overtime.fromJson(c.toJson());
  NoteTest.check(cq.rows.get(0).confirmed.equals("Yes") && cq.rows.get(1).confirmed.equals("No") && cq.rows.get(3).confirmed.isEmpty(), "confirmed saved");
  org.json.JSONObject oldRow = c1.toJson(); oldRow.remove("confirmed");
  NoteTest.check(Models.Overtime.OtRow.fromJson(oldRow).confirmed.isEmpty(), "old row: confirmed blank");
  NoteTest.check(c.confirmedCount() == 1, "confirmedCount counts named Yes rows");
  NoteTest.check(!Models.Overtime.isBlank(c3) && Models.Overtime.isBlank(c4), "confirmed makes a row not empty");
  cq.removeEmptyRows(); NoteTest.check(cq.rows.size() == 3, "remove empty rows keeps a confirmed-only row");
  c.startWeekend("2026-11-13");
  NoteTest.check(c1.confirmed.isEmpty() && c2.confirmed.isEmpty() && c.confirmedCount() == 0 && c1.name.equals("Al"), "clear answers clears confirmed");
  System.out.println("OT PASS"); } }
class AttendanceTest { public static void main(String[] a) throws Exception {
  Models.Project p = new Models.Project(); p.name = "B44 Zone 2";
  p.crew.add("Sam Maint"); p.crew.add("Jim McLaren"); p.crew.add("Al");
  Models.Attendance at = p.attendance;
  at.prepare(p.crew, "2026-10-27");
  NoteTest.check(at.rows.size()==3 && at.rows.get(0).name.equals("Sam Maint") && at.rows.get(2).name.equals("Al") && at.date.equals("2026-10-27"), "prepare fills crew names");
  NoteTest.check(at.summary().isEmpty(), "empty summary");
  at.rows.get(0).status = "Yes"; at.rows.get(1).status = "Vacation"; at.rows.get(2).status = "AWOL";
  Models.Attendance.AttRow extra = new Models.Attendance.AttRow(); extra.status = "Yes"; at.rows.add(extra); // no name: not counted
  at.rows.add(new Models.Attendance.AttRow());
  NoteTest.check(at.summary().equals("Yes 1 \u00B7 AWOL 1 \u00B7 Vacation 1"), "summary " + at.summary());
  NoteTest.check(at.count("Yes")==1 && at.filled().size()==3, "count and filled");
  at.include = true; at.notes = "Jim back Monday";
  Models.Project q = Models.Project.fromJson(p.toJson());
  NoteTest.check(p.toJson().has("attendance") && q.attendance.rows.size()==5 && q.attendance.include && q.attendance.notes.equals("Jim back Monday")
      && q.attendance.date.equals("2026-10-27") && q.attendance.rows.get(1).status.equals("Vacation"), "JSON round trip");
  org.json.JSONObject r0 = p.toJson().getJSONObject("attendance").getJSONArray("rows").getJSONObject(0);
  NoteTest.check(r0.getString("name").equals("Sam Maint") && r0.getString("status").equals("Yes"), "JSON keys");
  org.json.JSONObject old = p.toJson(); old.remove("attendance");
  Models.Project o = Models.Project.fromJson(old);
  NoteTest.check(o.attendance.rows.isEmpty() && o.attendance.date.isEmpty() && !o.attendance.include, "old data loads");
  old.put("attendance", "garbage"); NoteTest.check(Models.Project.fromJson(old).attendance.rows.isEmpty(), "garbage loads");
  q.attendance.prepare(q.crew, "2026-11-01"); NoteTest.check(q.attendance.rows.size()==5 && q.attendance.date.equals("2026-10-27"), "prepare leaves rows and date");
  q.attendance.removeEmptyRows(); NoteTest.check(q.attendance.rows.size()==4, "remove empty rows keeps a row with only a status");
  q.renamePerson("Jim McLaren", "Jimmy McLaren"); NoteTest.check(q.attendance.rows.get(1).name.equals("Jimmy McLaren"), "rename reaches attendance");
  q.attendance.startDay("2026-10-28");
  NoteTest.check(q.attendance.date.equals("2026-10-28") && q.attendance.rows.get(0).status.isEmpty() && q.attendance.rows.get(0).name.equals("Sam Maint")
      && q.attendance.notes.equals("Jim back Monday"), "startDay clears statuses only");
  Models.Attendance none = new Models.Attendance(); none.prepare(new java.util.ArrayList<>(), "2026-10-27");
  NoteTest.check(none.rows.size()==1 && none.rows.get(0).name.isEmpty(), "one blank row with no crew");
  NoteTest.check(Fmt.longDay("2026-10-27").equals("Tue Oct 27, 2026"), "long day " + Fmt.longDay("2026-10-27"));
  // Excel
  java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
  AttendanceXlsx.write(p, bo);
  java.util.Map<String,String> parts = new java.util.HashMap<>();
  try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bo.toByteArray()))) {
    for (java.util.zip.ZipEntry e; (e = z.getNextEntry()) != null; ) parts.put(e.getName(), new String(z.readAllBytes(), "UTF-8"));
  }
  NoteTest.check(parts.keySet().containsAll(java.util.Arrays.asList("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml")), "xlsx parts");
  javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
  for (String k : parts.keySet()) f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(parts.get(k).getBytes("UTF-8")));
  String sh = parts.get("xl/worksheets/sheet1.xml");
  NoteTest.check(parts.get("xl/workbook.xml").contains("name=\"Attendance\""), "sheet name");
  NoteTest.check(sh.contains("<c r=\"A1\" t=\"inlineStr\" s=\"5\"><is><t xml:space=\"preserve\">Attendance</t>"), "A1 title");
  NoteTest.check(sh.contains("<c r=\"A2\" t=\"inlineStr\"><is><t xml:space=\"preserve\">Date: Tue Oct 27, 2026</t>"), "A2 date");
  NoteTest.check(sh.contains("<c r=\"A3\" t=\"inlineStr\"><is><t xml:space=\"preserve\">B44 Zone 2</t>"), "A3 project");
  NoteTest.check(sh.contains("<c r=\"A5\" t=\"inlineStr\" s=\"2\"><is><t xml:space=\"preserve\">Name</t>") && sh.contains("<c r=\"B5\" t=\"inlineStr\" s=\"2\"><is><t xml:space=\"preserve\">Here?</t>")
      && sh.contains("<c r=\"C5\" t=\"inlineStr\" s=\"2\"><is><t xml:space=\"preserve\">Comment</t>"), "header row 5");
  NoteTest.check(sh.contains("<c r=\"A6\" t=\"inlineStr\"><is><t xml:space=\"preserve\">Sam Maint</t>") && sh.contains("<c r=\"B6\" t=\"inlineStr\" s=\"3\"><is><t xml:space=\"preserve\">Yes</t>")
      && sh.contains("<c r=\"B8\" t=\"inlineStr\" s=\"4\"><is><t xml:space=\"preserve\">AWOL</t>"), "rows");
  NoteTest.check(sh.contains("<row r=\"10\"><c r=\"A10\" t=\"inlineStr\"><is><t xml:space=\"preserve\">Yes</t></is></c><c r=\"B10\"><v>1</v></c>")
      && sh.contains("<c r=\"A11\" t=\"inlineStr\"><is><t xml:space=\"preserve\">AWOL</t>") && sh.contains("<c r=\"A12\" t=\"inlineStr\"><is><t xml:space=\"preserve\">Vacation</t>"), "counts");
  NoteTest.check(sh.contains("<c r=\"A14\" t=\"inlineStr\" s=\"1\"><is><t xml:space=\"preserve\">Notes</t>") && sh.contains("Jim back Monday"), "notes");
  NoteTest.check(sh.contains("width=\"28\"") && sh.contains("<col min=\"2\" max=\"2\" width=\"14\""), "widths");
  // Comments, Late and Left early
  NoteTest.check(String.join(",", Models.Attendance.STATUSES).equals("Yes,No,Called in,AWOL,Vacation,Off,Training,B72,Step up,Late,Left early"), "statuses");
  NoteTest.check(Models.Attendance.AttRow.fromJson(new org.json.JSONObject().put("status", "D72")).status.equals("B72"), "D72 renamed");
  Models.Project c = new Models.Project();
  Models.Attendance.AttRow c1 = new Models.Attendance.AttRow(); c1.name = "Sam"; c1.status = "Late"; c1.comment = "In at 7:40"; c.attendance.rows.add(c1);
  Models.Attendance.AttRow c2 = new Models.Attendance.AttRow(); c2.comment = "Ask about Friday"; c.attendance.rows.add(c2);
  Models.Attendance.AttRow c3 = new Models.Attendance.AttRow(); c3.name = "Jo"; c3.status = "Left early"; c.attendance.rows.add(c3);
  c.attendance.rows.add(new Models.Attendance.AttRow());
  NoteTest.check(c.attendance.summary().equals("Late 1 \u00B7 Left early 1"), "late in summary " + c.attendance.summary());
  Models.Project cq = Models.Project.fromJson(c.toJson());
  NoteTest.check(cq.attendance.rows.get(0).comment.equals("In at 7:40") && c.toJson().getJSONObject("attendance").getJSONArray("rows").getJSONObject(0).getString("comment").equals("In at 7:40"), "comment round trip");
  org.json.JSONObject nc = c.toJson(); nc.getJSONObject("attendance").getJSONArray("rows").getJSONObject(0).remove("comment");
  NoteTest.check(Models.Project.fromJson(nc).attendance.rows.get(0).comment.isEmpty(), "old row without comment");
  NoteTest.check(!c2.isBlank(), "comment-only row is not blank");
  cq.attendance.removeEmptyRows(); NoteTest.check(cq.attendance.rows.size()==3 && cq.attendance.rows.get(1).comment.equals("Ask about Friday"), "remove empty rows keeps a comment-only row");
  cq.attendance.startDay("2026-10-29"); NoteTest.check(cq.attendance.rows.get(0).comment.isEmpty() && cq.attendance.rows.get(0).status.isEmpty() && cq.attendance.rows.get(0).name.equals("Sam"), "startDay clears comment");
  c.attendance.date = "2026-10-27";
  java.io.ByteArrayOutputStream cb = new java.io.ByteArrayOutputStream(); AttendanceXlsx.write(c, cb);
  String cs = null;
  try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(cb.toByteArray()))) {
    for (java.util.zip.ZipEntry e; (e = z.getNextEntry()) != null; ) if (e.getName().equals("xl/worksheets/sheet1.xml")) cs = new String(z.readAllBytes(), "UTF-8");
  }
  NoteTest.check(cs.contains("<c r=\"C4\" t=\"inlineStr\" s=\"2\"><is><t xml:space=\"preserve\">Comment</t>")
      && cs.contains("<c r=\"C5\" t=\"inlineStr\"><is><t xml:space=\"preserve\">In at 7:40</t>") && cs.contains("<col min=\"3\" max=\"3\" width=\"40\""), "xlsx comment column");
  System.out.println("ATTENDANCE PASS"); } }
class ShiftTest { public static void main(String[] a) throws Exception {
  Models.Project p = new Models.Project(); Models.DailyLog l = new Models.DailyLog(); p.logs.add(l);
  Models.Task t = new Models.Task(); t.title="Finish torque checks"; t.nextShift=true; t.notes="bay 3"; l.tasks.add(t);
  Models.Project q = Models.Project.fromJson(p.toJson());
  Models.Task r = q.logs.get(0).tasks.get(0);
  NoteTest.check(r.nextShift, "next shift saved");
  Models.Task c = r.carryCopy(); NoteTest.check(!c.nextShift && c.notes.equals("bay 3") && !c.done, "carried copy starts fresh, keeps notes");
  System.out.println("SHIFT PASS"); } }
class CrewTest { public static void main(String[] a) throws Exception {
  Models.Project p = new Models.Project(); p.crew.add("Al"); p.crew.add("Bo"); p.crew.add("Cy");
  p.roles.put("Bo", Models.Project.TECH); p.roles.put("Cy", Models.Project.MGMT); p.assignTexts.add("Al");
  Models.Project q = Models.Project.fromJson(p.toJson());
  NoteTest.check(q.roleOf("Al").equals("crew") && q.roleOf("Bo").equals("tech") && q.roleOf("Cy").equals("mgmt"), "groups saved");
  NoteTest.check(q.assignTexts.contains("Al") && !q.assignTexts.contains("Bo"), "texting choice saved");
  org.json.JSONObject old = p.toJson(); old.remove("roles"); old.remove("assignTexts");
  Models.Project o = Models.Project.fromJson(old);
  NoteTest.check(o.roleOf("Bo").equals("crew") && o.assignTexts.isEmpty(), "old data: everyone Crew, nobody auto-texted");
  System.out.println("CREW PASS"); } }
class RenameTest { public static void main(String[] a) throws Exception {
  Models.Project p = new Models.Project(); p.crew.add("Jon"); p.phones.put("Jon","555"); p.roles.put("Jon", Models.Project.MGMT); p.assignTexts.add("Jon");
  Models.DailyLog l = new Models.DailyLog(); l.crew.add("Jon"); Models.Task t = new Models.Task(); t.assignees.add("Jon"); l.tasks.add(t); p.logs.add(l);
  Models.Overtime.OtRow r = new Models.Overtime.OtRow(); r.name="Jon"; p.overtime.rows.add(r);
  p.renamePerson("Jon","Jon Smith");
  NoteTest.check(p.crew.contains("Jon Smith") && !p.crew.contains("Jon") && "555".equals(p.phones.get("Jon Smith"))
    && p.roleOf("Jon Smith").equals("mgmt") && p.assignTexts.contains("Jon Smith") && l.crew.contains("Jon Smith")
    && t.assignees.contains("Jon Smith") && r.name.equals("Jon Smith"), "rename everywhere");
  NoteTest.check(Models.Project.roleLabel(Models.Project.MGMT).equals("Supervision"), "label Supervision");
  System.out.println("RENAME PASS"); } }
class SideTest { public static void main(String[] a) throws Exception {
  Models.Project p = new Models.Project(); Models.DailyLog l = new Models.DailyLog(); p.logs.add(l);
  String[][] f = {{"N1","north"},{"U1","unzoned"},{"S1","south"},{"X1",""},{"S2","south"},{"N2","north"}};
  for (String[] x : f) { Models.Task t = new Models.Task(); t.title=x[0]; t.side=x[1]; l.tasks.add(t); }
  Models.Project q = Models.Project.fromJson(p.toJson());
  StringBuilder sb = new StringBuilder();
  for (java.util.Map.Entry<String, java.util.List<Models.Task>> g : Models.Task.reportGroups(q.logs.get(0).tasks)) {
    sb.append(g.getKey()).append(":"); for (Models.Task t : g.getValue()) sb.append(t.title).append(","); sb.append(" "); }
  NoteTest.check(sb.toString().equals("South:S1,S2, North:N1,N2, Unzoned:U1, Other:X1, "), "report order " + sb);
  Models.DailyLog plain = new Models.DailyLog(); Models.Task t = new Models.Task(); plain.tasks.add(t);
  NoteTest.check(Models.Task.reportGroups(plain.tasks).size()==1 && Models.Task.reportGroups(plain.tasks).get(0).getKey().isEmpty(), "no areas set = report unchanged");
  NoteTest.check(q.logs.get(0).tasks.get(0).freshCopy().side.equals("north"), "area kept on copies/templates");
  NoteTest.check(Models.Task.sideLabel("unzoned").equals("Unzoned") && Models.Task.areaRank("unzoned") == 2 && Models.Task.areaRank("") == 3, "unzoned label and order");
  Models.DailyLog uz = new Models.DailyLog(); Models.Task u = new Models.Task(); u.side = "unzoned"; uz.tasks.add(u);
  NoteTest.check(Models.Task.reportGroups(uz.tasks).get(0).getKey().equals("Unzoned"), "unzoned only = gets its heading");
  System.out.println("SIDE PASS"); } }
class TextTest { public static void main(String[] a) throws Exception {
  Models.Project p = new Models.Project(); p.name="B44 Zone 2"; Models.DailyLog l = new Models.DailyLog(); l.date="2026-10-05";
  Models.Task t = new Models.Task(); t.title="Pack up sea can";
  String s = SmsHelper.taskText(p, l, t, "Tanner", "12266688639");
  System.out.println("---\n" + s + "\n---");
  NoteTest.check(s.endsWith("\n\n--------------------\nFrom: Tanner\nCall or text me at 1-226-668-8639"), "assignment text has number");
  NoteTest.check(SmsHelper.taskText(p, l, t, "Tanner", "").endsWith("--------------------\nFrom: Tanner"), "no number set = unchanged");
  NoteTest.check(SmsHelper.noticeText(p, "Meet at gate 3", "Tanner", "2266688639").endsWith("From: Tanner (B44 Zone 2)\nCall or text me at 226-668-8639"), "notify text has number");
  System.out.println("TEXT PASS"); } }
class TextTest2 { public static void main(String[] a) throws Exception {
  Models.Project p = new Models.Project(); p.name="B44 Zone 2"; Models.DailyLog l = new Models.DailyLog(); l.date="2026-10-05";
  Models.Task t = new Models.Task(); t.title="Pack up sea can"; t.notes="Use the north gate"; t.photosRequired=true;
  String s = SmsHelper.taskText(p, l, t, "Tanner", "12266688639");
  System.out.println("---\n" + s + "\n---");
  NoteTest.check(s.contains("Assigned to you: Pack up sea can\n\nPhotos required.\n\n----") && !s.contains("north gate"), "no notes in text");
  System.out.println("TEXT2 PASS"); } }
class LayoutTest { public static void main(String[] a) throws Exception {
  java.util.List<float[]> s = new java.util.ArrayList<>();
  for (int i=0;i<7;i++) s.add(new float[]{612,1320});   // tall phone photos
  s.add(new float[]{4000,3000});                         // one wide photo
  s.add(null);                                           // missing file
  java.util.List<java.util.List<PhotoLayout.Cell>> rows = PhotoLayout.rows(s, 500, 130, 8, 90);
  for (java.util.List<PhotoLayout.Cell> r : rows) { StringBuilder b = new StringBuilder(); for (PhotoLayout.Cell c : r) b.append(String.format("#%d@%.0f(%.0fx%.0f) ", c.index, c.x, c.w, c.h)); System.out.println(b); }
  for (java.util.List<PhotoLayout.Cell> r : rows) { PhotoLayout.Cell last = r.get(r.size()-1); NoteTest.check(last.x + last.w <= 500.01, "row fits width"); }
  NoteTest.check(rows.get(0).size() >= 6, "tall photos pack 6+ per row");
  NoteTest.check(PhotoLayout.REPORT_H == 80f, "report photos 80 pt");
  java.util.List<PhotoLayout.Cell> small = PhotoLayout.rows(s.subList(0,3), 500, PhotoLayout.REPORT_H, 8, 90, PhotoLayout.REPORT_SLOT).get(0);
  NoteTest.check(small.get(1).x - small.get(0).x == 48f, "narrow photo keeps caption room: " + (small.get(1).x - small.get(0).x));
  Models.Photo p = new Models.Photo(); p.addedBy="Jim McLaren"; java.util.Calendar c = java.util.Calendar.getInstance(); c.set(2026,9,5,7,32); p.addedAt=c.getTimeInMillis();
  NoteTest.check(PhotoLayout.caption(p, "2026-10-05").equals("JM 7:32 AM"), "caption same day: " + PhotoLayout.caption(p, "2026-10-05"));
  NoteTest.check(PhotoLayout.caption(p, "2026-10-06").equals("JM 10/05 7:32 AM"), "caption other day");
  System.out.println("LAYOUT PASS"); } }
class XlsxTest { public static void main(String[] a) throws Exception {
  Models.Project p = new Models.Project(); p.name="B44 Zone 2"; Models.Overtime ot = p.overtime; ot.weekOf="2026-10-09";
  String[][] rows = {{"Jim McLaren","Yes","No","Yes","8"},{"Alex Symonds","Yes","Yes","","10"},{"","","","",""},{"Beau & Co <test>","No","","Yes",""}};
  for (String[] r : rows) { Models.Overtime.OtRow o = new Models.Overtime.OtRow(); o.name=r[0]; o.fri=r[1]; o.sat=r[2]; o.sun=r[3]; o.hours=r[4]; ot.rows.add(o); }
  ot.notes = "Bring harnesses\nGate 3 opens at 6";
  try (java.io.FileOutputStream f = new java.io.FileOutputStream(a[0])) { OvertimeXlsx.write(p, "Tester", 0L, f); }
  System.out.println("XLSX WRITTEN"); } }
class PrefillTest { public static void main(String[] a) throws Exception {
  String html = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(a[0])), "UTF-8");
  FormPrefill.Details d = new FormPrefill.Details();
  String[][] v = {{"observation","Single"},{"project","FCFR"},{"site","B44 - Warehouse"},{"unit","Unit 5"},{"location","B44 zone 2"},
    {"workGroup","Millwright / Boilermaker Mixed"},{"position","FM"},{"gang","4888"},{"email","pat@example.com"},{"interview","No"},
    {"photos","No"},{"actions","None"},{"definitions","No"},{"rating","Positive Feedback"},{"supervisor","No"}};
  for (String[] x : v) d.answers.put(x[0], x[1]);
  d.name = "Pat Example";
  java.util.Calendar c = java.util.Calendar.getInstance(); c.set(2026,9,9);
  FormPrefill.Result r = FormPrefill.build("https://x.formstack.com/forms/obs", html, d, c.getTime());
  System.out.println(r.url.replace("&", "\n  &"));
  System.out.println("filled=" + r.filled.size() + " missed=" + r.missed);
  NoteTest.check(r.missed.isEmpty() && r.filled.size()==17, "all 17 filled");
  String[] want = {"field111001=Single%20Observation","field111002=FCFR","field111020=B44%20-%20Warehouse","field111021=Unit%205",
    "field111003=B44%20zone%202","field111004=Millwright%20%2F%20Boilermaker%20Mixed","field111007=FM","field111008=FCFR%20-%20Justin",
    "field111030=No","field111031=No","field111032=None","field111033=No","field111034=Positive%20Feedback","field111035=No"};
  for (String w : want) NoteTest.check(r.url.contains(w), w);
  FormPrefill.Details old = new FormPrefill.Details(); old.answers.put("project","FCFR");
  NoteTest.check(!old.isEmpty() && new FormPrefill.Details().isEmpty(), "empty check");
  System.out.println("PREFILL PASS"); } }
class AccessTest { public static void main(String[] a) throws Exception {
  java.security.KeyPairGenerator g = java.security.KeyPairGenerator.getInstance("EC");
  g.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
  java.security.KeyPair owner = g.generateKeyPair(), stranger = g.generateKeyPair();
  java.security.PublicKey built = AccessCodes.publicKey(AccessCodes.encodeKey(owner.getPublic()));
  String req = AccessCodes.requestCode("install-123", "Sam Maint");
  String sms = "Daily Work Log access request from Sam Maint:\n" + req;
  AccessCodes.Request r = AccessCodes.parseRequest(sms);
  NoteTest.check(r != null && r.installId.equals("install-123") && r.name.equals("Sam Maint"), "request round-trip");
  java.security.Signature s = java.security.Signature.getInstance("SHA256withECDSA");
  s.initSign(owner.getPrivate()); s.update(AccessCodes.payload(r.installId, r.name, "2027-10-16"));
  String ok = AccessCodes.approvalCode("2027-10-16", s.sign(), "");
  String reply = "Your Daily Work Log approval. Copy this whole text, then paste it on the sign-in screen:\n" + ok;
  System.out.println("request length " + req.length() + ", approval length " + ok.length());
  AccessCodes.Approval ap = AccessCodes.parseApproval(reply);
  NoteTest.check(AccessCodes.verify(built, "install-123", "Sam Maint", ap), "owner's approval accepted");
  NoteTest.check(!AccessCodes.verify(built, "install-999", "Sam Maint", ap), "same code on another phone rejected");
  NoteTest.check(!AccessCodes.verify(built, "install-123", "Someone Else", ap), "different name rejected");
  java.security.Signature f = java.security.Signature.getInstance("SHA256withECDSA");
  f.initSign(stranger.getPrivate()); f.update(AccessCodes.payload("install-123", "Sam Maint", "2027-10-16"));
  NoteTest.check(!AccessCodes.verify(built, "install-123", "Sam Maint", AccessCodes.parseApproval(AccessCodes.approvalCode("2027-10-16", f.sign(), null))), "forged approval rejected");
  NoteTest.check(!AccessCodes.verify(built, "install-123", "Sam Maint", AccessCodes.parseApproval(ok.replace("MjAyNy0xMC0xNnw", "MjA5OS0xMC0xNnw"))), "edited expiry rejected");
  NoteTest.check(AccessCodes.parseApproval("hello") == null && AccessCodes.parseRequest("DWL-REQ-!!") == null, "junk rejected");
  // Hand over: owner -> work phone -> another phone. Old approvals keep working.
  java.security.KeyPair work = g.generateKeyPair();
  String wurl = AccessCodes.keyUrl(work.getPublic());
  java.security.Signature h = java.security.Signature.getInstance("SHA256withECDSA");
  h.initSign(owner.getPrivate()); h.update(AccessCodes.handoverPayload(wurl));
  String chain = AccessCodes.extendChain("", wurl, h.sign());
  String text = "Daily Work Log owner handover:\n" + AccessCodes.handoverCode(work.getPrivate().getEncoded(), chain, "482913");
  System.out.println("handover length " + text.length());
  NoteTest.check(AccessCodes.looksLikeHandover(text), "handover recognised");
  NoteTest.check(AccessCodes.openHandover(text, "482914") == null, "wrong code rejected");
  AccessCodes.Handover got = AccessCodes.openHandover(text, "482913");
  NoteTest.check(got != null && got.chain.equals(chain), "right code opens it");
  java.security.PrivateKey wk = java.security.KeyFactory.getInstance("EC").generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(got.privateKeyPkcs8));
  NoteTest.check(java.util.Arrays.equals(AccessCodes.chainEnd(built, got.chain).getEncoded(), work.getPublic().getEncoded()), "chain leads to work phone");
  java.security.Signature w = java.security.Signature.getInstance("SHA256withECDSA");
  w.initSign(wk); w.update(AccessCodes.payload("install-777", "New Guy", "2027-11-01"));
  String ok2 = AccessCodes.approvalCode("2027-11-01", w.sign(), got.chain);
  NoteTest.check(AccessCodes.verify(built, "install-777", "New Guy", AccessCodes.parseApproval("Approval:\n" + ok2 + "\n")), "work phone's approval accepted");
  NoteTest.check(AccessCodes.verify(built, "install-123", "Sam Maint", AccessCodes.parseApproval(reply)), "old approval still valid");
  NoteTest.check(!AccessCodes.verify(built, "install-777", "New Guy", AccessCodes.parseApproval(AccessCodes.approvalCode("2027-11-01", AccessCodes.parseApproval(ok2).signature, ""))), "work signature without chain rejected");
  java.security.Signature x = java.security.Signature.getInstance("SHA256withECDSA");
  x.initSign(stranger.getPrivate()); x.update(AccessCodes.handoverPayload(wurl));
  NoteTest.check(AccessCodes.chainEnd(built, AccessCodes.extendChain("", wurl, x.sign())) == null, "fake handover rejected");
  String bad = text.substring(0, text.length() - 3) + (text.endsWith("A") ? "BBB" : "AAA");
  NoteTest.check(AccessCodes.openHandover(bad, "482913") == null, "edited handover rejected");
  java.security.KeyPair back = g.generateKeyPair();
  String burl = AccessCodes.keyUrl(back.getPublic());
  java.security.Signature b2 = java.security.Signature.getInstance("SHA256withECDSA");
  b2.initSign(wk); b2.update(AccessCodes.handoverPayload(burl));
  String chain2 = AccessCodes.extendChain(got.chain, burl, b2.sign());
  NoteTest.check(java.util.Arrays.equals(AccessCodes.chainEnd(built, chain2).getEncoded(), back.getPublic().getEncoded()), "second handover");
  NoteTest.check(AccessCodes.verify(built, "install-777", "New Guy", AccessCodes.parseApproval(ok2)), "work approval valid after second handover");
  System.out.println("ACCESS PASS"); } }
class SeaCanTest { public static void main(String[] a) throws Exception {
  Models.Project p = new Models.Project();
  Models.SeaCanMove m = new Models.SeaCanMove(); m.can = "TCLU 317503"; m.fromRow = "12"; m.fromLevel = "2"; m.toRow = "40"; m.toLevel = "1";
  p.seaCans.add(m); p.seaCans.add(new Models.SeaCanMove());
  Models.Project q = Models.Project.fromJson(p.toJson());
  NoteTest.check(q.seaCans.size() == 2 && q.seaCans.get(0).can.equals("TCLU 317503") && q.seaCans.get(0).toRow.equals("40") && q.seaCans.get(0).fromLevel.equals("2"), "sea cans saved and loaded");
  NoteTest.check(q.seaCans.get(1).isEmpty() && !q.seaCans.get(0).isEmpty(), "empty rows spotted");
  Models.Project old = Models.Project.fromJson(new org.json.JSONObject("{\"name\":\"x\"}"));
  NoteTest.check(old.seaCans.isEmpty(), "older data has no sea cans");
  System.out.println("SEACAN PASS"); } }
class SeaCanExportTest { public static void main(String[] a) throws Exception {
  Models.Project p = new Models.Project(); p.name = "B44";
  Models.SeaCanMove m = new Models.SeaCanMove(); m.can = "BPPU 201006-1"; m.fromLetter = "D"; m.fromRow = "33"; m.fromLevel = "3"; m.toLetter = "E"; m.toRow = "34"; m.toLevel = "2"; m.weight = "5";
  Models.SeaCanMove q = new Models.SeaCanMove(); q.can = "Odd, \"one\""; q.fromRow = "3"; q.weight = "4.5";
  p.seaCans.add(m); p.seaCans.add(new Models.SeaCanMove()); p.seaCans.add(q);
  p.seaCanInclude = true;
  NoteTest.check(Models.Project.fromJson(p.toJson()).seaCanInclude, "include tick saved");
  NoteTest.check(Models.Project.fromJson(p.toJson()).seaCans.get(0).fromLetter.equals("D") && Models.Project.fromJson(p.toJson()).seaCans.get(2).weight.equals("4.5"), "letters and weight saved");
  NoteTest.check(m.fromLocation().equals("D33 L3") && m.toLocation().equals("E34 L2") && q.fromLocation().equals("3") && q.toLocation().isEmpty(), "locations");
  NoteTest.check(Models.SeaCanMove.location("", "12", "2").equals("12 L2") && Models.SeaCanMove.location("", "", "3").equals("L3") && Models.SeaCanMove.location("E", "", "").equals("E"), "location text");
  NoteTest.check(p.filledSeaCans().size() == 2, "only numbered sea cans exported");
  java.io.ByteArrayOutputStream csv = new java.io.ByteArrayOutputStream();
  SeaCanExport.writeCsv(p, csv);
  String c = csv.toString("UTF-8");
  NoteTest.check(c.equals("\uFEFFMoves,Can #,From Location,To Location,Weight,Bin #,From Location,To Location\r\n1,BPPU 201006-1,D33 L3,E34 L2,5,,,\r\n2,\"Odd, \"\"one\"\"\",3,,4.5,,,\r\n"), "csv " + c);
  try (java.io.FileOutputStream f = new java.io.FileOutputStream(a[0] + "/seacans.xlsx")) { SeaCanExport.writeXlsx(p, "Tanner", 0L, f); }
  try (java.io.FileOutputStream f = new java.io.FileOutputStream(a[0] + "/seacans.docx")) { ReportDocx.writeSeaCans(p, "Tanner", 0L, f); }
  System.out.println("SEACANEXPORT PASS"); } }
class ManualTest { public static void main(String[] a) throws Exception {
  String json = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(a[0])), "UTF-8");
  org.json.JSONArray arr = new org.json.JSONObject(json).getJSONArray("sections");
  java.util.List<org.json.JSONObject> list = new java.util.ArrayList<>();
  int newOnly = 0;
  for (int i = 0; i < arr.length(); i++) { org.json.JSONObject s = arr.getJSONObject(i); if (s.optBoolean("newAppOnly")) newOnly++; else list.add(s); }
  NoteTest.check(list.size() > 30 && list.size() + newOnly == arr.length(), "new-app-only topics hidden in the classic app");
  boolean backup = false;
  for (org.json.JSONObject s : list) if (s.getString("id").equals("backup")) backup = true;
  NoteTest.check(backup, "backup topic shown in the classic app (it has Back up now)");
  java.util.List<Object[]> hits = ManualActivity.search(list, "sea can");
  NoteTest.check(((org.json.JSONObject) hits.get(0)[0]).getString("id").equals("sea-can-movements"), "search sea can");
  NoteTest.check(ManualActivity.search(list, "zzzz").isEmpty(), "no match");
  NoteTest.check(ManualActivity.search(list, "backup restore").isEmpty() || !((org.json.JSONObject) ManualActivity.search(list, "backup restore").get(0)[0]).optBoolean("newAppOnly"), "no new-app topics");
  System.out.println("MANUAL PASS"); } }
class HiddenTabsTest { public static void main(String[] a) throws Exception {
  NoteTest.check(LogsActivity.TAB_IDS.length == LogsActivity.TAB_NAMES.length && java.util.Arrays.asList(LogsActivity.TAB_IDS).contains("seacans"), "tab ids");
  NoteTest.check(String.join(",", LogsActivity.TAB_IDS).equals("notify,links,contacts,overtime,attendance,notes,seacans")
      && LogsActivity.TAB_NAMES[4].equals("Attendance") && LogsActivity.TAB_NAMES[2].equals("Contacts"), "contacts after links, attendance after overtime");
  NoteTest.check(LogsActivity.firstRowCount(7)==4 && LogsActivity.firstRowCount(6)==3 && LogsActivity.firstRowCount(2)==2, "pill rows");
  System.out.println("TABS PASS"); } }
class DoneTextTest { public static void main(String[] a) throws Exception {
  Models.Project p = new Models.Project(); p.name = "B44 Zone 2";
  Models.DailyLog l = new Models.DailyLog(); l.date = "2026-10-16";
  Models.Task t = new Models.Task(); t.title = "Pack up sea can 317503"; t.photosRequired = true;
  // The exact text this app sends, as lines
  String sent = SmsHelper.taskText(p, l, t, "Tanner", "12266688639");
  java.util.List<AssignmentText> r = AssignmentText.parse(java.util.Arrays.asList(sent.split("\n", -1)));
  NoteTest.check(r.size() == 1, "one found");
  AssignmentText x = r.get(0);
  NoteTest.check(x.title.equals("Pack up sea can 317503") && x.photosRequired && x.from.equals("Tanner") && x.phone.equals("12266688639")
      && x.logTitle.equals("Daily work log 16/10/2026") && x.project.equals("B44 Zone 2"), "exact text " + x.title + "|" + x.from + "|" + x.phone + "|" + x.logTitle + "|" + x.project);
  t.photosRequired = false;
  x = AssignmentText.parse(java.util.Arrays.asList(SmsHelper.taskText(p, l, t, "Tanner", "").split("\n", -1))).get(0);
  NoteTest.check(!x.photosRequired && x.phone.isEmpty() && x.from.equals("Tanner"), "no number");
  // OCR-like screenshot: wrapped title, semicolon, odd dashes, time, Notify-style From
  java.util.List<String> ocr = java.util.Arrays.asList("< Tanner", "Daily work log 16/10/2026 (B44)", "Assigned to you; Move pallets",
      "to B07 laydown", "12:36 PM", "———————", "From: Tanner (B44 Zone 2)", "Call or text me at 1-226-668-8639",
      "Daily work log 16/10/2026 (B44)", "Assigned to you: Clean crib.", "--------------------", "From: Tanner", "Call or text me at 226 668 8639");
  r = AssignmentText.parse(ocr);
  NoteTest.check(r.size() == 2, "two found");
  NoteTest.check(r.get(0).title.equals("Move pallets to B07 laydown") && r.get(0).from.equals("Tanner") && r.get(0).phone.equals("12266688639"), "wrapped " + r.get(0).title);
  NoteTest.check(r.get(1).title.equals("Clean crib") && r.get(1).phone.equals("2266688639") && r.get(1).project.equals("B44"), "second " + r.get(1).title);
  NoteTest.check(AssignmentText.parse(java.util.Arrays.asList("hello", "see you at 7")).isEmpty(), "junk");
  // Done text
  t.done = true; t.completedAt = 0;
  String d = SmsHelper.doneText(p, l, t, "Sam", "");
  NoteTest.check(d.equals("Daily work log 16/10/2026 (B44 Zone 2)\n\nCompleted: Pack up sea can 317503\nDone by Sam, " + Fmt.stamp(0) + "\n\n--------------------\nFrom: Sam"), "done text " + d);
  t.doneTextName = "Tanner"; t.doneTextPhone = "1-226-668-8639";
  Models.Task q = Models.Task.fromJson(t.toJson());
  NoteTest.check(q.doneTextName.equals("Tanner") && q.doneTextPhone.equals("1-226-668-8639") && t.carryCopy().doneTextName.equals("Tanner"), "saved and carried");
  System.out.println("DONETEXT PASS"); } }
class SearchTest { public static void main(String[] a) throws Exception {
  Models.Project p = new Models.Project();
  java.util.Calendar cal = java.util.Calendar.getInstance(); cal.set(2026, 9, 20, 10, 15); long day20 = cal.getTimeInMillis();
  Models.DailyLog older = new Models.DailyLog(); older.date = "2026-10-20"; older.createdAt = 1; p.logs.add(older);
  Models.DailyLog newer = new Models.DailyLog(); newer.date = "2026-10-21"; newer.createdAt = 2; newer.name = "Pump day"; p.logs.add(newer);
  Models.DailyLog none = new Models.DailyLog(); none.date = "2026-10-22"; p.logs.add(none);
  Models.Task t1 = new Models.Task(); t1.title = "Move pump 3"; t1.notes = "Pump 3 is leaking"; t1.assignees.add("Pumpkin Joe"); older.tasks.add(t1);
  Models.Task t2 = new Models.Task(); t2.title = ""; t2.completedBy = "Sam"; t2.securedBy = "Sam"; t2.notes = "x"; older.tasks.add(t2);
  String longNote = "Started the shift with a safety talk and then we went out to the laydown yard where the PUMP was waiting for us all day long in the rain and the wind";
  Models.Task t3 = new Models.Task(); t3.title = "Laydown"; t3.notes = longNote; newer.tasks.add(t3);
  Models.Extra e1 = new Models.Extra(); e1.type = Models.Extra.LINK; e1.title = "Pump manual"; e1.detail = "https://x"; older.extras.add(e1);
  Models.Extra e2 = new Models.Extra(); e2.type = Models.Extra.NOTE; e2.title = "Tanner"; e2.detail = "check the pump seals"; older.extras.add(e2);
  Models.Notice n = new Models.Notice(); n.to.add("Al"); n.note = "Bring the pump key"; n.sentAt = day20; p.notices.add(n);
  Models.Notice n2 = new Models.Notice(); n2.to.add("Pumper"); n2.note = "Hi"; n2.sentAt = day20; p.notices.add(n2);
  Models.Notice n3 = new Models.Notice(); n3.to.add("Al"); n3.note = "pump other day"; n3.sentAt = day20 + 5L * 86400000L; p.notices.add(n3);
  Models.Note nt = new Models.Note(); nt.text = "Pump\n\n  hose   cracked"; nt.createdAt = day20; p.notes.add(nt);
  Models.SeaCanMove m = new Models.SeaCanMove(); m.can = "TCLU 317503"; m.fromLetter = "D"; m.fromRow = "33"; m.weight = "14"; p.seaCans.add(m);
  Models.SeaCanMove m2 = new Models.SeaCanMove(); m2.fromLetter = "D"; p.seaCans.add(m2);   // no can number
  NoteTest.check(ProjectSearch.run(p, "  ").isEmpty(), "empty query");
  ProjectSearch.Result r = ProjectSearch.run(p, "PUMP");
  NoteTest.check(r.days.size() == 2 && r.days.get(0).log == newer && r.days.get(1).log == older, "newest first, only hits");
  java.util.List<String> d = r.days.get(1).lines;
  System.out.println(d);
  NoteTest.check(d.equals(java.util.Arrays.asList("Field: Move pump 3", "Notes on Move pump 3: Pump 3 is leaking", "Move pump 3: Pumpkin Joe",
      "Also today: Link used: Pump manual", "Also today: Note by Tanner: check the pump seals", "Sent note: Bring the pump key", "Sent note: Hi",
      "Note: Pump hose cracked")), "line order " + d);
  java.util.List<String> d0 = r.days.get(0).lines;
  NoteTest.check(d0.get(0).equals("Checklist: Pump day") && d0.get(1).startsWith("Notes on Laydown: …") && d0.get(1).endsWith("…"), "snippet " + d0);
  NoteTest.check(ProjectSearch.snippet(longNote, "pump").length() == 62 && ProjectSearch.snippet(longNote, "pump").contains("PUMP"), "snippet window");
  NoteTest.check(ProjectSearch.snippet("Started the shift with a safety talk and then we went out to the laydown", "started").endsWith("…")
      && !ProjectSearch.snippet("Started the shift with a safety talk and then we went out to the laydown", "started").startsWith("…"), "snippet start");
  java.util.List<String> sam = ProjectSearch.run(p, "sam").days.get(0).lines;
  NoteTest.check(sam.equals(java.util.Arrays.asList("(Untitled field): Sam")), "first name only, untitled " + sam);
  NoteTest.check(ProjectSearch.run(p, "pump").seaCans.isEmpty(), "no sea can for pump");
  ProjectSearch.Result c = ProjectSearch.run(p, "d33");
  NoteTest.check(c.seaCans.equals(java.util.Arrays.asList("TCLU 317503 · D33 to – · 14")) && c.days.isEmpty(), "sea can line " + c.seaCans);
  NoteTest.check(ProjectSearch.run(p, "3175").seaCans.size() == 1 && ProjectSearch.run(p, "zzz").isEmpty(), "can number / nothing");
  System.out.println("SEARCH PASS"); } }
class ReportXlsxTest {
  static java.util.Map<String,String> parts(Models.Project p, Models.DailyLog l) throws Exception {
    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
    ReportXlsx.write(p, l, "Tanner", 0L, bo);
    java.util.Map<String,String> m = new java.util.HashMap<>();
    try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bo.toByteArray()))) {
      for (java.util.zip.ZipEntry e; (e = z.getNextEntry()) != null; ) m.put(e.getName(), new String(z.readAllBytes(), "UTF-8"));
    }
    javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
    for (String k : m.keySet()) f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(m.get(k).getBytes("UTF-8")));
    return m;
  }
  static String t(String s) { return "<is><t xml:space=\"preserve\">" + s + "</t></is>"; }
  public static void main(String[] a) throws Exception {
  Models.Project p = new Models.Project(); p.name = "B44"; p.location = "Site 2";
  Models.DailyLog l = new Models.DailyLog(); l.date = "2026-10-27"; p.logs.add(l);
  Models.Task t1 = new Models.Task(); t1.title = "Pack up sea can"; t1.side = "south"; t1.done = true; t1.completedBy = "Sam"; t1.completedAt = 1000; t1.nextShift = true;
  t1.loadSecured = true; t1.securedBy = "Al"; Models.Photo ph = new Models.Photo(); ph.file = "a.jpg"; t1.photos.add(ph); t1.photos.add(ph); l.tasks.add(t1);
  Models.Task t2 = new Models.Task(); t2.title = "Clean crib & <b>"; t2.side = "north"; t2.photosRequired = true; t2.notes = " wipe down "; t2.assignees.add("Jo"); t2.assignees.add("Al"); l.tasks.add(t2);
  java.util.Map<String,String> m = parts(p, l);
  NoteTest.check(m.keySet().containsAll(java.util.Arrays.asList("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml")), "zip parts");
  NoteTest.check(m.get("xl/workbook.xml").contains("name=\"Daily report\""), "sheet name");
  NoteTest.check(m.get("xl/styles.xml").contains("cellXfs count=\"13\"") && m.get("xl/styles.xml").contains("FFFFF59D"), "13 styles (with on hold fill)");
  String s = m.get("xl/worksheets/sheet1.xml");
  System.out.println(s.substring(0, Math.min(1800, s.length())));
  NoteTest.check(s.contains("<row r=\"1\" ht=\"24\" customHeight=\"1\"><c r=\"A1\" t=\"inlineStr\" s=\"2\">" + t("Daily work log 27/10/2026")), "title row");
  NoteTest.check(s.contains("<c r=\"A2\" t=\"inlineStr\" s=\"3\">" + t("B44 · Site 2")) && s.contains(t("Generated " + Fmt.longStamp(0) + " by Tanner"))
      && s.contains("<c r=\"A4\" t=\"inlineStr\" s=\"1\">" + t("1/2 fields completed")) && s.contains("<c r=\"A5\" t=\"inlineStr\" s=\"3\">" + t("Crew: Jo, Al")), "meta rows");
  NoteTest.check(s.contains("<c r=\"A7\" t=\"inlineStr\" s=\"4\">" + t("Area")) && s.contains("<c r=\"J7\" t=\"inlineStr\" s=\"4\">" + t("Photos")), "header row 7");
  NoteTest.check(s.contains("<c r=\"A8\" t=\"inlineStr\" s=\"10\">" + t("South")) && s.contains("<c r=\"A9\" t=\"inlineStr\" s=\"10\">" + t("North")), "area labels");
  NoteTest.check(s.contains("<c r=\"C8\" t=\"inlineStr\" s=\"6\">" + t("Done")) && s.contains("<c r=\"C9\" t=\"inlineStr\" s=\"7\">" + t("Not done")), "done styles");
  NoteTest.check(s.contains("<c r=\"G8\" t=\"inlineStr\" s=\"5\">" + t("Yes, Al")) && s.contains("<c r=\"H8\" t=\"inlineStr\" s=\"9\">" + t("Yes"))
      && s.contains("<c r=\"J8\" s=\"9\"><v>2</v></c>") && s.contains("<c r=\"E8\" t=\"inlineStr\" s=\"5\">" + t(Fmt.stamp(1000))), "done row");
  NoteTest.check(s.contains("<c r=\"J9\" t=\"inlineStr\" s=\"7\">" + t("Required, none")) && s.contains(t("Clean crib &amp; &lt;b&gt;"))
      && s.contains("<c r=\"I9\" t=\"inlineStr\" s=\"5\">" + t("wipe down")) && s.contains("<c r=\"D9\" s=\"5\"/>"), "not done row");
  NoteTest.check(!s.contains("Also today") && !s.contains("Weekend overtime") && !s.contains(">Attendance") && !s.contains("Sea can movements"), "no sections when not included");
  // Sections only when ticked and filled
  Models.Extra e = new Models.Extra(); e.type = Models.Extra.TEMPLATE; e.title = "Daily"; e.detail = "3 fields"; e.include = true; l.extras.add(e);
  Models.Overtime.OtRow o = new Models.Overtime.OtRow(); o.name = "Sam"; o.fri = "Yes"; o.sat = "No"; o.hours = "8"; p.overtime.rows.add(o);
  Models.Attendance.AttRow ar = new Models.Attendance.AttRow(); ar.name = "Jo"; ar.status = "AWOL"; ar.comment = "no call"; p.attendance.rows.add(ar); p.attendance.date = "2026-10-27";
  Models.SeaCanMove sc = new Models.SeaCanMove(); sc.can = "TCLU 1"; sc.toLetter = "B"; sc.toRow = "7"; sc.weight = "4.5"; p.seaCans.add(sc);
  s = parts(p, l).get("xl/worksheets/sheet1.xml");
  NoteTest.check(s.contains("<c r=\"A11\" t=\"inlineStr\" s=\"8\">" + t("Also today")) && s.contains("<c r=\"C12\" t=\"inlineStr\" s=\"4\">" + t("Details"))
      && s.contains("<c r=\"B13\" t=\"inlineStr\" s=\"5\">" + t("Template used: Daily")) && s.contains("<c r=\"C13\" s=\"5\"/>"), "also today, template detail empty");
  NoteTest.check(!s.contains("Weekend overtime") && !s.contains("Attendance Tue") && !s.contains("Sea can movements"), "not ticked yet");
  p.overtime.include = true; p.overtime.notes = "Bring harness"; p.attendance.include = true; p.seaCanInclude = true;
  o.confirmed = "Yes";
  s = parts(p, l).get("xl/worksheets/sheet1.xml");
  System.out.println(s.substring(s.indexOf("<row r=\"15\"")));
  NoteTest.check(s.contains("<c r=\"A15\" t=\"inlineStr\" s=\"8\">" + t("Weekend overtime")) && s.contains("<c r=\"E16\" t=\"inlineStr\" s=\"4\">" + t("Hours"))
      && s.contains("<c r=\"B17\" t=\"inlineStr\" s=\"6\">" + t("Yes")) && s.contains("<c r=\"C17\" t=\"inlineStr\" s=\"7\">" + t("No")) && s.contains("<c r=\"D17\" s=\"9\"/>")
      && s.contains("<c r=\"E17\" s=\"9\"><v>8</v></c>") && s.contains("<c r=\"A18\" t=\"inlineStr\" s=\"1\">" + t("Yes total")) && s.contains("<c r=\"B18\" s=\"9\"><v>1</v></c>")
      && s.contains("<c r=\"A19\" t=\"inlineStr\">" + t("Notes: Bring harness")), "overtime section");
  NoteTest.check(s.contains("<c r=\"F16\" t=\"inlineStr\" s=\"4\">" + t("Confirmed")) && s.contains("<c r=\"F17\" t=\"inlineStr\" s=\"6\">" + t("Yes"))
      && s.contains("<c r=\"F18\" s=\"9\"><v>1</v></c>"), "overtime Confirmed column");
  java.io.ByteArrayOutputStream ob = new java.io.ByteArrayOutputStream();
  OvertimeXlsx.write(p, "Tanner", 0L, ob);
  String os = "";
  try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(ob.toByteArray()))) {
    for (java.util.zip.ZipEntry ze; (ze = z.getNextEntry()) != null; ) if (ze.getName().equals("xl/worksheets/sheet1.xml")) os = new String(z.readAllBytes(), "UTF-8");
  }
  NoteTest.check(os.contains("<c r=\"F5\" t=\"inlineStr\" s=\"2\">" + t("Confirmed")) && os.contains("<c r=\"F6\" t=\"inlineStr\" s=\"3\">" + t("Yes"))
      && os.contains("<c r=\"F7\" s=\"3\"><v>1</v></c>") && os.contains("<col min=\"6\" max=\"6\" width=\"11\""), "overtime xlsx Confirmed column");
  String doc = DelayTest.docx(p, l);
  NoteTest.check(doc.contains("<w:gridCol w:w=\"3080\"/><w:gridCol w:w=\"1000\"/><w:gridCol w:w=\"1000\"/><w:gridCol w:w=\"1000\"/><w:gridCol w:w=\"1000\"/><w:gridCol w:w=\"1000\"/><w:gridCol w:w=\"2000\"/></w:tblGrid>")
      && doc.contains(">Confirmed</w:t>") && doc.contains("<w:b/><w:color w:val=\"1DB04B\"/><w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/></w:rPr><w:t xml:space=\"preserve\">Yes</w:t>"), "docx Confirmed column");
  NoteTest.check(s.contains("<c r=\"A21\" t=\"inlineStr\" s=\"8\">" + t("Attendance Tue Oct 27, 2026")) && s.contains("<c r=\"C22\" t=\"inlineStr\" s=\"4\">" + t("Comment"))
      && s.contains("<c r=\"B23\" t=\"inlineStr\" s=\"7\">" + t("AWOL")) && s.contains("<c r=\"C23\" t=\"inlineStr\" s=\"5\">" + t("no call"))
      && s.contains("<c r=\"A24\" t=\"inlineStr\" s=\"1\">" + t("AWOL 1")), "attendance section");
  NoteTest.check(s.contains("<c r=\"A26\" t=\"inlineStr\" s=\"8\">" + t("Sea can movements")) && s.contains("<c r=\"A28\" s=\"9\"><v>1</v></c>")
      && s.contains("<c r=\"C28\" t=\"inlineStr\" s=\"9\">" + t("–")) && s.contains("<c r=\"D28\" t=\"inlineStr\" s=\"9\">" + t("B7")) && s.contains("<c r=\"E28\" s=\"9\"><v>4.5</v></c>"), "sea can section");
  Models.DailyLog empty = new Models.DailyLog(); empty.date = "2026-10-28";
  Models.Project bare = new Models.Project();
  String es = parts(bare, empty).get("xl/worksheets/sheet1.xml");
  NoteTest.check(es.contains("<c r=\"B8\" t=\"inlineStr\" s=\"5\">" + t("No fields")) && es.contains("<c r=\"A8\" s=\"5\"/>") && es.contains("<row r=\"2\"></row>"), "no fields");
  System.out.println("REPORTXLSX PASS"); } }
class MailTest {
  /** A tiny SMTP server for one connection: replies from the script, records what it was sent. */
  static java.net.ServerSocket fake(java.util.List<String> got, boolean badPassword, boolean badRcpt) throws Exception {
    java.net.ServerSocket ss = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
    Thread th = new Thread(() -> {
      try (java.net.Socket s = ss.accept()) {
        java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream(), "UTF-8"));
        java.io.Writer out = new java.io.OutputStreamWriter(s.getOutputStream(), "UTF-8");
        java.util.function.Consumer<String> say = r -> { try { out.write(r + "\r\n"); out.flush(); } catch (Exception e) { throw new RuntimeException(e); } };
        say.accept("220 fake ESMTP");
        int step = 0; String line;
        while ((line = in.readLine()) != null) {
          got.add(line);
          if (step == 6) {  // inside DATA
            if (line.equals(".")) { say.accept("250 2.0.0 OK queued"); step = 7; }
            continue;
          }
          if (line.startsWith("EHLO")) { say.accept("250-fake at your service"); say.accept("250-AUTH LOGIN PLAIN"); say.accept("250 SMTPUTF8"); }
          else if (line.equals("AUTH LOGIN")) { say.accept("334 VXNlcm5hbWU6"); step = 1; }
          else if (step == 1) { say.accept("334 UGFzc3dvcmQ6"); step = 2; }
          else if (step == 2) { if (badPassword) { say.accept("535-5.7.8 Username and Password not accepted."); say.accept("535 5.7.8 Learn more"); } else say.accept("235 2.7.0 Accepted"); step = 3; }
          else if (line.startsWith("MAIL FROM")) say.accept("250 2.1.0 OK");
          else if (line.startsWith("RCPT TO")) say.accept(badRcpt ? "550 5.1.1 No such user" : "250 2.1.5 OK");
          else if (line.equals("DATA")) { say.accept("354 Go ahead"); step = 6; }
          else if (line.equals("QUIT")) { say.accept("221 bye"); break; }
          else say.accept("502 what");
        }
      } catch (Exception e) { /* client closed */ }
    });
    th.setDaemon(true); th.start();
    return ss;
  }
  public static void main(String[] a) throws Exception {
    java.util.List<String> to = java.util.Arrays.asList("boss@aecon.com", "b@y.com");
    byte[] img = new byte[200]; for (int i = 0; i < img.length; i++) img[i] = (byte) i;
    java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); cal.set(2026, 9, 30, 14, 5, 9);
    String m = Mailer.buildMessage("Tanner", "t@gmail.com", to, "Load securement - Pack up", "Line 1\nLine 2",
        java.util.Arrays.asList(new Mailer.Attachment("IMG_1.jpg", img)), "dwl-abc", cal.getTime());
    NoteTest.check(m.startsWith("From: \"Tanner\" <t@gmail.com>\r\nTo: boss@aecon.com, b@y.com\r\nSubject: Load securement - Pack up\r\nDate: Fri, 30 Oct 2026 14:05:09 +0000\r\nMIME-Version: 1.0\r\nContent-Type: multipart/mixed; boundary=\"dwl-abc\"\r\n\r\n--dwl-abc\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"), "headers\n" + m.substring(0, 300));
    NoteTest.check(m.contains(java.util.Base64.getEncoder().encodeToString("Line 1\r\nLine 2".getBytes("UTF-8")) + "\r\n"), "body base64 with CRLF");
    NoteTest.check(m.contains("--dwl-abc\r\nContent-Type: image/jpeg; name=\"IMG_1.jpg\"\r\nContent-Disposition: attachment; filename=\"IMG_1.jpg\"\r\nContent-Transfer-Encoding: base64\r\n\r\n") && m.endsWith("--dwl-abc--\r\n"), "attachment part and closing boundary");
    String b64 = java.util.Base64.getEncoder().encodeToString(img);
    String lines = Mailer.base64Lines(img);
    String[] ls = lines.split("\r\n");
    NoteTest.check(ls.length == 4 && ls[0].length() == 76 && ls[3].length() <= 76 && String.join("", ls).equals(b64) && m.contains(lines), "76-char lines");
    for (String l : m.split("\r\n")) NoteTest.check(l.length() <= 998 && l.chars().allMatch(ch -> ch < 128), "7-bit line");
    String u = Mailer.buildMessage("Zoë", "t@gmail.com", to, "Sécurité", "x", null, "b", cal.getTime());
    NoteTest.check(u.contains("Subject: =?UTF-8?B?" + java.util.Base64.getEncoder().encodeToString("Sécurité".getBytes("UTF-8")) + "?=\r\n")
        && u.contains("From: =?UTF-8?B?" + java.util.Base64.getEncoder().encodeToString("Zoë".getBytes("UTF-8")) + "?= <t@gmail.com>"), "RFC 2047");
    NoteTest.check(Mailer.encodeHeader("plain").equals("plain") && Mailer.buildMessage("", "t@gmail.com", to, "s", "x", null, "b", null).startsWith("From: <t@gmail.com>"), "plain header, no name");
    // addresses
    NoteTest.check(Mailer.addresses(" a@x.com, b@y.com;c@z.ca ").equals(java.util.Arrays.asList("a@x.com", "b@y.com", "c@z.ca")) && Mailer.validList("a@x.com, b@y.com")
        && !Mailer.validList("a@x") && !Mailer.validList(" ") && !Mailer.validList("a@x.com, nope"), "addresses");
    // groups of at most 18 MB
    java.util.List<Mailer.Attachment> big = new java.util.ArrayList<>();
    for (int i = 0; i < 5; i++) big.add(new Mailer.Attachment("p" + i, new byte[7 * 1024 * 1024]));
    java.util.List<java.util.List<Mailer.Attachment>> g = Mailer.groups(big, Mailer.MAX_GROUP_BYTES);
    NoteTest.check(g.size() == 3 && g.get(0).size() == 2 && g.get(1).size() == 2 && g.get(2).size() == 1, "18 MB groups");
    NoteTest.check(Mailer.groups(java.util.Arrays.asList(new Mailer.Attachment("huge", new byte[20 * 1024 * 1024])), Mailer.MAX_GROUP_BYTES).size() == 1
        && Mailer.groups(new java.util.ArrayList<>(), 10).isEmpty(), "big photo alone");
    // SMTP dialogue
    java.util.List<String> got = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    java.net.ServerSocket ss = fake(got, false, false);
    String msg = ".starts with a dot\r\n" + m;
    Mailer.send("127.0.0.1", ss.getLocalPort(), false, "t@gmail.com", "abcd efgh", to, msg, 5000);
    Thread.sleep(200);
    NoteTest.check(got.get(0).equals("EHLO dailyworklog") && got.get(1).equals("AUTH LOGIN")
        && got.get(2).equals(java.util.Base64.getEncoder().encodeToString("t@gmail.com".getBytes())) && got.get(3).equals(java.util.Base64.getEncoder().encodeToString("abcd efgh".getBytes()))
        && got.get(4).equals("MAIL FROM:<t@gmail.com>") && got.get(5).equals("RCPT TO:<boss@aecon.com>") && got.get(6).equals("RCPT TO:<b@y.com>") && got.get(7).equals("DATA"), "dialogue " + got.subList(0, 8));
    NoteTest.check(got.get(8).equals("..starts with a dot") && got.get(9).startsWith("From: ") && got.contains(".") && got.get(got.size() - 1).equals("QUIT"), "data, dot-stuffing, QUIT");
    // 535 -> plain words
    java.net.ServerSocket s2 = fake(new java.util.ArrayList<>(), true, false);
    String err = null;
    try { Mailer.send("127.0.0.1", s2.getLocalPort(), false, "t@gmail.com", "x", to, m, 5000); } catch (Mailer.MailException e) { err = e.getMessage(); }
    NoteTest.check("Gmail didn't accept the address or app password. Check them in Email sending.".equals(err), "535 " + err);
    java.net.ServerSocket s3 = fake(new java.util.ArrayList<>(), false, true);
    err = null;
    try { Mailer.send("127.0.0.1", s3.getLocalPort(), false, "t@gmail.com", "x", to, m, 5000); } catch (Mailer.MailException e) { err = e.getMessage(); }
    NoteTest.check("Gmail wouldn't send to boss@aecon.com.".equals(err), "550 " + err);
    // nobody listening
    java.net.ServerSocket s4 = new java.net.ServerSocket(0); int dead = s4.getLocalPort(); s4.close();
    err = null;
    try { Mailer.send("127.0.0.1", dead, false, "t@gmail.com", "x", to, m, 2000); } catch (Mailer.MailException e) { err = e.getMessage(); }
    NoteTest.check(Mailer.OFFLINE.equals(err), "offline " + err);
    // the email body
    Models.Project p = new Models.Project(); p.name = "B44";
    Models.DailyLog l = new Models.DailyLog(); l.date = "2026-10-30";
    Models.Task t = new Models.Task(); t.title = " ";
    NoteTest.check(LoadEmail.defaultSubject().equals("Load securement - ") && LoadEmail.blankSubject("Load securement - ")
        && LoadEmail.finalSubject(" Load securement -  ").equals("Load securement") && LoadEmail.finalSubject("Load securement - 410018").equals("Load securement - 410018"), "default subject");
    NoteTest.check("410018".equals(CanNumber.find(java.util.Arrays.asList("BPPU", "410018 5", "42U6", "30,480 KG"))) && CanNumber.find(java.util.Arrays.asList("30,480 KG")) == null, "can number");
    t.title = "Pack up sea can";
    NoteTest.check(LoadEmail.body(p, l, t, "Tanner", 0L, 3).equals("Load securement photos\nField: Pack up sea can\nChecklist: Daily work log 30/10/2026 (B44)\nSecured by Tanner, "
        + Fmt.stamp(0L) + "\n3 photos attached.\n\nSent from Daily Work Log"), "body");
    // Task JSON
    Models.Photo ph = new Models.Photo(); ph.file = "IMG_L.jpg"; ph.addedBy = "Tanner"; t.securedPhotos.add(ph); t.securedPhotosInReport = false;
    Models.Task q = Models.Task.fromJson(t.toJson());
    NoteTest.check(q.securedPhotos.size() == 1 && q.securedPhotos.get(0).file.equals("IMG_L.jpg") && !q.securedPhotosInReport, "secured photos round trip");
    org.json.JSONObject old = t.toJson(); old.remove("securedPhotos"); old.remove("securedPhotosInReport");
    Models.Task o = Models.Task.fromJson(old);
    NoteTest.check(o.securedPhotos.isEmpty() && o.securedPhotosInReport, "old task: [] / true");
    NoteTest.check(t.freshCopy().securedPhotos.isEmpty() && t.freshCopy().securedPhotosInReport && t.carryCopy().securedPhotos.isEmpty() && t.carryCopy().securedPhotosInReport, "copies reset");
    System.out.println("MAIL PASS"); } }
class RichTest {
  static final Rich.Style B = new Rich.Style(true, false, "", false), U = new Rich.Style(false, true, "", false);
  static String show(java.util.List<Rich.Segment> segs) {
    java.util.List<String> o = new java.util.ArrayList<>();
    for (Rich.Segment x : segs) o.add(x.text + "[" + x.style + "]");
    return String.join("|", o);
  }
  static java.util.List<Rich.Run> runs(Rich.Run... r) { return java.util.Arrays.asList(r); }
  static void eq(Object got, Object want, String name) {
    if (!got.equals(want)) System.out.println("got  " + got + "\nwant " + want);
    NoteTest.check(got.equals(want), name);
  }
  public static void main(String[] a) throws Exception {
    java.util.List<Rich.Run> none = java.util.Collections.emptyList();
    // 1-3 segments
    eq(show(Rich.segments("Crane delayed, on  hold now", none, true)), "Crane []|delayed[b,red]|, []|on  hold[b,hl]| now[]", "1. Delay red bold, on hold bold highlighted");
    eq(show(Rich.segments("abc", runs(new Rich.Run(0, 2, B)), false)), "ab[b]|c[]", "2. user bold, keywords off");
    eq(show(Rich.segments("Delay", runs(new Rich.Run(0, 5, new Rich.Style(false, true, "orange", false))), true)), "Delay[b,u,red]", "3. Delay beats the user colour, keeps underline");
    // 4-6 toggles and colour
    java.util.List<Rich.Run> r = Rich.applyToggle("hello", none, 1, 3, false);
    eq(r, runs(new Rich.Run(1, 3, B)), "4. bold on");
    eq(Rich.applyToggle("hello", r, 1, 3, false), none, "4. bold off again");
    eq(Rich.applyToggle("hello", runs(new Rich.Run(0, 2, B)), 1, 4, false), runs(new Rich.Run(0, 4, B)), "5. partly bold -> all bold");
    eq(Rich.applyColour("hello", runs(new Rich.Run(0, 5, B)), 1, 3, "red"),
        runs(new Rich.Run(0, 1, B), new Rich.Run(1, 3, new Rich.Style(true, false, "red", false)), new Rich.Run(3, 5, B)), "6. colour inside bold");
    // 7-10 edits
    eq(Rich.afterEdit("hello", "heXYllo", runs(new Rich.Run(0, 5, B)), null), runs(new Rich.Run(0, 7, B)), "7. typed inside bold stays bold");
    eq(Rich.afterEdit("hello", "hello!", runs(new Rich.Run(0, 5, B)), null), runs(new Rich.Run(0, 6, B)), "8. typed at the end");
    eq(Rich.afterEdit("hello", "hllo", runs(new Rich.Run(1, 3, U)), null), runs(new Rich.Run(1, 2, U)), "9. deleted e");
    eq(Rich.afterEdit("ab", "aXb", none, B), runs(new Rich.Run(1, 2, B)), "10. typing style");
    // 11 bad runs, relay, plurals, json
    org.json.JSONArray bad = new org.json.JSONArray("[{\"s\":3,\"e\":3,\"b\":true},{\"s\":0,\"e\":99,\"u\":true},{\"s\":0,\"e\":2,\"c\":\"pink\"}]");
    eq(Rich.fromJson(bad, 5), runs(new Rich.Run(0, 5, U)), "11. bad runs dropped, clipped");
    eq(show(Rich.segments("relay", none, true)), "relay[]", "11. relay is not a delay");
    eq(show(Rich.segments("Delays DELAYED", none, true)), "Delays[b,red]| []|DELAYED[b,red]", "11. Delays DELAYED");
    eq(Rich.toJson(runs(new Rich.Run(1, 3, new Rich.Style(true, false, "red", false)))).toString(), "[{\"s\":1,\"e\":3,\"b\":true,\"c\":\"red\"}]", "11. json keys");
    // Saved with tasks and notes; carried copies keep the notes' formatting, fresh copies don't.
    Models.Project p = new Models.Project();
    Models.DailyLog l = new Models.DailyLog(); l.date = "2026-11-10"; p.logs.add(l);
    Models.Task t = new Models.Task(); t.title = "Crane delay"; t.notes = "Pump on hold today"; t.notesStyle = runs(new Rich.Run(0, 4, B)); l.tasks.add(t);
    Models.Note n = new Models.Note(); n.text = "Seal replaced"; n.textStyle = runs(new Rich.Run(5, 13, new Rich.Style(false, true, "orange", false))); p.notes.add(n);
    Models.Project q = Models.Project.fromJson(p.toJson());
    eq(q.logs.get(0).tasks.get(0).notesStyle, t.notesStyle, "notesStyle saved");
    eq(q.notes.get(0).textStyle, n.textStyle, "textStyle saved");
    eq(t.carryCopy().notesStyle, t.notesStyle, "carryCopy keeps notesStyle");
    NoteTest.check(t.freshCopy().notesStyle.isEmpty(), "freshCopy has none");
    org.json.JSONObject old = t.toJson(); old.remove("notesStyle");
    NoteTest.check(Models.Task.fromJson(old).notesStyle.isEmpty(), "old task: no formatting");
    org.json.JSONObject cut = t.toJson(); cut.put("notes", "Pu");
    eq(Models.Task.fromJson(cut).notesStyle, runs(new Rich.Run(0, 2, B)), "runs clipped to the text");
    // Reports: Excel rich cell + on hold fill; Word runs.
    Models.Extra e = p.record(null, l.date, Models.Extra.NOTE, n.id, "Tanner", n.text, n.photos, "Tanner", 1L); e.include = true;
    String s = ReportXlsxTest.parts(p, l).get("xl/worksheets/sheet1.xml");
    NoteTest.check(s.contains("<c r=\"B8\" t=\"inlineStr\" s=\"5\"><is><r><rPr><rFont val=\"Calibri\"/><color rgb=\"FF000000\"/><sz val=\"11\"/></rPr><t xml:space=\"preserve\">Crane </t></r>"
        + "<r><rPr><rFont val=\"Calibri\"/><b/><color rgb=\"FFD93B3F\"/><sz val=\"11\"/></rPr><t xml:space=\"preserve\">delay</t></r></is></c>"), "xlsx title Delay red bold");
    NoteTest.check(s.contains("<c r=\"I8\" t=\"inlineStr\" s=\"11\"><is><r><rPr><rFont val=\"Calibri\"/><b/><color rgb=\"FF000000\"/><sz val=\"11\"/></rPr><t xml:space=\"preserve\">Pump</t></r>"), "xlsx notes bold + on hold fill");
    NoteTest.check(s.contains("<color rgb=\"FFE8730C\"/><sz val=\"11\"/><u/></rPr><t xml:space=\"preserve\">replaced</t>"), "xlsx also-today note keeps its formatting");
    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
    ReportDocx.write(p, l, "Tanner", 0L, new java.io.File("."), f -> null, bo);
    String doc = "";
    try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bo.toByteArray()))) {
      for (java.util.zip.ZipEntry ze; (ze = z.getNextEntry()) != null; ) if (ze.getName().equals("word/document.xml")) doc = new String(z.readAllBytes(), "UTF-8");
    }
    javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new java.io.ByteArrayInputStream(doc.getBytes("UTF-8")));
    NoteTest.check(doc.contains("<w:b/><w:color w:val=\"D93B3F\"/><w:sz w:val=\"26\"/><w:szCs w:val=\"26\"/></w:rPr><w:t xml:space=\"preserve\">delay</w:t>"), "docx title Delay red");
    NoteTest.check(doc.contains("<w:b/><w:color w:val=\"1B2533\"/><w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/><w:highlight w:val=\"yellow\"/></w:rPr><w:t xml:space=\"preserve\">on hold</w:t>"), "docx on hold highlighted");
    NoteTest.check(doc.contains("<w:color w:val=\"E8730C\"/><w:sz w:val=\"18\"/><w:szCs w:val=\"18\"/><w:u w:val=\"single\"/></w:rPr><w:t xml:space=\"preserve\">replaced</w:t>"), "docx note underline orange");
    System.out.println("RICH PASS"); } }
class DelayTest {
  static String docx(Models.Project p, Models.DailyLog l) throws Exception {
    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
    ReportDocx.write(p, l, "Tanner", 0L, new java.io.File("."), f -> null, bo);
    try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bo.toByteArray()))) {
      for (java.util.zip.ZipEntry ze; (ze = z.getNextEntry()) != null; ) if (ze.getName().equals("word/document.xml")) return new String(z.readAllBytes(), "UTF-8");
    }
    return "";
  }
  public static void main(String[] a) throws Exception {
    Models.Project p = new Models.Project();
    Models.DailyLog l = new Models.DailyLog(); l.date = "2026-11-10"; p.logs.add(l);
    Models.Task t = new Models.Task(); t.title = "Lift pump"; t.notes = "Bring slings"; t.notesStyle = java.util.Arrays.asList(new Rich.Run(6, 12, RichTest.B));
    t.delayed = true; t.delayReason = "Waiting on crane"; t.delayedBy = "Sam"; t.delayedAt = 1000; l.tasks.add(t);
    Models.Task t2 = new Models.Task(); t2.title = "Sweep"; l.tasks.add(t2);
    // JSON round trip and old data
    Models.Task q = Models.Project.fromJson(p.toJson()).logs.get(0).tasks.get(0);
    NoteTest.check(q.delayed && q.delayReason.equals("Waiting on crane") && q.delayedBy.equals("Sam") && q.delayedAt == 1000, "delay saved");
    org.json.JSONObject old = t.toJson(); old.remove("delayed"); old.remove("delayReason"); old.remove("delayedBy"); old.remove("delayedAt");
    Models.Task o = Models.Task.fromJson(old);
    NoteTest.check(!o.delayed && o.delayReason.isEmpty() && o.delayedBy.isEmpty() && o.delayedAt == 0, "old data: not delayed");
    Models.Task c = t.carryCopy(), f = t.freshCopy();
    NoteTest.check(c.delayed && c.delayReason.equals(t.delayReason) && c.delayedBy.equals("Sam") && c.delayedAt == 1000, "carryCopy keeps the delay");
    NoteTest.check(!f.delayed && f.delayReason.isEmpty() && f.delayedBy.isEmpty() && f.delayedAt == 0, "freshCopy drops it");
    // Excel: status Delayed (red), notes start with the reason, notes formatting shifted
    String s = ReportXlsxTest.parts(p, l).get("xl/worksheets/sheet1.xml");
    NoteTest.check(s.contains("<c r=\"C8\" t=\"inlineStr\" s=\"7\">" + ReportXlsxTest.t("Delayed")) && s.contains("<c r=\"C9\" t=\"inlineStr\" s=\"7\">" + ReportXlsxTest.t("Not done")), "xlsx status Delayed / Not done");
    String r = "<r><rPr><rFont val=\"Calibri\"/>";
    NoteTest.check(s.contains("<c r=\"I8\" t=\"inlineStr\" s=\"5\"><is>" + r + "<b/><color rgb=\"FFD93B3F\"/><sz val=\"11\"/></rPr><t xml:space=\"preserve\">Delayed</t></r>"
        + r + "<color rgb=\"FF000000\"/><sz val=\"11\"/></rPr><t xml:space=\"preserve\">: Waiting on crane\nBring </t></r>"
        + r + "<b/><color rgb=\"FF000000\"/><sz val=\"11\"/></rPr><t xml:space=\"preserve\">slings</t></r></is></c>"), "xlsx notes prefix, runs shifted");
    t.notes = ""; t.notesStyle = new java.util.ArrayList<>();
    s = ReportXlsxTest.parts(p, l).get("xl/worksheets/sheet1.xml");
    NoteTest.check(s.contains("<t xml:space=\"preserve\">: Waiting on crane</t></r></is></c>"), "xlsx no notes: just the reason");
    t.notes = "Bring slings";
    // Word: red bold line then grey who/when
    String d = docx(p, l);
    NoteTest.check(d.contains("<w:b/><w:color w:val=\"D93B3F\"/><w:sz w:val=\"18\"/><w:szCs w:val=\"18\"/></w:rPr><w:t xml:space=\"preserve\">Delayed: Waiting on crane</w:t></w:r>"
        + "<w:r><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\"/><w:color w:val=\"6B7685\"/><w:sz w:val=\"18\"/><w:szCs w:val=\"18\"/></w:rPr><w:t xml:space=\"preserve\"> (Sam, " + Fmt.stamp(1000) + ")</w:t>"), "docx delayed line");
    t.delayedBy = "";
    NoteTest.check(docx(p, l).contains(">Delayed: Waiting on crane</w:t></w:r></w:p>"), "docx no who: no brackets");
    // Search
    java.util.List<String> lines = ProjectSearch.run(p, "crane").days.get(0).lines;
    NoteTest.check(lines.equals(java.util.Arrays.asList("Lift pump: Delayed: Waiting on crane")), "search reason " + lines);
    lines = ProjectSearch.run(p, "delayed").days.get(0).lines;
    NoteTest.check(lines.equals(java.util.Arrays.asList("Lift pump: Delayed: Waiting on crane")), "search delayed " + lines);
    t.delayed = false;
    NoteTest.check(ProjectSearch.run(p, "crane").isEmpty(), "not delayed: no line");
    cleared(p, l, t);
    System.out.println("DELAY PASS"); }

  static void cleared(Models.Project p, Models.DailyLog l, Models.Task t) throws Exception {
    long h = 3600000L, m = 60000L, t0 = 1_700_000_000_000L;
    t.delayed = true; t.delayReason = "Waiting on crane"; t.delayedBy = "Sam"; t.delayedAt = t0;
    t.delayCleared = true; t.delayClearedBy = "Jo"; t.delayClearedAt = t0 + 2 * h + 15 * m; t.delayGoAhead = "Rick";
    // JSON round trip and old data
    Models.Task q = Models.Task.fromJson(t.toJson());
    NoteTest.check(q.delayCleared && q.delayClearedBy.equals("Jo") && q.delayClearedAt == t.delayClearedAt && q.delayGoAhead.equals("Rick") && q.delayed, "cleared saved, delay kept");
    org.json.JSONObject old = t.toJson(); for (String k : new String[]{"delayCleared", "delayClearedBy", "delayClearedAt", "delayGoAhead"}) old.remove(k);
    Models.Task o = Models.Task.fromJson(old);
    NoteTest.check(!o.delayCleared && o.delayClearedBy.isEmpty() && o.delayClearedAt == 0 && o.delayGoAhead.isEmpty() && o.delayed, "old data: not cleared");
    // carryCopy: a cleared delay doesn't carry; an open one does (without cleared fields)
    Models.Task c = t.carryCopy();
    NoteTest.check(!c.delayed && c.delayReason.isEmpty() && !c.delayCleared && c.delayClearedAt == 0 && c.delayGoAhead.isEmpty(), "carryCopy drops a cleared delay");
    Models.Task f = t.freshCopy();
    NoteTest.check(!f.delayed && !f.delayCleared && f.delayGoAhead.isEmpty(), "freshCopy: none");
    // Not delayed clears everything
    Models.Task n = Models.Task.fromJson(t.toJson()); n.clearDelay();
    NoteTest.check(!n.delayed && n.delayReason.isEmpty() && n.delayedBy.isEmpty() && n.delayedAt == 0 && !n.delayCleared && n.delayClearedBy.isEmpty()
        && n.delayClearedAt == 0 && n.delayGoAhead.isEmpty(), "Not delayed clears all");
    // delayLength
    Models.Task d = new Models.Task(); d.delayedAt = t0;
    String[][] cases = {{"45", "45 min"}, {"120", "2 h"}, {"135", "2 h 15 min"}, {"1620", "1 day 3 h"}, {"1", "1 min"}};
    for (String[] cs : cases) { d.delayClearedAt = t0 + Long.parseLong(cs[0]) * m + 30000; RichTest.eq(Models.Task.delayLength(d), cs[1], "delayLength " + cs[1]); }
    d.delayClearedAt = t0 + 59000; RichTest.eq(Models.Task.delayLength(d), "under a minute", "delayLength under a minute");
    d.delayClearedAt = 0; RichTest.eq(Models.Task.delayLength(d), "", "delayLength empty: not cleared");
    d.delayClearedAt = t0 - m; RichTest.eq(Models.Task.delayLength(d), "", "delayLength empty: cleared before delayed");
    d.delayedAt = 0; d.delayClearedAt = t0; RichTest.eq(Models.Task.delayLength(d), "", "delayLength empty: no delay time");
    d.delayedAt = t0; d.delayClearedAt = t0 + 50 * h; RichTest.eq(Models.Task.delayLength(d), "2 days 2 h", "delayLength 2 days");
    RichTest.eq(t.clearedDetails(), " (Jo, go-ahead from Rick, delayed 2 h 15 min)", "cleared details");
    // Excel: status Not done once cleared; notes prefix with cleared line; green bold words; runs shifted
    t.notes = "Bring slings"; t.notesStyle = java.util.Arrays.asList(new Rich.Run(6, 12, RichTest.B));
    String s = ReportXlsxTest.parts(p, l).get("xl/worksheets/sheet1.xml");
    NoteTest.check(s.contains("<c r=\"C8\" t=\"inlineStr\" s=\"7\">" + ReportXlsxTest.t("Not done")), "xlsx cleared: Not done");
    String r = "<r><rPr><rFont val=\"Calibri\"/>", st = Fmt.stamp(t.delayClearedAt);
    String want = "<c r=\"I8\" t=\"inlineStr\" s=\"5\"><is>" + r + "<b/><color rgb=\"FFD93B3F\"/><sz val=\"11\"/></rPr><t xml:space=\"preserve\">Delayed</t></r>"
        + r + "<color rgb=\"FF000000\"/><sz val=\"11\"/></rPr><t xml:space=\"preserve\">: Waiting on crane\n</t></r>"
        + r + "<b/><color rgb=\"FF148A3A\"/><sz val=\"11\"/></rPr><t xml:space=\"preserve\">Delay cleared</t></r>"
        + r + "<color rgb=\"FF000000\"/><sz val=\"11\"/></rPr><t xml:space=\"preserve\">: " + st + " (go-ahead from Rick)\nBring </t></r>"
        + r + "<b/><color rgb=\"FF000000\"/><sz val=\"11\"/></rPr><t xml:space=\"preserve\">slings</t></r></is></c>";
    if (!s.contains(want)) System.out.println(s.substring(s.indexOf("<c r=\"I8\""), s.indexOf("<c r=\"J8\"")));
    NoteTest.check(s.contains(want), "xlsx notes: delayed + cleared prefix, green words, runs shifted");
    t.delayGoAhead = ""; t.notes = "";
    s = ReportXlsxTest.parts(p, l).get("xl/worksheets/sheet1.xml");
    NoteTest.check(s.contains("<t xml:space=\"preserve\">: " + st + "</t></r></is></c>"), "xlsx no go-ahead, no notes");
    t.delayGoAhead = "Rick";
    // Word
    String doc = docx(p, l);
    NoteTest.check(doc.contains("<w:b/><w:color w:val=\"148A3A\"/><w:sz w:val=\"18\"/><w:szCs w:val=\"18\"/></w:rPr><w:t xml:space=\"preserve\">Delay cleared: " + st + "</w:t></w:r>"
        + "<w:r><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\"/><w:color w:val=\"6B7685\"/><w:sz w:val=\"18\"/><w:szCs w:val=\"18\"/></w:rPr><w:t xml:space=\"preserve\"> (Jo, go-ahead from Rick, delayed 2 h 15 min)</w:t>"), "docx cleared line");
    t.delayClearedBy = ""; t.delayGoAhead = ""; t.delayClearedAt = 0;
    NoteTest.check(docx(p, l).contains(">Delay cleared: " + Fmt.stamp(0) + "</w:t></w:r></w:p>"), "docx cleared: no bracket when nothing to add");
    t.delayClearedAt = t0 + 2 * h;
    // Search
    java.util.List<String> lines = ProjectSearch.run(p, "cleared").days.get(0).lines;
    RichTest.eq(lines, java.util.Arrays.asList("Lift pump: Delay cleared " + Fmt.stamp(t.delayClearedAt)), "search cleared");
    lines = ProjectSearch.run(p, "delay").days.get(0).lines;
    RichTest.eq(lines, java.util.Arrays.asList("Lift pump: Delayed: Waiting on crane", "Lift pump: Delay cleared " + Fmt.stamp(t.delayClearedAt)), "search delay: both lines");
    t.delayCleared = false;
    NoteTest.check(ProjectSearch.run(p, "cleared").isEmpty(), "not cleared: no line");
  } }
class ProfileTest {
  public static void main(String[] a) throws Exception {
    // Store JSON round trip with the new profile details
    Store s = new Store();
    s.userName = "Tanner"; s.contactPhone = "5195550123"; s.userEmail = "tanner@example.com"; s.workPosition = "Foreman"; s.workLocation = "Main plant, Building 2";
    s.formDetails.answers.put("project", "FCFR");
    Models.Project p = new Models.Project(); p.name = "Warehouse expansion"; s.projects.add(p);
    org.json.JSONObject j = s.toJson();
    NoteTest.check(j.getString("userEmail").equals("tanner@example.com") && j.getString("workPosition").equals("Foreman") && j.getString("workLocation").equals("Main plant, Building 2"), "profile keys written");
    Store t = new Store(); t.fromJson(new org.json.JSONObject(j.toString()));
    NoteTest.check(t.userName.equals("Tanner") && t.contactPhone.equals("5195550123") && t.userEmail.equals("tanner@example.com")
        && t.workPosition.equals("Foreman") && t.workLocation.equals("Main plant, Building 2") && t.projects.size() == 1
        && t.formDetails.get("project").equals("FCFR"), "profile round trip");
    j.remove("userEmail"); j.remove("workPosition"); j.remove("workLocation");
    Store o = new Store(); o.fromJson(j);
    NoteTest.check(o.userEmail.isEmpty() && o.workPosition.isEmpty() && o.workLocation.isEmpty() && o.userName.equals("Tanner"), "old data: profile blank");
    // Email check
    for (String good : new String[]{"tanner@example.com", "a.b@c.co", " x@y.z "}) NoteTest.check(Fmt.isEmail(good), "email ok: " + good);
    for (String bad : new String[]{"", "tanner", "tanner@", "tanner@example", "@example.com", "tan ner@example.com", "a@b@c.com"}) NoteTest.check(!Fmt.isEmail(bad), "email bad: '" + bad + "'");
    // Observation form: profile fills email / position / location when the saved answer is blank; a saved answer wins
    FormPrefill.Details saved = new FormPrefill.Details();
    saved.answers.put("position", "FM"); saved.answers.put("location", "  "); saved.answers.put("project", "FCFR");
    FormPrefill.Details d = FormPrefill.withProfile(saved, "Tanner", "tanner@example.com", "Foreman", "B44 zone 2");
    NoteTest.check(d.get("email").equals("tanner@example.com") && d.get("position").equals("FM") && d.get("location").equals("B44 zone 2")
        && d.get("project").equals("FCFR") && d.name.equals("Tanner"), "form prefill falls back to profile");
    NoteTest.check(saved.get("email").isEmpty() && saved.get("location").isEmpty(), "saved answers untouched");
    NoteTest.check(FormPrefill.withProfile(new FormPrefill.Details(), "", "", "", "").isEmpty()
        && !FormPrefill.withProfile(new FormPrefill.Details(), "", "", "Foreman", "").isEmpty(), "empty unless something is set");
    NoteTest.check(FormPrefill.QUESTIONS[1].label.equals("Project") && FormPrefill.QUESTIONS[2].label.equals("Site")
        && FormPrefill.QUESTIONS[1].keys[0].equals("bruce power project"), "generic labels, real-form keywords kept");
    System.out.println("PROFILE PASS"); } }
class TagsTest {
  public static void main(String[] a) throws Exception {
    // JSON: only known tags, fixed order, missing = []
    Models.Note n = new Models.Note(); n.text = "Loose rail";
    org.json.JSONObject j = n.toJson();
    NoteTest.check(j.getJSONArray("tags").length() == 0, "tags always written");
    j.put("tags", new org.json.JSONArray("[\"Crew concern\", \"Bogus\", \"Safety concern\", \"Crew concern\", \"training\"]"));
    Models.Note q = Models.Note.fromJson(j);
    RichTest.eq(q.tags, java.util.Arrays.asList("Safety concern", "Crew concern"), "unknown dropped, fixed order, no repeats");
    RichTest.eq(Models.Note.fromJson(q.toJson()).tags, q.tags, "tags round trip");
    j.remove("tags");
    NoteTest.check(Models.Note.fromJson(j).tags.isEmpty(), "old note: no tags");
    Models.Note only = new Models.Note(); only.tags = java.util.Arrays.asList("Training");
    NoteTest.check(only.isEmpty(), "tags alone don't keep a new note");
    // Rich.tagged
    Rich.Style ob = new Rich.Style(true, false, "orange", false);
    Rich.Tagged t = Rich.tagged("Loose rail", null, java.util.Arrays.asList("Safety concern", "Crew concern"));
    RichTest.eq(t.text, "Safety concern, Crew concern: Loose rail", "tagged text");
    RichTest.eq(t.runs, java.util.Arrays.asList(new Rich.Run(0, 28, ob)), "orange bold over the tag names only");
    t = Rich.tagged("Loose rail", java.util.Arrays.asList(new Rich.Run(0, 5, RichTest.U)), java.util.Arrays.asList("Training"));
    RichTest.eq(t.text, "Training: Loose rail", "one tag");
    RichTest.eq(t.runs, java.util.Arrays.asList(new Rich.Run(0, 8, ob), new Rich.Run(10, 15, RichTest.U)), "note runs shifted by the prefix");
    t = Rich.tagged("", null, java.util.Arrays.asList("Training", "Crew concern"));
    RichTest.eq(t.text, "Training, Crew concern", "empty text: just the tags, no colon");
    RichTest.eq(t.runs, java.util.Arrays.asList(new Rich.Run(0, 22, ob)), "empty text runs");
    java.util.List<Rich.Run> own = java.util.Arrays.asList(new Rich.Run(1, 3, RichTest.B));
    t = Rich.tagged("Loose rail", own, new java.util.ArrayList<>());
    NoteTest.check(t.text.equals("Loose rail") && t.runs.equals(own), "no tags: unchanged");
    RichTest.eq(show(Rich.segments(Rich.tagged("Loose rail", null, java.util.Arrays.asList("Safety concern")).text,
        Rich.tagged("Loose rail", null, java.util.Arrays.asList("Safety concern")).runs, true)), "Safety concern[b,orange]|: Loose rail[]", "tagged segments");
    // Reports and search
    Models.Project p = new Models.Project();
    Models.DailyLog l = new Models.DailyLog(); l.date = Fmt.todayIso(); p.logs.add(l);
    Models.Note m = new Models.Note(); m.text = "Loose rail"; m.by = "Sam"; m.tags = java.util.Arrays.asList("Safety concern"); p.notes.add(m);
    Models.Extra e = p.record(null, l.date, Models.Extra.NOTE, m.id, m.by, m.text, m.photos, "Sam", 1L); e.include = true;
    String s = ReportXlsxTest.parts(p, l).get("xl/worksheets/sheet1.xml");
    NoteTest.check(s.contains("<b/><color rgb=\"FFE8730C\"/><sz val=\"11\"/></rPr><t xml:space=\"preserve\">Safety concern</t></r>")
        && s.contains("<t xml:space=\"preserve\">: Loose rail</t>"), "xlsx also today: tags in front");
    String d = DelayTest.docx(p, l);
    NoteTest.check(d.contains("<w:b/><w:color w:val=\"E8730C\"/><w:sz w:val=\"18\"/><w:szCs w:val=\"18\"/></w:rPr><w:t xml:space=\"preserve\">Safety concern</w:t>"), "docx also today: tags in front");
    Models.Note photoOnly = new Models.Note(); photoOnly.tags = java.util.Arrays.asList("Crew concern"); photoOnly.by = "Sam";
    Models.Photo ph = new Models.Photo(); ph.file = "a.jpg"; photoOnly.photos.add(ph); p.notes.add(photoOnly);
    Models.Extra e2 = p.record(null, l.date, Models.Extra.NOTE, photoOnly.id, "Sam", "", photoOnly.photos, "Sam", 2L); e2.include = true;
    NoteTest.check(ReportXlsx.detail(p, e2).text.equals("Crew concern") && DelayTest.docx(p, l).contains(">Crew concern</w:t>"), "tags-only note still printed");
    java.util.List<String> lines = ProjectSearch.run(p, "safety").days.get(0).lines;
    NoteTest.check(lines.contains("Note: Safety concern: Loose rail"), "search finds tag " + lines);
    System.out.println("TAGS PASS"); }
  static String show(java.util.List<Rich.Segment> s) { return RichTest.show(s); } }
class GroupsTest {
  public static void main(String[] a) throws Exception {
    // Defaults and round trip
    Models.Project p = new Models.Project();
    java.util.List<String> names = new java.util.ArrayList<>(); for (Models.Project.Group g : p.groups) names.add(g.id + "=" + g.name);
    RichTest.eq(names, java.util.Arrays.asList("crew=Crew", "tech=Techs", "mgmt=Supervision"), "default groups");
    org.json.JSONObject j = p.toJson();
    NoteTest.check(j.getJSONArray("groups").length() == 3, "groups written");
    j.remove("groups"); j.remove("levels"); j.remove("trades"); j.remove("unionLocals");
    Models.Project old = Models.Project.fromJson(j);
    NoteTest.check(old.groups.size() == 3 && old.groups.get(1).name.equals("Techs") && old.levels.isEmpty() && old.trades.isEmpty() && old.unionLocals.isEmpty(), "old data: default groups, no details");
    j.put("groups", new org.json.JSONArray());
    NoteTest.check(Models.Project.fromJson(j).groups.size() == 3, "empty groups list: defaults");
    p.crew.add("Al"); p.crew.add("Bo"); p.crew.add("Cy");
    p.groups.get(1).name = "Mechanics";
    Models.Project.Group night = p.addGroup("  Night shift ");
    NoteTest.check(night.name.equals("Night shift") && !night.id.isEmpty() && p.groups.get(3) == night, "new group at the end, trimmed, new id");
    p.roles.put("Bo", night.id); p.roles.put("Cy", "gone");
    p.levels.put("Al", "Journeyman"); p.trades.put("Al", "Millwright"); p.unionLocals.put("Al", "Local 1687");
    Models.Project q = Models.Project.fromJson(p.toJson());
    NoteTest.check(q.groups.size() == 4 && q.groups.get(1).name.equals("Mechanics") && q.groups.get(3).id.equals(night.id) && q.roleOf("Bo").equals(night.id),
        "groups round trip");
    NoteTest.check(q.levelOf("Al").equals("Journeyman") && q.tradeOf("Al").equals("Millwright") && q.localOf("Al").equals("Local 1687"), "details round trip");
    RichTest.eq(q.personLine("Al"), "Millwright · Journeyman · Local 1687", "person line");
    NoteTest.check(q.personLine("Bo").isEmpty(), "person line empty");
    // Unknown or missing group -> first group
    NoteTest.check(q.roleOf("Cy").equals("crew") && q.roleOf("Nobody").equals("crew"), "unknown group -> first group");
    // Levels filtering
    org.json.JSONObject lj = p.toJson(); lj.put("levels", new org.json.JSONObject("{\"Al\":\"Journeyman\",\"Bo\":\"Foreman\",\"Cy\":\"apprentice\",\"Di\":\"Apprentice\"}"));
    Models.Project lv = Models.Project.fromJson(lj);
    RichTest.eq(new java.util.TreeMap<>(lv.levels).toString(), "{Al=Journeyman, Di=Apprentice}", "levels: only Apprentice / Journeyman");
    // Names taken (any case)
    NoteTest.check(p.groupNameTaken("night SHIFT", "") && !p.groupNameTaken("Night shift", night.id) && !p.groupNameTaken("Day", ""), "group name check");
    // Delete group moves people to the first group left
    p.deleteGroup(night.id);
    NoteTest.check(p.groups.size() == 3 && p.group(night.id) == null && p.roleOf("Bo").equals("crew") && "crew".equals(p.roles.get("Bo")), "delete group moves people");
    p.roles.put("Al", "crew"); p.roles.put("Cy", "tech");
    p.deleteGroup("crew");
    NoteTest.check(p.groups.get(0).id.equals("tech") && p.roleOf("Al").equals("tech") && p.roleOf("Bo").equals("tech") && p.roleOf("Cy").equals("tech"),
        "deleting the first group: everyone to the new first");
    p.deleteGroup("tech"); p.deleteGroup("mgmt");
    NoteTest.check(p.groups.size() == 1, "the last group can't be deleted");
    // renamePerson moves levels / trades / locals
    Models.Project r = new Models.Project(); r.crew.add("Jon");
    r.levels.put("Jon", "Apprentice"); r.trades.put("Jon", "Teamster"); r.unionLocals.put("Jon", "Local 128"); r.roles.put("Jon", "mgmt");
    r.renamePerson("Jon", "Jon Smith");
    NoteTest.check(r.levelOf("Jon Smith").equals("Apprentice") && r.tradeOf("Jon Smith").equals("Teamster") && r.localOf("Jon Smith").equals("Local 128")
        && r.roleOf("Jon Smith").equals("mgmt") && r.levels.get("Jon") == null && r.trades.get("Jon") == null && r.unionLocals.get("Jon") == null, "rename moves details");
    // putOrRemove: blank removes
    Models.Project.putOrRemove(r.trades, "Jon Smith", "  ");
    NoteTest.check(r.tradeOf("Jon Smith").isEmpty() && !r.trades.containsKey("Jon Smith"), "blank removes");
    // Store: trade options default vs empty list; local options default []
    Store s = new Store();
    RichTest.eq(s.tradeOptions, java.util.Arrays.asList("Boilermaker", "Millwright", "Teamster", "Electrician", "Labourer", "Pipefitter"), "default trades");
    NoteTest.check(s.localOptions.isEmpty(), "no locals by default");
    org.json.JSONObject sj = s.toJson(); sj.remove("tradeOptions"); sj.remove("localOptions");
    Store s2 = new Store(); s2.fromJson(sj);
    NoteTest.check(s2.tradeOptions.size() == 6 && s2.localOptions.isEmpty(), "old file: default trades");
    s.tradeOptions.clear(); s.localOptions.add("Local 128");
    Store s3 = new Store(); s3.fromJson(new org.json.JSONObject(s.toJson().toString()));
    NoteTest.check(s3.tradeOptions.isEmpty() && s3.localOptions.equals(java.util.Arrays.asList("Local 128")), "empty trade list stays empty; locals saved");
    // Adding an option: no duplicates, any case
    java.util.List<String> opts = new java.util.ArrayList<>(java.util.Arrays.asList("Millwright"));
    NoteTest.check(CrewDialog.addOption(opts, " millwright ").equals("Millwright") && opts.size() == 1, "add trade: no duplicate");
    NoteTest.check(CrewDialog.addOption(opts, "Ironworker").equals("Ironworker") && opts.size() == 2, "add trade");
    // CrewDialog.Person save
    Models.Project cp = new Models.Project(); cp.crew.add("Al");
    CrewDialog.Person per = new CrewDialog.Person(); per.name = "Al"; per.group = "tech"; per.level = "Journeyman"; per.trade = "Pipefitter"; per.local = ""; per.phone = "555"; per.texts = true;
    per.saveTo(cp, "Al");
    CrewDialog.Person back = CrewDialog.Person.of(cp, "Al");
    NoteTest.check(back.group.equals("tech") && back.level.equals("Journeyman") && back.trade.equals("Pipefitter") && back.local.isEmpty() && back.phone.equals("555")
        && back.texts && !cp.unionLocals.containsKey("Al"), "person box saves group, level, trade, local");
    System.out.println("GROUPS PASS"); } }
class ContactsTest {
  static Models.Contact c(String name, String company, String title, String phone, String email, String notes) {
    Models.Contact x = new Models.Contact(); x.name = name; x.company = company; x.title = title; x.phone = phone; x.email = email; x.notes = notes; return x;
  }
  static String names(java.util.List<Models.Contact> l) { java.util.List<String> o = new java.util.ArrayList<>(); for (Models.Contact x : l) o.add(x.name); return String.join(",", o); }
  public static void main(String[] a) throws Exception {
    // JSON round trip, missing = []
    Store s = new Store();
    NoteTest.check(s.contacts.isEmpty(), "no contacts by default");
    Models.Contact k = c("Pat Lee", "Acme Power", "Safety coordinator", "519-555-0100", "pat@acme.com", "Gate 3 badge office");
    k.link = "https://acme.com/safety"; s.contacts.add(k);
    org.json.JSONObject j = s.toJson();
    Store t = new Store(); t.fromJson(new org.json.JSONObject(j.toString()));
    Models.Contact b = t.contacts.get(0);
    NoteTest.check(t.contacts.size() == 1 && b.id.equals(k.id) && b.name.equals("Pat Lee") && b.company.equals("Acme Power") && b.title.equals("Safety coordinator")
        && b.phone.equals("519-555-0100") && b.email.equals("pat@acme.com") && b.link.equals("https://acme.com/safety") && b.notes.equals("Gate 3 badge office"), "contact round trip");
    j.remove("contacts");
    Store o = new Store(); o.fromJson(j);
    NoteTest.check(o.contacts.isEmpty(), "old file: no contacts");
    Models.Contact partial = Models.Contact.fromJson(new org.json.JSONObject("{\"name\":\"Jo\"}"));
    NoteTest.check(partial.name.equals("Jo") && partial.company.isEmpty() && partial.link.isEmpty() && !partial.id.isEmpty(), "missing fields blank");
    RichTest.eq(k.role(), "Safety coordinator · Acme Power", "title · company");
    RichTest.eq(c("A", "Acme", "", "", "", "").role(), "Acme", "company only");
    RichTest.eq(c("A", "", "Boss", "", "", "").role(), "Boss", "title only");
    // Search matching and sort order
    java.util.List<Models.Contact> all = new java.util.ArrayList<>();
    all.add(c("zoe Park", "Hydro One", "", "", "", "")); all.add(k); all.add(c("al Smith", "", "Crane operator", "226-555-0199", "al@x.ca", "Night shift"));
    all.add(c("Bea", "", "", "", "", ""));
    RichTest.eq(names(Models.Contact.list(all, "")), "al Smith,Bea,Pat Lee,zoe Park", "sorted by name, any case");
    RichTest.eq(names(Models.Contact.list(all, "HYDRO")), "zoe Park", "matches company");
    RichTest.eq(names(Models.Contact.list(all, "crane")), "al Smith", "matches title");
    RichTest.eq(names(Models.Contact.list(all, "555-01")), "al Smith,Pat Lee", "matches phone");
    RichTest.eq(names(Models.Contact.list(all, "acme.com")), "Pat Lee", "matches email");
    RichTest.eq(names(Models.Contact.list(all, "night")), "al Smith", "matches notes");
    RichTest.eq(names(Models.Contact.list(all, "bea")), "Bea", "matches name");
    k.link = "https://secret.example"; NoteTest.check(Models.Contact.list(all, "secret").isEmpty(), "link isn't searched");
    // Tab rows
    String[] want = {"", "1", "2", "3", "3,1", "3,2", "3,3", "4,3", "4,4", "3,3,3", "3,3,3,1"};
    for (int n = 1; n <= 10; n++) {
      int[] r = LogsActivity.tabRowSizes(n);
      java.util.List<String> parts = new java.util.ArrayList<>(); int sum = 0; for (int x : r) { parts.add(String.valueOf(x)); sum += x; }
      NoteTest.check(String.join(",", parts).equals(want[n]) && sum == n, "tab rows for " + n + ": " + parts);
    }
    NoteTest.check(LogsActivity.firstRowCount(8) == 4 && LogsActivity.firstRowCount(9) == 3, "first row 8 -> 4, 9 -> 3");
    // Web links
    RichTest.eq(Fmt.webLink("example.com"), "https://example.com", "adds https://");
    RichTest.eq(Fmt.webLink("  http://Example.com/a b "), "http://Example.com/ab", "keeps http, drops spaces");
    RichTest.eq(Fmt.webLink("https://acme.com/safety?x=1"), "https://acme.com/safety?x=1", "full link unchanged");
    NoteTest.check(Fmt.webLink("") == null && Fmt.webLink("https://") == null && Fmt.webLink("not a{link") == null, "invalid links refused");
    RichTest.eq(Fmt.normalizeUrl("www.acme.com"), "https://www.acme.com", "normalize like Links");
    System.out.println("CONTACTS PASS"); } }
class RequestEmailTest {
  public static void main(String[] a) throws Exception {
    // Owner email from assets/owner_email.txt
    RichTest.eq(AccessCodes.ownerEmailFrom(null), "", "missing file: none");
    RichTest.eq(AccessCodes.ownerEmailFrom(""), "", "empty: none");
    RichTest.eq(AccessCodes.ownerEmailFrom("  \n "), "", "blank: none");
    RichTest.eq(AccessCodes.ownerEmailFrom("tanner at example"), "", "not an email: none");
    RichTest.eq(AccessCodes.ownerEmailFrom("tanner@example"), "", "no dot: none");
    RichTest.eq(AccessCodes.ownerEmailFrom("  tanner@example.com \r\n"), "tanner@example.com", "valid, trimmed");
    RichTest.eq(AccessCodes.ownerEmailFrom("tanner@example.com\nsecond line"), "tanner@example.com", "first line only");
    // mailto
    String code = AccessCodes.requestCode("3f2b8c1e-9a7d-4e21-b5c6-0d8e7f6a5b4c", "Jo Smith");
    String body = AccessCodes.requestEmailBody("Jo Smith", code);
    RichTest.eq(AccessCodes.requestSubject("Jo Smith"), "Daily Work Log access request from Jo Smith", "subject");
    RichTest.eq(body, "Daily Work Log access request from Jo Smith:\n" + code + "\n\nTo approve: copy this whole email, open Daily Work Log, "
        + "tap the person icon, More…, Approvals / hand over, and paste it.", "body");
    RichTest.eq(AccessCodes.requestText("Jo Smith", code), "Daily Work Log access request from Jo Smith:\n" + code, "text as before");
    String m = AccessCodes.mailto("tanner@example.com", "Hi Jo & Al", "a b\nc:d?e=f/…");
    RichTest.eq(m, "mailto:tanner@example.com?subject=Hi%20Jo%20%26%20Al&body=a%20b%0Ac%3Ad%3Fe%3Df%2F%E2%80%A6", "mailto encoding");
    NoteTest.check(!m.contains("+"), "no + for spaces");
    android.net.Uri u = android.net.Uri.parse(AccessCodes.mailto("tanner@example.com", AccessCodes.requestSubject("Jo Smith"), body));
    NoteTest.check("mailto".equals(u.getScheme()), "parses as mailto");
    String full = AccessCodes.mailto("tanner@example.com", AccessCodes.requestSubject("Jo Smith"), body);
    String q = full.substring(full.indexOf('?') + 1);
    String[] kv = q.split("&");
    RichTest.eq(java.net.URLDecoder.decode(kv[1].substring(5), "UTF-8"), body, "body decodes back");
    // parseRequest: plain, emailed, wrapped, quoted
    AccessCodes.Request r = AccessCodes.parseRequest(AccessCodes.requestText("Jo Smith", code));
    NoteTest.check(r != null && r.name.equals("Jo Smith") && r.installId.equals("3f2b8c1e-9a7d-4e21-b5c6-0d8e7f6a5b4c"), "text message");
    String email = "From: Jo <jo@example.com>\nSubject: " + AccessCodes.requestSubject("Jo Smith") + "\n\n" + body + "\n\nSent from my phone";
    r = AccessCodes.parseRequest(email);
    NoteTest.check(r != null && r.name.equals("Jo Smith"), "whole email");
    String c = code;
    String wrapped = "Daily Work Log access request from Jo Smith:\n" + c.substring(0, 70) + "\n" + c.substring(70) + "\n\nTo approve: copy";
    r = AccessCodes.parseRequest(wrapped);
    NoteTest.check(r != null && r.name.equals("Jo Smith"), "code wrapped onto the next line (" + c.length() + " chars)");
    String quoted = "On Tue, Jo wrote:\n> Daily Work Log access request from Jo Smith:\n> " + c.substring(0, 66) + "\r\n> " + c.substring(66) + "\r\n>\n> To approve: copy this whole email";
    r = AccessCodes.parseRequest(quoted);
    NoteTest.check(r != null && r.name.equals("Jo Smith") && r.installId.equals("3f2b8c1e-9a7d-4e21-b5c6-0d8e7f6a5b4c"), "quoted and wrapped");
    String nested = "> > " + c.substring(0, 64) + "\n> > " + c.substring(64, 72) + "\n> > " + c.substring(72) + "\n";
    NoteTest.check(AccessCodes.parseRequest(nested) != null && AccessCodes.parseRequest(nested).name.equals("Jo Smith"), "split over three quoted lines");
    // A short code followed by a word on the next line keeps its name
    String shortCode = AccessCodes.requestCode("x1", "Al");
    r = AccessCodes.parseRequest("request:\n" + shortCode + "\nThanks");
    NoteTest.check(r != null && r.name.equals("Al") && r.installId.equals("x1"), "short code then Thanks");
    // A long code line followed by a word: the word isn't taken as code
    r = AccessCodes.parseRequest(code + "\nThanks\n");
    NoteTest.check(r != null && r.name.equals("Jo Smith"), "long code then Thanks");
    r = AccessCodes.parseRequest(code + " thanks");
    NoteTest.check(r != null && r.name.equals("Jo Smith"), "code then words on the same line");
    NoteTest.check(AccessCodes.parseRequest("nothing here") == null && AccessCodes.parseRequest("DWL-REQ-") == null
        && AccessCodes.parseRequest("DWL-REQ-abc") == null, "no code / bad code");
    // A second code is used if the first is broken
    r = AccessCodes.parseRequest("DWL-REQ-zzzz\n" + code);
    NoteTest.check(r != null && r.name.equals("Jo Smith"), "broken first code, good second");
    System.out.println("REQUEST EMAIL PASS"); } }
class BackupTest {
  static java.io.File tmp(String name) throws Exception {
    java.io.File d = java.nio.file.Files.createTempDirectory("dwl-" + name).toFile(); d.deleteOnExit(); return d;
  }
  static void write(java.io.File f, byte[] b) throws Exception { java.nio.file.Files.write(f.toPath(), b); }
  static byte[] read(java.io.File f) throws Exception { return java.nio.file.Files.readAllBytes(f.toPath()); }
  public static void main(String[] a) throws Exception {
    // A store with a project and two photos
    java.io.File home = tmp("home");
    Store s = new Store(home);
    s.userName = "Tanner"; s.workPosition = "Foreman";
    Models.Project p = new Models.Project(); p.name = "Warehouse expansion"; p.crew.add("Al"); s.projects.add(p);
    Models.Link l = new Models.Link(); l.title = "Procedures"; l.url = "https://example.com"; s.links.add(l);
    byte[] jpgA = new byte[70000]; new java.util.Random(1).nextBytes(jpgA);
    write(new java.io.File(s.photoDir(), "a.jpg"), jpgA);
    write(new java.io.File(s.photoDir(), "b.jpg"), "second photo".getBytes("UTF-8"));
    s.save();
    java.io.File zip = Backup.create(s, tmp("out"));
    RichTest.eq(zip.getName(), "DailyWorkLog-backup-" + Fmt.todayIso() + ".zip", "file name");
    try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(zip)) {
      NoteTest.check(z.getEntry("worklog.json") != null && z.getEntry("photos/a.jpg") != null && z.getEntry("photos/b.jpg") != null && z.size() == 3, "entries");
      NoteTest.check(z.getEntry("photos/a.jpg").getMethod() == java.util.zip.ZipEntry.STORED && z.getEntry("photos/a.jpg").getSize() == 70000, "photos STORED");
      NoteTest.check(java.util.Arrays.equals(z.getInputStream(z.getEntry("photos/a.jpg")).readAllBytes(), jpgA), "photo bytes");
      org.json.JSONObject j = new org.json.JSONObject(new String(z.getInputStream(z.getEntry("worklog.json")).readAllBytes(), "UTF-8"));
      NoteTest.check(j.getString("userName").equals("Tanner") && j.getJSONArray("projects").length() == 1, "worklog.json is the store");
    }
    // inspect
    Backup.Info info = Backup.inspect(zip);
    NoteTest.check(info != null && info.projects == 1 && info.photos == 2 && info.userName.equals("Tanner"), "inspect");
    RichTest.eq(MainActivity.restoreMessage(info), "Backup from Tanner: 1 project, 2 photos.\n\nThis replaces everything on this phone with the backup. It can't be undone.", "confirm text");
    RichTest.eq(MainActivity.restoreMessage(new Backup.Info(3, 1, " ")), "Backup: 3 projects, 1 photo.\n\nThis replaces everything on this phone with the backup. It can't be undone.", "confirm text, no name");
    // Restore into another store that has its own data
    java.io.File other = tmp("other");
    Store t = new Store(other);
    t.userName = "Old phone"; Models.Project old = new Models.Project(); old.name = "Old project"; t.projects.add(old); t.hiddenTabs.add("notes"); t.tradeOptions.clear();
    t.save(); t.flush();
    NoteTest.check(Backup.restore(t, zip), "restored");
    NoteTest.check(t.userName.equals("Tanner") && t.workPosition.equals("Foreman") && t.projects.size() == 1 && t.projects.get(0).name.equals("Warehouse expansion")
        && t.links.size() == 1 && t.hiddenTabs.isEmpty() && t.tradeOptions.size() == 6, "data replaced, not added to");
    NoteTest.check(java.util.Arrays.equals(read(new java.io.File(t.photoDir(), "a.jpg")), jpgA) && new java.io.File(t.photoDir(), "b.jpg").exists(), "photos written");
    java.io.File[] before = other.listFiles((d, n) -> n.startsWith("worklog-before-restore-") && n.endsWith(".json"));
    NoteTest.check(before != null && before.length == 1 && new String(read(before[0]), "UTF-8").contains("Old project"), "copy kept before restoring");
    Store reloaded = new Store(other);
    NoteTest.check(reloaded.userName.equals("Tanner") && reloaded.projects.size() == 1, "saved to worklog.json");
    // Not a backup
    java.io.File notZip = new java.io.File(tmp("bad"), "notes.zip"); write(notZip, "hello".getBytes("UTF-8"));
    NoteTest.check(Backup.inspect(notZip) == null && !Backup.restore(new Store(tmp("x")), notZip), "not a zip");
    java.io.File noData = new java.io.File(tmp("bad2"), "photos.zip");
    try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(noData))) {
      z.putNextEntry(new java.util.zip.ZipEntry("photos/a.jpg")); z.write(1); z.closeEntry();
    }
    Store untouched = new Store(tmp("y")); untouched.userName = "Keep me";
    NoteTest.check(Backup.inspect(noData) == null && !Backup.restore(untouched, noData) && untouched.userName.equals("Keep me"), "zip without worklog.json");
    // A zip laid out like the new app's (worklog.json deflated, photos stored), with bad photo names
    java.io.File fromNew = new java.io.File(tmp("new"), "DailyWorkLog-backup-2026-11-12.zip");
    try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(fromNew))) {
      z.putNextEntry(new java.util.zip.ZipEntry("worklog.json"));
      z.write("{\"version\":2,\"userName\":\"Pat\",\"projects\":[{\"id\":\"p1\",\"name\":\"From iPhone\",\"crew\":[\"Bo\"]}],\"links\":[]}".getBytes("UTF-8"));
      z.closeEntry();
      for (String n : new String[]{"photos/good.jpg", "photos/", "photos/../evil.jpg", "photos/sub/x.jpg", "photos/..jpg", "other/z.jpg"}) {
        byte[] b = n.getBytes("UTF-8");
        java.util.zip.ZipEntry e = new java.util.zip.ZipEntry(n);
        if (!n.endsWith("/")) {
          java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(b);
          e.setMethod(java.util.zip.ZipEntry.STORED); e.setSize(b.length); e.setCompressedSize(b.length); e.setCrc(crc.getValue());
        }
        z.putNextEntry(e); if (!n.endsWith("/")) z.write(b); z.closeEntry();
      }
    }
    java.io.File third = tmp("third");
    Store u = new Store(third);
    NoteTest.check(Backup.restore(u, fromNew) && u.userName.equals("Pat") && u.projects.get(0).name.equals("From iPhone") && u.projects.get(0).crew.contains("Bo"), "new app's backup restores");
    String[] names = u.photoDir().list();
    java.util.Arrays.sort(names);
    RichTest.eq(String.join(",", names), "good.jpg", "bad photo names skipped");
    NoteTest.check(!new java.io.File(third, "evil.jpg").exists() && !new java.io.File(u.photoDir(), "sub").exists(), "nothing written outside photos");
    NoteTest.check(Backup.photoName(new java.util.zip.ZipEntry("photos/ok.jpg")).equals("ok.jpg") && Backup.photoName(new java.util.zip.ZipEntry("photos/a..b.jpg")) == null, "photo name check");
    System.out.println("BACKUP PASS");
    System.exit(0);  // the stores' save threads
  } }
class OtCommentTest {
  public static void main(String[] a) throws Exception {
    Models.Overtime.OtRow r = new Models.Overtime.OtRow(); r.name = "Al"; r.confirmed = "Training"; r.comment = "First aid course Sat";
    Models.Overtime.OtRow q = Models.Overtime.OtRow.fromJson(r.toJson());
    NoteTest.check(q.confirmed.equals("Training") && q.comment.equals("First aid course Sat"), "comment + Training round trip");
    org.json.JSONObject old = r.toJson(); old.remove("comment");
    NoteTest.check(Models.Overtime.OtRow.fromJson(old).comment.isEmpty(), "old row: no comment");
    Models.Overtime ot = new Models.Overtime();
    Models.Overtime.OtRow onlyComment = new Models.Overtime.OtRow(); onlyComment.comment = "Ask Bo";
    ot.rows.add(onlyComment); ot.rows.add(new Models.Overtime.OtRow());
    NoteTest.check(!Models.Overtime.isBlank(onlyComment), "a comment makes a row not empty");
    ot.removeEmptyRows(); NoteTest.check(ot.rows.size() == 1 && ot.rows.get(0) == onlyComment, "remove empty rows keeps a comment-only row");
    Models.Overtime.OtRow y = new Models.Overtime.OtRow(); y.name = "Bo"; y.confirmed = "Yes";
    ot.rows.add(r); ot.rows.add(y);
    NoteTest.check(ot.confirmedCount() == 1, "confirmedCount ignores Training");
    // Excel daily report, Overtime Excel, Word
    Models.Project p = new Models.Project(); p.overtime = ot; ot.include = true;
    Models.DailyLog l = new Models.DailyLog(); l.date = "2026-11-13"; p.logs.add(l);
    String s = ReportXlsxTest.parts(p, l).get("xl/worksheets/sheet1.xml");
    NoteTest.check(s.contains(ReportXlsxTest.t("Comment")) && s.contains(">" + ReportXlsxTest.t("First aid course Sat")) && s.contains(ReportXlsxTest.t("Training")), "xlsx daily: Comment column");
    int at = s.indexOf(ReportXlsxTest.t("Training"));
    NoteTest.check(s.substring(s.lastIndexOf("<c ", at), at).contains("s=\"9\""), "xlsx daily: Training plain");
    java.io.ByteArrayOutputStream ob = new java.io.ByteArrayOutputStream();
    OvertimeXlsx.write(p, "Tanner", 0L, ob);
    String os = "";
    try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(ob.toByteArray()))) {
      for (java.util.zip.ZipEntry ze; (ze = z.getNextEntry()) != null; ) if (ze.getName().equals("xl/worksheets/sheet1.xml")) os = new String(z.readAllBytes(), "UTF-8");
    }
    NoteTest.check(os.contains("<c r=\"G5\" t=\"inlineStr\" s=\"2\">" + ReportXlsxTest.t("Comment")) && os.contains(ReportXlsxTest.t("First aid course Sat"))
        && os.contains("t=\"inlineStr\">" + ReportXlsxTest.t("Training")), "overtime xlsx: Comment column, Training plain");
    String d = DelayTest.docx(p, l);
    NoteTest.check(d.contains(">Comment</w:t>") && d.contains(">First aid course Sat</w:t>") && d.contains("<w:gridCol w:w=\"2000\"/>"), "docx: Comment column");
    NoteTest.check(d.contains("<w:color w:val=\"1B2533\"/><w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/></w:rPr><w:t xml:space=\"preserve\">Training</w:t>"), "docx: Training plain");
    System.out.println("OT COMMENT PASS"); } }
class FormsTest {
  static String qs(FormReader.Result r) {
    java.util.List<String> o = new java.util.ArrayList<>();
    for (Models.FormQuestion q : r.questions) o.add(q.label + "(" + q.kind + ")");
    return String.join(" | ", o);
  }
  static final String SAMPLE = "entice reviews / Save as / Print / Save to OneDrive / DATE: / NAME: / EMPLOYEE # / EMPLOYER / TERM: / "
      + "PERIOD COVERED FROM: / TO: / A. WORK DONE IN THE ABOVE PERIOD: / B. 1. Improvement shown in above period / "
      + "2. Understanding of work / 3. Progress in learning this phase of work / 4. Work quality / "
      + "5. Interest shown in this period / 6. Attendance / 7. Punctuality / 8. Co-operation / 9. Safety Attitude / "
      + "10. Mechanical skills / Point System / Excellent 4.0 / Average Plus 3.0 / Average 2.0 / Average Minus 1.0 / "
      + "Poor 0.0 / Overall average of above: / (total #1 - #10 and divide by 10) / "
      + "C. Your overall comment on the apprentice: / Signature Of Supervisor / Signature Of Steward / "
      + "Signature Of Apprenticeship Chairman / Signature Of Apprentice / cc: Apprentice @@ Personnel @@ File / "
      + "FW: Apprentice Reviews / (No subject)";
  static java.util.List<String> sampleLines() {
    java.util.List<String> out = new java.util.ArrayList<>();
    for (String l : SAMPLE.split(" / ")) out.add(l.replace(" @@ ", " / "));
    return out;
  }
  static Models.ReportForm sampleForm() {
    FormReader.Result r = FormReader.read(sampleLines());
    Models.ReportForm f = new Models.ReportForm(); f.name = "Apprentice review"; f.questions = r.questions; f.signatures = r.signatures;
    return f;
  }
  public static void main(String[] a) throws Exception {
    // The sample form
    FormReader.Result r = FormReader.read(sampleLines());
    String ratings = "Improvement shown in above period(rating) | Understanding of work(rating) | Progress in learning this phase of work(rating) | "
        + "Work quality(rating) | Interest shown in this period(rating) | Attendance(rating) | Punctuality(rating) | Co-operation(rating) | "
        + "Safety Attitude(rating) | Mechanical skills(rating)";
    RichTest.eq(qs(r), "Date(date) | Name(text) | Employee #(text) | Employer(text) | Term(text) | Period covered from(date) | "
        + "Period covered to(date) | Work done in the above period(long) | " + ratings + " | Overall average(average) | "
        + "Your overall comment on the apprentice(long)", "sample questions");
    RichTest.eq(r.signatures, java.util.Arrays.asList("Signature Of Supervisor", "Signature Of Steward", "Signature Of Apprenticeship Chairman",
        "Signature Of Apprentice"), "sample signatures");
    RichTest.eq(r.questions.get(8).choices.toString(), "[Excellent (4.0), Average Plus (3.0), Average (2.0), Average Minus (1.0), Poor (0.0)]", "rating choices");
    NoteTest.check(r.questions.get(0).id.equals("q1") && r.questions.get(19).id.equals("q20") && r.questions.size() == 20, "ids q1..q20");
    for (Models.FormQuestion q : r.questions) if (!q.kind.equals("rating")) NoteTest.check(q.choices.isEmpty(), "no choices on " + q.label);
    // Other line shapes
    RichTest.eq(qs(FormReader.read(java.util.Arrays.asList("NAME: ____ DATE: ____"))), "Name(text) | Date(date)", "two blanks on a line");
    RichTest.eq(qs(FormReader.read(java.util.Arrays.asList("Employer ________"))), "Employer(text)", "label then blank");
    RichTest.eq(qs(FormReader.read(java.util.Arrays.asList("cc: Apprentice / Personnel / File", "FW: Apprentice Reviews", "Supervisor:"))), "Supervisor(text)", "cc / FW lines aren't questions");
    RichTest.eq(qs(FormReader.read(java.util.Arrays.asList("Reason for discipline:", "Describe what happened ......", "Was the employee warned before?",
        "1. Attitude", "Signature of employee ______ Signature of supervisor ______"))),
        "Reason for discipline(long) | Describe what happened(long) | Was the employee warned before?(text) | Attitude(text)", "discipline form, no scale");
    RichTest.eq(FormReader.read(java.util.Arrays.asList("Signature of employee ______ Signature of supervisor ______")).signatures,
        java.util.Arrays.asList("Signature of employee", "Signature of supervisor"), "two signatures on a line");
    NoteTest.check(FormReader.read(java.util.Arrays.asList("x", "(No subject)", "Rating Scale:", "")).questions.isEmpty(), "skipped lines");
    RichTest.eq(FormReader.defaultSignatures("discipline"), java.util.Arrays.asList("Supervisor signature", "Employee signature", "Witness signature"), "default discipline signatures");
    // Word and Excel text
    java.io.File dir = java.nio.file.Files.createTempDirectory("forms").toFile();
    java.io.File docx = new java.io.File(dir, "f.docx");
    try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(docx))) {
      z.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
      z.write(("<?xml version=\"1.0\"?><w:document xmlns:w=\"x\"><w:body>"
          + "<w:p><w:pPr><w:tabs><w:tab w:val=\"left\" w:pos=\"720\"/></w:tabs></w:pPr><w:r><w:t>NAME:</w:t></w:r><w:r><w:tab/><w:t xml:space=\"preserve\">____ </w:t></w:r></w:p>"
          + "<w:p/><w:p><w:r><w:t>Tom &amp; Jerry</w:t><w:br/><w:t>Line two</w:t></w:r></w:p>"
          + "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>EMPLOYER</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>TERM:</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"
          + "</w:body></w:document>").getBytes("UTF-8"));
      z.closeEntry();
    }
    RichTest.eq(FormReader.docxLines(docx), java.util.Arrays.asList("NAME:    ____ ", "Tom & Jerry", "Line two", "EMPLOYER", "TERM:"), "docx lines");
    java.io.File xlsx = new java.io.File(dir, "f.xlsx");
    try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(xlsx))) {
      z.putNextEntry(new java.util.zip.ZipEntry("xl/sharedStrings.xml"));
      z.write("<sst><si><t>DATE:</t></si><si><r><t>Name</t></r><r><t xml:space=\"preserve\">: </t></r></si></sst>".getBytes("UTF-8"));
      z.closeEntry();
      z.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"));
      z.write(("<worksheet><sheetData><row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\"><v>5</v></c><c r=\"C1\" t=\"s\"><v>1</v></c></row>"
          + "<row r=\"2\"><c r=\"A2\" t=\"inlineStr\"><is><t>Excellent 4.0</t></is></c><c r=\"B2\" s=\"1\"/></row></sheetData></worksheet>").getBytes("UTF-8"));
      z.closeEntry();
    }
    RichTest.eq(FormReader.xlsxLines(xlsx), java.util.Arrays.asList("DATE:", "Name:", "Excellent 4.0"), "xlsx lines");
    boolean threw = false; try { FormReader.docxLines(xlsx); } catch (java.io.IOException e) { threw = true; }
    NoteTest.check(threw, "not a Word file");
    System.out.println("FORMS PASS"); } }
class AutoFillTest {
  static Models.FormQuestion q(Models.ReportForm f, String label, String kind) {
    Models.FormQuestion x = new Models.FormQuestion(f.newQuestionId(), label, kind); f.questions.add(x); return x;
  }
  public static void main(String[] a) throws Exception {
    Store s = new Store();
    s.userName = "Tanner"; s.workPosition = "Foreman"; s.userEmail = "tanner@example.com"; s.contactPhone = "5195550123"; s.workLocation = "Main plant";
    Models.Project p = new Models.Project(); p.name = "Warehouse expansion"; p.crew.add("Al"); p.crew.add("Bo"); p.crew.add("cy");
    p.levels.put("Al", "Apprentice"); p.levels.put("cy", "Apprentice"); p.trades.put("Al", "Millwright"); p.unionLocals.put("Al", "Local 1687");
    p.employeeNumbers.put("Al", "E-204"); p.phones.put("Al", "2265550199"); p.roles.put("Al", "tech");
    Models.ReportForm f = new Models.ReportForm(); f.id = "form1"; f.name = "Review";
    String[][] cases = {
      {"Supervisor name", "Tanner"}, {"Evaluated by", "Tanner"}, {"Foreman phone", "519-555-0123"}, {"Supervisor email", "tanner@example.com"},
      {"Supervisor position", "Foreman"}, {"Date", ReportFill.day(1_763_000_000_000L)}, {"Review date", ReportFill.day(1_763_000_000_000L)},
      {"Employee #", "E-204"}, {"Badge", "E-204"}, {"Name", "Al"}, {"Apprentice's name", "Al"}, {"Trade", "Millwright"},
      {"Union local", "Local 1687"}, {"Level", "Apprentice"}, {"Phone number", "226-555-0199"}, {"Project", "Warehouse expansion"},
      {"Work location", "Main plant"}, {"Crew", "Techs"}, {"Comments", ""}, {"Employer", ""}};
    Models.FilledReport r = new Models.FilledReport(); r.formId = f.id; r.person = "Al"; r.project = p.name;
    for (String[] c : cases) {
      Models.FormQuestion x = q(f, c[0], "text");
      RichTest.eq(ReportFill.suggest(s, p, f, r, x, java.util.Collections.emptyList(), 1_763_000_000_000L), c[1], "auto-fill " + c[0]);
    }
    RichTest.eq(ReportFill.day(1_763_000_000_000L), "Nov 13, 2025", "date format");
    p.location = "Building 2";
    RichTest.eq(ReportFill.suggest(s, p, f, r, f.questions.get(16), java.util.Collections.emptyList(), 0), "Building 2", "location: project's first");
    Models.FormQuestion rating = q(f, "Name", "rating");
    NoteTest.check(ReportFill.suggest(s, p, f, r, rating, java.util.Collections.emptyList(), 0).isEmpty(), "only text questions");
    // autoFill never overwrites
    Models.FormQuestion nameQ = f.questions.get(9);
    r.answers.put(nameQ.id, "Albert");
    ReportFill.autoFill(s, p, f, r, java.util.Collections.emptyList(), 1_763_000_000_000L);
    NoteTest.check(r.answer(nameQ.id).equals("Albert") && r.answer(f.questions.get(0).id).equals("Tanner"), "autoFill fills empties only");
    // Carry over Employer / Term
    Models.ReportForm g = new Models.ReportForm(); g.id = "g";
    Models.FormQuestion emp = q(g, "Employer", "text"), term = q(g, "TERM", "text");
    Models.FilledReport old1 = new Models.FilledReport(); old1.formId = "g"; old1.person = "Bo"; old1.answers.put(emp.id, "Acme"); old1.answers.put(term.id, "3"); old1.updatedAt = 10;
    Models.FilledReport old2 = new Models.FilledReport(); old2.formId = "g"; old2.person = "Al"; old2.answers.put(term.id, "2"); old2.updatedAt = 5;
    Models.FilledReport old3 = new Models.FilledReport(); old3.formId = "g"; old3.person = "Al"; old3.answers.put(term.id, "4"); old3.updatedAt = 20;
    Models.FilledReport other = new Models.FilledReport(); other.formId = "zz"; other.person = "Al"; other.answers.put(emp.id, "Wrong form");
    java.util.List<Models.FilledReport> earlier = java.util.Arrays.asList(old1, old2, old3, other);
    Models.FilledReport nr = new Models.FilledReport(); nr.formId = "g"; nr.person = "Al";
    Models.FormQuestion term2 = new Models.FormQuestion("x", "term", "text");
    RichTest.eq(ReportFill.suggest(s, p, g, nr, emp, earlier, 0), "Acme", "employer from anyone when Al has none");
    RichTest.eq(ReportFill.suggest(s, p, g, nr, term, earlier, 0), "", "term: no carry-over (Al has no term)");
    p.terms.put("Al", "2nd term");
    RichTest.eq(ReportFill.suggest(s, p, g, nr, term, earlier, 0), "2nd term", "term: the person's term");
    Models.FormQuestion dept = new Models.FormQuestion("dp", "Department", "text"); g.questions.add(dept);
    old3.answers.put(dept.id, "Maintenance");
    RichTest.eq(ReportFill.suggest(s, p, g, nr, dept, earlier, 0), "Maintenance", "department still carries over");
    Models.FilledReport cyr = new Models.FilledReport(); cyr.formId = "g"; cyr.person = "cy";
    RichTest.eq(ReportFill.suggest(s, p, g, cyr, term, earlier, 0), "", "term empty when none");
    Models.FilledReport tb = new Models.FilledReport(); tb.person = "cy";
    ReportFill.setAnswer(p, term, tb, "3rd term", 1);
    NoteTest.check(p.termOf("cy").isEmpty(), "term not saved back from a typed answer");
    // Saving back to the person
    Models.FilledReport br = new Models.FilledReport(); br.person = "Bo";
    ReportFill.setAnswer(p, new Models.FormQuestion("a", "Employee no.", "text"), br, " B-7 ", 5);
    ReportFill.setAnswer(p, new Models.FormQuestion("b", "Trade", "text"), br, "Teamster", 5);
    ReportFill.setAnswer(p, new Models.FormQuestion("c", "Local", "text"), br, "Local 128", 5);
    ReportFill.setAnswer(p, new Models.FormQuestion("d", "Trade", "text"), r, "Welder", 5);  // Al has one already
    NoteTest.check(p.employeeNumberOf("Bo").equals("B-7") && p.tradeOf("Bo").equals("Teamster") && p.localOf("Bo").equals("Local 128")
        && p.tradeOf("Al").equals("Millwright") && br.updatedAt == 5 && br.answer("a").equals(" B-7 "), "saved back only when empty");
    // Average
    Models.ReportForm sf = FormsTest.sampleForm();
    Models.FilledReport ar = new Models.FilledReport();
    RichTest.eq(ReportFill.average(sf, ar), "", "no ratings: no average");
    ar.answers.put("q9", "Excellent"); ar.answers.put("q10", "Average"); ar.answers.put("q11", "average minus"); ar.answers.put("q12", "nonsense");
    RichTest.eq(ReportFill.average(sf, ar), "2.3", "average of 4, 2, 1");
    NoteTest.check(ReportFill.missing(sf, ar).size() == 20 - 1 - 4, "missing skips answered and the average");
    // People
    RichTest.eq(ReportFill.people(p, "apprentice"), java.util.Arrays.asList("Al", "cy"), "apprentices A-Z");
    RichTest.eq(ReportFill.people(p, "discipline"), java.util.Arrays.asList("Al", "Bo", "cy"), "everyone A-Z");
    // JSON round trips
    Models.Document d = new Models.Document(); d.title = "Lifting plan"; d.file = "doc-1.pdf"; d.kind = Models.Document.kindOf("Plan.PDF");
    s.documents.add(d); sf.files.add("doc-2.png"); s.reportForms.add(sf);
    Models.FilledReport fr = new Models.FilledReport(); fr.formId = sf.id; fr.formName = sf.name; fr.person = "Al"; fr.project = p.name; fr.answers.put("q1", "Nov 13, 2026");
    s.filledReports.add(fr); s.projects.add(p);
    Store t = new Store(); t.fromJson(new org.json.JSONObject(s.toJson().toString()));
    NoteTest.check(t.documents.get(0).title.equals("Lifting plan") && t.documents.get(0).kind.equals("pdf") && t.documents.get(0).file.equals("doc-1.pdf"), "documents round trip");
    Models.ReportForm tf = t.reportForms.get(0);
    NoteTest.check(tf.questions.size() == 20 && tf.question("q9").choices.size() == 5 && tf.question("q9").choices.get(0).value.equals("4.0")
        && tf.signatures.size() == 4 && tf.files.equals(sf.files) && tf.name.equals("Apprentice review"), "forms round trip");
    NoteTest.check(t.filledReports.get(0).answer("q1").equals("Nov 13, 2026") && t.filled(fr.id).person.equals("Al") && t.form(sf.id) != null, "filled reports round trip");
    NoteTest.check(t.projects.get(0).employeeNumberOf("Al").equals("E-204"), "employee numbers round trip");
    org.json.JSONObject old = s.toJson(); old.remove("documents"); old.remove("reportForms"); old.remove("filledReports");
    Store o = new Store(); o.fromJson(old);
    NoteTest.check(o.documents.isEmpty() && o.reportForms.isEmpty() && o.filledReports.isEmpty(), "old file: none");
    p.renamePerson("Al", "Alan");
    NoteTest.check(p.employeeNumberOf("Alan").equals("E-204") && p.employeeNumberOf("Al").isEmpty(), "rename moves employee #");
    RichTest.eq(Models.Document.kindOf("x.jpeg") + Models.Document.kindOf("a.docx") + Models.Document.kindOf("b.XLSX") + Models.Document.kindOf("c.txt"), "photodocxxlsxother", "kinds from names");
    RichTest.eq(ReportFill.pdfName(fr, "Apprentice review").replaceAll("\\d{4}-\\d{2}-\\d{2}", "D"), "Apprentice review - Al D.pdf", "pdf name");
    System.out.println("AUTOFILL PASS"); } }
class CrewNumbersTest {
  public static void main(String[] a) throws Exception {
    // Tab-separated, as pasted from Excel, with an empty column between DISN # and Position
    java.util.List<CrewNumbers.Row> r = CrewNumbers.parse("Aecon PRNNR #\tBP #\tDISN #\t\tPosition\tName\n"
        + "30016159\t509056\t410727\tTrainer\tMW\tCallum Clarke\n\n30016160\t509057\t\t\tMW\tJo  Smith\r\n");
    RichTest.eq(r.toString(), "[Callum Clarke|30016159|509056|410727, Jo Smith|30016160|509057|]", "tab headings");
    r = CrewNumbers.parse("Name | Employee # | BP # | DISN #\nCallum Clarke | 30016159 | 509056 | 410727\n | 1 | 2 | 3");
    RichTest.eq(r.toString(), "[Callum Clarke|30016159|509056|410727]", "| headings, nameless row skipped");
    r = CrewNumbers.parse("Callum Clarke, 30016159, 509056, 410727\nMW  Bo Dee  411-22\nZed 7");
    RichTest.eq(r.toString(), "[Callum Clarke|30016159|509056|410727, Bo Dee|411-22||]", "no headings (\"Zed 7\" is one cell with a digit: no name)");
    // Apply
    Models.Project p = new Models.Project(); p.crew.add("callum clarke"); p.crew.add("Jo O'Neil-Smith"); p.employeeNumbers.put("callum clarke", "old");
    p.bpNumbers.put("callum clarke", "keep");
    java.util.List<CrewNumbers.Row> rows = java.util.Arrays.asList(new CrewNumbers.Row("Callum  Clarke", "30016159", "", "410727"),
        new CrewNumbers.Row("jo  oneil.smith", "5", "6", "7"), new CrewNumbers.Row("Ann Lee", "9", "", ""));
    CrewNumbers.Applied done = CrewNumbers.apply(p, rows);
    NoteTest.check(done.updated.equals(java.util.Arrays.asList("callum clarke", "Jo O'Neil-Smith")) && done.missing.size() == 1
        && p.employeeNumberOf("callum clarke").equals("30016159") && p.bpOf("callum clarke").equals("keep") && p.disnOf("callum clarke").equals("410727")
        && p.disnOf("Jo O'Neil-Smith").equals("7"), "apply: matched ignoring case, spaces, punctuation; empty keeps");
    RichTest.eq(CrewNumbers.summary(done), "Numbers added for 2 people.\n\nNot on this project's crew: Ann Lee.", "summary");
    CrewNumbers.Applied one = new CrewNumbers.Applied(); one.updated.add("x");
    RichTest.eq(CrewNumbers.summary(one), "Numbers added for 1 person.", "summary singular");
    CrewNumbers.addMissing(p, done.missing);
    NoteTest.check(p.crew.contains("Ann Lee") && p.employeeNumberOf("Ann Lee").equals("9") && p.roleOf("Ann Lee").equals(p.groups.get(0).id), "add the missing to the crew");
    RichTest.eq(p.numbersLine("callum clarke"), "Employee # 30016159 · BP # keep · DISN # 410727", "numbers line");
    // JSON and rename
    Models.Project q = Models.Project.fromJson(p.toJson());
    NoteTest.check(q.bpOf("callum clarke").equals("keep") && q.disnOf("Jo O'Neil-Smith").equals("7"), "BP / DISN round trip");
    org.json.JSONObject j = p.toJson(); j.getJSONObject("bpNumbers").put("Zed", "  ");
    NoteTest.check(!Models.Project.fromJson(j).bpNumbers.containsKey("Zed"), "blank dropped on load");
    p.renamePerson("callum clarke", "Callum Clarke");
    NoteTest.check(p.bpOf("Callum Clarke").equals("keep") && p.disnOf("Callum Clarke").equals("410727") && p.bpOf("callum clarke").isEmpty(), "rename moves BP / DISN");
    // Auto-fill and saving back
    Store s = new Store();
    Models.ReportForm f = new Models.ReportForm();
    Models.FilledReport fr = new Models.FilledReport(); fr.person = "Callum Clarke";
    RichTest.eq(ReportFill.suggest(s, p, f, fr, new Models.FormQuestion("a", "BP #", "text"), java.util.Collections.emptyList(), 0), "keep", "BP # auto-fill");
    RichTest.eq(ReportFill.suggest(s, p, f, fr, new Models.FormQuestion("b", "DISN", "text"), java.util.Collections.emptyList(), 0), "410727", "DISN auto-fill");
    RichTest.eq(ReportFill.suggest(s, p, f, fr, new Models.FormQuestion("c", "Aecon PRNNR #", "text"), java.util.Collections.emptyList(), 0), "30016159", "PRNNR is the employee number");
    Models.FilledReport ann = new Models.FilledReport(); ann.person = "Ann Lee";
    ReportFill.setAnswer(p, new Models.FormQuestion("a", "BP #", "text"), ann, "B1", 1);
    ReportFill.setAnswer(p, new Models.FormQuestion("b", "DISN #", "text"), ann, "D1", 1);
    NoteTest.check(p.bpOf("Ann Lee").equals("B1") && p.disnOf("Ann Lee").equals("D1"), "BP / DISN saved back");
    System.out.println("CREWNUMBERS PASS"); } }
class FormPdfTest {
  public static void main(String[] a) throws Exception {
    Models.ReportForm f = FormsTest.sampleForm();
    Models.FilledReport r = new Models.FilledReport(); r.person = "Al"; r.project = "Warehouse"; r.formName = f.name;
    r.answers.put("q1", "Nov 13, 2026"); r.answers.put("q9", "Excellent"); r.answers.put("q20", "Good progress this term.");
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    try {
      FormPdf.write(f, r, 0L, null, out);
      NoteTest.check(out.size() > 0, "PDF built");
    } catch (UnsatisfiedLinkError | RuntimeException e) {
      // android.graphics.pdf.PdfDocument needs the phone's native code; on the JVM it can't run.
      System.out.println("skipped: PdfDocument isn't available on the JVM (" + e.getClass().getSimpleName() + ")");
    }
    System.out.println("FORMPDF PASS"); } }
class PlacementTest {
  static void near(double got, double want, String name) {
    if (Math.abs(got - want) > 0.002) System.out.println("got " + got + " want " + want);
    NoteTest.check(Math.abs(got - want) <= 0.002, name);
  }
  public static void main(String[] a) throws Exception {
    java.util.List<FormReader.Line> lines = new java.util.ArrayList<>();
    lines.add(new FormReader.Line("NAME:", 0, 0.107, 0.274, 0.165, 0.287));
    lines.add(new FormReader.Line("A. WORK DONE IN THE ABOVE PERIOD:", 0, 0.107, 0.336, 0.497, 0.349));
    lines.add(new FormReader.Line("B. 1. Improvement shown in above period", 0, 0.143, 0.403, 0.487, 0.416));
    lines.add(new FormReader.Line("3. Progress in learning this phase of work", 0, 0.143, 0.430, 0.506, 0.443));
    lines.add(new FormReader.Line("Excellent 4.0", 0, 0.6, 0.5, 0.7, 0.513));
    lines.add(new FormReader.Line("Poor 0.0", 0, 0.6, 0.52, 0.7, 0.533));
    lines.add(new FormReader.Line("Overall average of above:", 0, 0.143, 0.6, 0.4, 0.613));
    lines.add(new FormReader.Line("(total #1 - #10 and divide by 10)", 0, 0.143, 0.62, 0.4, 0.633));
    lines.add(new FormReader.Line("NAME2: ____ DATE: ____", 1, 0.1, 0.1, 0.9, 0.12));
    lines.add(new FormReader.Line("Comments"));  // no box (e.g. from Word)
    FormReader.Result r = FormReader.readLines(lines);
    RichTest.eq(FormsTest.qs(r), "Name(text) | Work done in the above period(long) | Improvement shown in above period(rating) | "
        + "Progress in learning this phase of work(rating) | Overall average(average) | Name2(text) | Date(date)", "questions");
    Models.Place name = r.questions.get(0).place;
    near(name.x, 0.177, "Name x"); near(name.y, 0.287, "Name y"); NoteTest.check(name.page == 0, "Name page");
    near(r.questions.get(1).place.x, 0.107, "Work done x"); near(r.questions.get(1).place.y, 0.349 + 1.1 * 0.013, "Work done y");
    near(r.questions.get(2).place.x, 0.566, "rating x (widest + 0.06)"); near(r.questions.get(2).place.y, 0.416, "rating y");
    near(r.questions.get(3).place.x, 0.566, "rating x same column");
    near(r.questions.get(4).place.x, 0.566, "average in the rating column (right of (total...) is further left)"); near(r.questions.get(4).place.y, 0.613 + 1.2 * 0.013, "average under, (total...) follows");
    // Two on one line: label ends estimated from character positions
    Models.Place n2 = r.questions.get(5).place, d = r.questions.get(6).place;
    String norm = FormReader.normalize("NAME2: ____ DATE: ____");
    near(n2.x, 0.1 + 0.8 * (norm.indexOf(':') + 1) / norm.length() + 0.012, "first label end");
    near(d.x, 0.1 + 0.8 * (norm.indexOf("DATE:") + 5) / (double) norm.length() + 0.012, "second label end");
    NoteTest.check(n2.page == 1 && Math.abs(d.y - 0.12) < 1e-9, "page 2, baseline at the bottom");
    // Average right of a wide "(total ...)" line
    java.util.List<FormReader.Line> wide = new java.util.ArrayList<>();
    wide.add(new FormReader.Line("1. Attitude", 0, 0.1, 0.1, 0.3, 0.11));
    wide.add(new FormReader.Line("Excellent 4.0", 0, 0.1, 0.2, 0.2, 0.21));
    wide.add(new FormReader.Line("Poor 0.0", 0, 0.1, 0.22, 0.2, 0.23));
    wide.add(new FormReader.Line("Overall average of above:", 0, 0.1, 0.3, 0.35, 0.31));
    wide.add(new FormReader.Line("(total #1 - #10 and divide by 10)", 0, 0.1, 0.32, 0.6, 0.33));
    FormReader.Result w = FormReader.readLines(wide);
    near(w.questions.get(1).place.x, 0.615, "average right of the (total ...) line");
    java.util.List<FormReader.Line> noParen = new java.util.ArrayList<>(wide.subList(0, 4));
    near(FormReader.readLines(noParen).questions.get(1).place.x, 0.365, "average right of its own line, past the rating column");
    near(FormReader.readLines(noParen).questions.get(1).place.y, 0.31, "average on its line when no (total ...)");
    near(r.textSize, 0.013 * 1.15, "text size from the median line height (x 1.15)");
    NoteTest.check(FormReader.read(java.util.Arrays.asList("NAME:")).questions.get(0).place == null
        && FormReader.read(java.util.Arrays.asList("NAME:")).textSize == 0.014, "no boxes: no places, default size");
    // JSON: place and fontScale
    Models.ReportForm f = new Models.ReportForm(); f.questions = r.questions; f.fontScale = 1.3; f.textSize = r.textSize;
    Models.ReportForm g = Models.ReportForm.fromJson(new org.json.JSONObject(f.toJson().toString()));
    NoteTest.check(Math.abs(g.fontScale - 1.3) < 1e-9 && Math.abs(g.questions.get(0).place.x - name.x) < 1e-9 && g.questions.get(5).place.page == 1
        && Math.abs(g.textSize - r.textSize) < 1e-9, "place / fontScale round trip");
    org.json.JSONObject q = f.toJson().getJSONArray("questions").getJSONObject(0);
    org.json.JSONObject plain = new Models.FormQuestion("q9", "X", "text").toJson();
    NoteTest.check(q.has("place") && !plain.has("place"), "place omitted when none");
    org.json.JSONObject old = f.toJson(); old.remove("fontScale"); old.put("textSize", 5);
    Models.ReportForm o = Models.ReportForm.fromJson(old);
    NoteTest.check(o.fontScale == 1.0 && o.textSize == 0.03, "old form: fontScale 1.0, textSize clamped");
    System.out.println("PLACEMENT PASS"); } }
class FormCopyTest {
  public static void main(String[] a) throws Exception {
    java.io.File home = java.nio.file.Files.createTempDirectory("dwl-copy").toFile();
    Store s = new Store(home);
    Models.ReportForm f = FormsTest.sampleForm(); f.files.add("doc-a.jpg"); f.files.add("doc-b.png");
    for (String n : f.files) java.nio.file.Files.write(s.photoFile(n).toPath(), new byte[]{1});
    s.reportForms.add(f);
    // A report started on the form keeps a copy
    Models.FilledReport r = new Models.FilledReport(); r.formId = f.id; r.form = f.copy(); r.answers.put("q2", "Al");
    s.filledReports.add(r);
    NoteTest.check(s.formFor(r) == f, "live form used while it exists");
    Models.FilledReport back = Models.FilledReport.fromJson(new org.json.JSONObject(r.toJson().toString()));
    NoteTest.check(back.form != null && back.form.questions.size() == 20 && back.form.id.equals(f.id), "report keeps its copy in JSON");
    NoteTest.check(!new Models.FilledReport().toJson().has("form"), "no copy: no key");
    // Delete the form: the report uses its copy, and the files stay because the copy uses them
    s.removeForm(f);
    NoteTest.check(s.form(f.id) == null && s.formFor(r) == r.form && s.formFor(r).question("q2").label.equals("Name"), "copy used when the form is deleted");
    NoteTest.check(s.photoFile("doc-a.jpg").exists() && s.photoFile("doc-b.png").exists() && s.fileInUse("doc-a.jpg"), "files kept while a copy uses them");
    // An old report with no copy gets one when its form goes
    Models.ReportForm g = new Models.ReportForm(); g.files.add("doc-c.jpg"); java.nio.file.Files.write(s.photoFile("doc-c.jpg").toPath(), new byte[]{1});
    s.reportForms.add(g);
    Models.FilledReport old = new Models.FilledReport(); old.formId = g.id; s.filledReports.add(old);
    s.removeForm(g);
    NoteTest.check(old.form != null && s.formFor(old) != null && s.photoFile("doc-c.jpg").exists(), "old report gets a copy before its form goes");
    // Files go once nothing uses them
    s.filledReports.clear();
    Models.ReportForm h = new Models.ReportForm(); h.files.add("doc-a.jpg"); s.reportForms.add(h);
    s.removeForm(h);
    NoteTest.check(!s.photoFile("doc-a.jpg").exists(), "files deleted when no report copy uses them");
    // pictures(): photos and PDF pages, not the source file
    Models.ReportForm p = new Models.ReportForm(); p.files.add("doc-1.pdf"); p.files.add("doc-2.png"); p.files.add("doc-3.png");
    RichTest.eq(p.pictures(), java.util.Arrays.asList("doc-2.png", "doc-3.png"), "page pictures");
    // What's written on the form
    Models.ReportForm sf = FormsTest.sampleForm();
    Models.FilledReport fr = new Models.FilledReport(); fr.answers.put("q9", "Excellent"); fr.answers.put("q10", "Average"); fr.answers.put("q1", "Nov 13, 2026");
    RichTest.eq(FormPdf.written(sf, fr, sf.question("q9")), "4.0", "rating writes its value");
    RichTest.eq(FormPdf.written(sf, fr, sf.question("q19")), "3.0", "average worked out");
    RichTest.eq(FormPdf.written(sf, fr, sf.question("q1")), "Nov 13, 2026", "text as typed");
    Models.FormQuestion noValue = new Models.FormQuestion("x", "Q", "rating"); noValue.choices.add(new Models.Choice("Good", ""));
    fr.answers.put("x", "Good");
    RichTest.eq(FormPdf.written(sf, fr, noValue), "Good", "rating with no value writes its label");
    // PDF with pictures (needs the phone's PDF code; skipped on the JVM)
    sf.files.add("doc-z.png");
    sf.question("q1").place = new Models.Place(0, 0.3, 0.2);
    try {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      FormPdf.write(sf, fr, 0L, s.photoDir(), out);
      NoteTest.check(out.size() > 0, "PDF with pictures built");
    } catch (UnsatisfiedLinkError | RuntimeException e) {
      System.out.println("skipped: PdfDocument isn't available on the JVM (" + e.getClass().getSimpleName() + ")");
    }
    System.out.println("FORMCOPY PASS");
    System.exit(0);
  } }
class FormLinesTest {
  static void rect(int[] lum, int w, int x1, int y1, int x2, int y2) {
    for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) lum[y * w + x] = FormLines.luminance(0x141414);
  }
  static void near(double got, double want, double tol, String name) {
    if (Math.abs(got - want) > tol) System.out.println("got " + got + " want " + want);
    NoteTest.check(Math.abs(got - want) <= tol, name);
  }
  public static void main(String[] a) throws Exception {
    // A white 1000 x 1400 page with: a line right of "NAME:", a heading's underline, 3 ruled lines, and letters.
    int w = 1000, h = 1400;
    int[] lum = new int[w * h];
    java.util.Arrays.fill(lum, FormLines.luminance(0xFAFAFA));
    rect(lum, w, 220, 300, 550, 301);  // NAME blank
    rect(lum, w, 600, 420, 700, 421);  // underline under the heading words
    for (int y : new int[]{520, 560, 600}) rect(lum, w, 150, y, 900, y + 1);  // comment lines
    for (int x = 100; x < 400; x += 14) rect(lum, w, x, 700, x + 9, 716);  // "text": short dark blocks
    java.util.List<FormLines.Segment> segs = FormLines.find(lum, w, h, 0);
    NoteTest.check(segs.size() == 5, "5 segments " + segs);
    near(segs.get(0).x0, 0.22, 0.005, "first x0"); near(segs.get(0).x1, 0.551, 0.005, "first x1"); near(segs.get(0).y * 1400, 300.5, 1.5, "first y");
    java.util.List<FormReader.Line> lines = java.util.Arrays.asList(
        new FormReader.Line("NAME:", 0, 100 / 1000.0, 280 / 1400.0, 160 / 1000.0, 298 / 1400.0),
        new FormReader.Line("Point System", 0, 600 / 1000.0, 400 / 1400.0, 700 / 1000.0, 418 / 1400.0),
        new FormReader.Line("C. Your overall comment on the apprentice:", 0, 100 / 1000.0, 480 / 1400.0, 480 / 1000.0, 498 / 1400.0));
    FormReader.Result r = FormReader.readLines(lines, segs);
    Models.Place name = null, comment = null;
    for (Models.FormQuestion q : r.questions) {
      if (q.label.equals("Name")) name = q.place;
      if (q.kind.equals("long")) comment = q.place;
    }
    near(name.x, 0.226, 0.002, "Name starts where its line starts");
    near(name.w, 0.551 - 0.22 - 0.012, 0.004, "Name width = the line's length");
    near(comment.x, 0.156, 0.002, "comment x");
    NoteTest.check(comment.rows.size() == 2, "comment carries on to the next two ruled lines");
    near(comment.y, 520.5 / 1400 - 0.0045, 0.002, "comment y");
    // Fitting: 10 units per character at size 1.
    java.util.function.ToDoubleFunction<String> at1 = t -> t.length() * 10.0;
    NoteTest.check(Fit.oneLine("Tyson Zimmer", 2000, 1, at1) == 1, "fits");
    NoteTest.check(Fit.oneLine("Aecon Industrial Western", 120, 1, at1) == 0.5, "shrinks, not below half");
    Fit.Rows fit = Fit.rows("one two three four five six seven eight nine ten", 200, 2, 1, at1);
    NoteTest.check(fit.lines.size() <= 3 && fit.size < 1, "rows: smaller to fit");
    RichTest.eq(Fit.wrap("a b c", 30, t -> t.length() * 10.0), java.util.Arrays.asList("a b", "c"), "wrap");
    RichTest.eq(Fit.wrap("abcdefgh", 30, t -> t.length() * 10.0), java.util.Arrays.asList("abc", "def", "gh"), "long word split");
    // Moving keeps the line length and moves the following lines too.
    Models.Place moved = comment.moved(0.01, 0.02);
    NoteTest.check(moved.w == comment.w && Math.abs(moved.rows.get(0) - (comment.rows.get(0) + 0.02)) < 1e-9, "moved");
    Models.Place back = Models.Place.fromJson(comment.toJson());
    NoteTest.check(back.rows.size() == 2 && Math.abs(back.w - comment.w) < 1e-9, "w / rows round trip");
    org.json.JSONObject plain = new Models.Place(0, 0.5, 0.5).toJson();
    NoteTest.check(!plain.has("w") && !plain.has("rows"), "w / rows omitted when none");
    near(new Models.Place(0, 0.5, 0.5).width(), 0.46, 1e-9, "no line: to the page's edge");
    // Without segments: the section 8 placement, w = 0
    FormReader.Result none = FormReader.readLines(lines);
    NoteTest.check(none.questions.get(0).place.w == 0 && none.questions.get(0).place.rows.isEmpty(), "no lines found: old placement");
    System.out.println("FORMLINES PASS"); } }
class TermTest {
  public static void main(String[] a) throws Exception {
    Models.Project p = new Models.Project(); p.crew.add("Al"); p.levels.put("Al", "Apprentice"); p.trades.put("Al", "Millwright");
    p.unionLocals.put("Al", "Local 1687"); p.terms.put("Al", "2nd term");
    RichTest.eq(p.personLine("Al"), "Millwright \u00B7 Apprentice \u00B7 2nd term \u00B7 Local 1687", "trade line with the term");
    org.json.JSONObject j = p.toJson(); j.getJSONObject("terms").put("Bo", "5th term").put("Cy", " ");
    Models.Project q = Models.Project.fromJson(j);
    NoteTest.check(q.termOf("Al").equals("2nd term") && !q.terms.containsKey("Bo") && !q.terms.containsKey("Cy"), "terms round trip, others dropped");
    p.renamePerson("Al", "Alan");
    NoteTest.check(p.termOf("Alan").equals("2nd term") && p.termOf("Al").isEmpty(), "rename moves the term");
    NoteTest.check(CrewDialog.showsTerm("Apprentice") && !CrewDialog.showsTerm("Journeyman") && !CrewDialog.showsTerm(""), "Term shows only for Apprentice");
    CrewDialog.Person x = CrewDialog.Person.of(p, "Alan");
    NoteTest.check(x.term.equals("2nd term"), "person box starts with the term");
    x.level = "Journeyman"; x.saveTo(p, "Alan");
    NoteTest.check(p.termOf("Alan").isEmpty(), "not Apprentice: term removed");
    x.level = "Apprentice"; x.term = "4th term"; x.saveTo(p, "Alan");
    NoteTest.check(p.termOf("Alan").equals("4th term"), "apprentice: term saved");
    x.term = ""; x.saveTo(p, "Alan");
    NoteTest.check(!p.terms.containsKey("Alan"), "None removes it");
    System.out.println("TERM PASS"); } }
class SafeguardsTest {
  static FormReader.Line l(String t, double x0, double y0, double x1, double y1) { return new FormReader.Line(t, 0, x0, y0, x1, y1); }
  static FormLines.Segment seg(double x0, double x1, double y) { return new FormLines.Segment(0, x0, x1, y); }
  public static void main(String[] a) throws Exception {
    FormReader.Result r = FormReader.readLines(java.util.Arrays.asList(
        l("Employee name:", 0.10, 0.100, 0.25, 0.110),
        l("Date:", 0.60, 0.100, 0.66, 0.110),  // a second label on the same line as the name's blank
        l("Details of the incident:", 0.10, 0.200, 0.40, 0.210),
        l("Supervisor signature", 0.15, 0.300, 0.40, 0.310),  // under the line at 0.296
        l("Position:", 0.10, 0.400, 0.20, 0.410)),
      java.util.Arrays.asList(seg(0.26, 0.90, 0.111), seg(0.10, 0.90, 0.225), seg(0.10, 0.90, 0.240), seg(0.10, 0.90, 0.255),
        seg(0.10, 0.45, 0.296), seg(0.21, 0.60, 0.411)));
    java.util.Map<String, Models.FormQuestion> q = new java.util.HashMap<>();
    for (Models.FormQuestion x : r.questions) q.put(x.label, x);
    Models.Place name = q.get("Employee name").place, inc = q.get("Details of the incident").place;
    FormLinesTest.near(name.x, 0.266, 0.001, "name starts on its line");
    NoteTest.check(name.x + name.w < 0.60, "name stops before \"Date:\" (" + (name.x + name.w) + ")");
    NoteTest.check(inc.rows.size() == 2, "incident uses its 3 ruled lines, not the signature line");
    FormLinesTest.near(inc.rowFit(), 0.015 * 0.66, 0.0005, "rowFit (average gap x 0.66)");
    NoteTest.check(new Models.Place(0, 0.1, 0.1).rowFit() < 0, "no rows: no rowFit");
    // A job title from Manage Crew
    Models.ReportForm form = new Models.ReportForm(); form.questions = r.questions;
    Models.Project p = new Models.Project(); p.crew.add("Al"); p.trades.put("Al", "Millwright"); p.levels.put("Al", "Apprentice");
    Store s = new Store(); s.userName = "Me";
    Models.FilledReport rep = new Models.FilledReport(); rep.person = "Al";
    ReportFill.autoFill(s, p, form, rep, java.util.Collections.emptyList(), 0);
    RichTest.eq(rep.answer(q.get("Position").id), "Millwright Apprentice", "Position");
    RichTest.eq(rep.answer(q.get("Employee name").id), "Al", "Employee name");
    p.trades.remove("Al");
    RichTest.eq(ReportFill.suggest(s, p, form, rep, new Models.FormQuestion("z", "Job title", "text"), java.util.Collections.emptyList(), 0), "Apprentice", "job title: just Apprentice");
    p.levels.remove("Al");
    RichTest.eq(ReportFill.suggest(s, p, form, rep, new Models.FormQuestion("z", "Occupation", "text"), java.util.Collections.emptyList(), 0), "", "neither: empty");
    s.workPosition = "Foreman";
    RichTest.eq(ReportFill.suggest(s, p, form, rep, new Models.FormQuestion("z", "Position of supervisor", "text"), java.util.Collections.emptyList(), 0), "Foreman", "me rule still wins");
    System.out.println("SAFEGUARDS PASS"); } }
class RowsAndDatesTest {
  static FormReader.Line l(String t, double x0, double y0, double x1, double y1) { return new FormReader.Line(t, 0, x0, y0, x1, y1); }
  public static void main(String[] a) throws Exception {
    // Item numbers and points read as their own column are joined back to their words.
    FormReader.Result r = FormReader.readLines(java.util.Arrays.asList(
        l("1.", 0.14, 0.400, 0.16, 0.410), l("2.", 0.14, 0.414, 0.16, 0.424), l("10.", 0.13, 0.428, 0.16, 0.438),
        l("Improvement shown in above period", 0.19, 0.400, 0.49, 0.410),
        l("Understanding of work", 0.19, 0.414, 0.37, 0.424),
        l("Mechanical skills", 0.19, 0.428, 0.32, 0.438),
        l("Excellent", 0.66, 0.40, 0.74, 0.41), l("4.0", 0.85, 0.40, 0.88, 0.41),
        l("Average", 0.66, 0.414, 0.73, 0.424), l("2.0", 0.85, 0.414, 0.88, 0.424),
        l("Poor", 0.66, 0.428, 0.70, 0.438), l("0.0", 0.85, 0.428, 0.88, 0.438),
        l("Overall average of above:", 0.32, 0.45, 0.53, 0.46)));
    java.util.List<String> got = new java.util.ArrayList<>();
    for (Models.FormQuestion q : r.questions) got.add(q.label + "/" + q.kind);
    RichTest.eq(got, java.util.Arrays.asList("Improvement shown in above period/rating", "Understanding of work/rating",
        "Mechanical skills/rating", "Overall average/average"), "numbers column joined: ratings found");
    java.util.List<String> legend = new java.util.ArrayList<>();
    for (Models.Choice c : r.questions.get(0).choices) legend.add(c.label + " " + c.value);
    RichTest.eq(legend, java.util.Arrays.asList("Excellent 4.0", "Average 2.0", "Poor 0.0"), "points joined to their words");
    // Dates: the period is one pick, dates read back, only a plain Date is today.
    Models.ReportForm f = new Models.ReportForm();
    f.questions = FormReader.read(java.util.Arrays.asList("DATE:", "PERIOD COVERED FROM:", "TO:", "Date of incident:", "Update:")).questions;
    got.clear();
    for (Models.FormQuestion q : f.questions) got.add(q.label + "/" + q.kind);
    RichTest.eq(got, java.util.Arrays.asList("Date/date", "Period covered from/date", "Period covered to/date", "Date of incident/date", "Update/text"), "date kinds");
    RichTest.eq(ReportFill.rangeEnd(f, f.questions.get(1)).label, "Period covered to", "range end");
    NoteTest.check(ReportFill.rangeEnd(f, f.questions.get(0)) == null && ReportFill.rangeStart(f, f.questions.get(2)) == f.questions.get(1), "range pairing");
    java.util.Calendar d = ReportFill.parseDate("Nov 3, 2026");
    NoteTest.check(d != null && d.get(java.util.Calendar.YEAR) == 2026 && d.get(java.util.Calendar.MONTH) == 10 && d.get(java.util.Calendar.DAY_OF_MONTH) == 3, "parseDate");
    NoteTest.check(ReportFill.parseDate("soon") == null, "parseDate: not a date");
    java.util.Calendar today = java.util.Calendar.getInstance(); today.clear(); today.set(2026, 10, 13, 9, 0);
    Models.FilledReport rep = new Models.FilledReport();
    ReportFill.autoFill(new Store(), new Models.Project(), f, rep, java.util.Collections.emptyList(), today.getTimeInMillis());
    RichTest.eq(rep.answers.toString(), "{q1=Nov 13, 2026}", "only a plain Date is today");
    got.clear();
    for (Models.FormQuestion q : ReportFill.missing(f, rep)) got.add(q.label);
    RichTest.eq(got, java.util.Arrays.asList("Period covered from", "Period covered to", "Date of incident", "Update"), "missing");
    // The average is never asked.
    Models.ReportForm sf = FormsTest.sampleForm();
    for (Models.FormQuestion q : ReportFill.missing(sf, new Models.FilledReport())) NoteTest.check(!q.kind.equals("average"), "average not asked: " + q.label);
    // The calendar's dates
    RichTest.eq(DatePick.format(DatePick.utc("Aug 1, 2026")), "Aug 1, 2026", "calendar date round trip");
    RichTest.eq(DatePick.periodLabel("Period covered from"), "Period covered", "period label");
    // rowFit: the average gap x 0.66
    Models.Place p = new Models.Place(0, 0.1, 0.20, 0.5, java.util.Arrays.asList(0.22, 0.26));
    FormLinesTest.near(p.rowFit(), 0.03 * 0.66, 1e-9, "rowFit uses the average gap");
    System.out.println("ROWSDATES PASS"); } }
