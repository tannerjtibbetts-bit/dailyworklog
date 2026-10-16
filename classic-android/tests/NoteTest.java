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
  System.out.println("OT PASS"); } }
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
  Models.SeaCanMove m = new Models.SeaCanMove(); m.can = "BPPU 201006-1"; m.fromLetter = "D"; m.fromRow = "33"; m.fromLevel = "3"; m.toLetter = "E"; m.toRow = "34"; m.toLevel = "2";
  Models.SeaCanMove q = new Models.SeaCanMove(); q.can = "Odd, \"one\""; q.fromRow = "3";
  p.seaCans.add(m); p.seaCans.add(new Models.SeaCanMove()); p.seaCans.add(q);
  p.seaCanInclude = true;
  NoteTest.check(Models.Project.fromJson(p.toJson()).seaCanInclude, "include tick saved");
  NoteTest.check(Models.Project.fromJson(p.toJson()).seaCans.get(0).fromLetter.equals("D"), "letters saved");
  NoteTest.check(m.fromLocation().equals("D33 L3") && m.toLocation().equals("E34 L2") && q.fromLocation().equals("3") && q.toLocation().isEmpty(), "locations");
  NoteTest.check(Models.SeaCanMove.location("", "12", "2").equals("12 L2") && Models.SeaCanMove.location("", "", "3").equals("L3") && Models.SeaCanMove.location("E", "", "").equals("E"), "location text");
  NoteTest.check(p.filledSeaCans().size() == 2, "only numbered sea cans exported");
  java.io.ByteArrayOutputStream csv = new java.io.ByteArrayOutputStream();
  SeaCanExport.writeCsv(p, csv);
  String c = csv.toString("UTF-8");
  NoteTest.check(c.equals("\uFEFFMoves,Can #,From Location,To Location,Weight,Bin #,From Location,To Location\r\n1,BPPU 201006-1,D33 L3,E34 L2,,,,\r\n2,\"Odd, \"\"one\"\"\",3,,,,,\r\n"), "csv " + c);
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
