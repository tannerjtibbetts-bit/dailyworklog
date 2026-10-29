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
  NoteTest.check(String.join(",", Models.Attendance.STATUSES).equals("Yes,No,AWOL,Vacation,Off,Training,D72,Step up,Late,Left early"), "statuses");
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
  NoteTest.check(newOnly > 0 && list.size() > 30, "backup topics hidden in the classic app");
  java.util.List<Object[]> hits = ManualActivity.search(list, "sea can");
  NoteTest.check(((org.json.JSONObject) hits.get(0)[0]).getString("id").equals("sea-can-movements"), "search sea can");
  NoteTest.check(ManualActivity.search(list, "zzzz").isEmpty(), "no match");
  NoteTest.check(ManualActivity.search(list, "backup restore").isEmpty() || !((org.json.JSONObject) ManualActivity.search(list, "backup restore").get(0)[0]).optBoolean("newAppOnly"), "no new-app topics");
  System.out.println("MANUAL PASS"); } }
class HiddenTabsTest { public static void main(String[] a) throws Exception {
  NoteTest.check(LogsActivity.TAB_IDS.length == LogsActivity.TAB_NAMES.length && java.util.Arrays.asList(LogsActivity.TAB_IDS).contains("seacans"), "tab ids");
  NoteTest.check(String.join(",", LogsActivity.TAB_IDS).equals("notify,links,overtime,attendance,notes,seacans")
      && LogsActivity.TAB_NAMES[3].equals("Attendance"), "attendance after overtime");
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
  NoteTest.check(m.get("xl/styles.xml").contains("cellXfs count=\"11\""), "11 styles");
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
  s = parts(p, l).get("xl/worksheets/sheet1.xml");
  System.out.println(s.substring(s.indexOf("<row r=\"15\"")));
  NoteTest.check(s.contains("<c r=\"A15\" t=\"inlineStr\" s=\"8\">" + t("Weekend overtime")) && s.contains("<c r=\"E16\" t=\"inlineStr\" s=\"4\">" + t("Hours"))
      && s.contains("<c r=\"B17\" t=\"inlineStr\" s=\"6\">" + t("Yes")) && s.contains("<c r=\"C17\" t=\"inlineStr\" s=\"7\">" + t("No")) && s.contains("<c r=\"D17\" s=\"9\"/>")
      && s.contains("<c r=\"E17\" s=\"9\"><v>8</v></c>") && s.contains("<c r=\"A18\" t=\"inlineStr\" s=\"1\">" + t("Yes total")) && s.contains("<c r=\"B18\" s=\"9\"><v>1</v></c>")
      && s.contains("<c r=\"A19\" t=\"inlineStr\">" + t("Notes: Bring harness")), "overtime section");
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
