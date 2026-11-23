// Daily Work Log: app overview deck (structured: theme, layouts, sections).
const path = require("path");
const pptxgen = require("pptxgenjs");
const React = require("react");
const ReactDOMServer = require("react-dom/server");
const sharp = require("sharp");
const Md = require("react-icons/md");
const { applyTheme } = require("/root/.claude/skills/synced/3ef43209-a201-4b07-a645-30d0961e0161_425f44b6-f943-422f-a65e-0a57e3ccca4e/pptx/scripts/apply_theme.js");

const IMG = path.join(__dirname, "..", "deck");
const OUT = path.join(__dirname, "..", "deck", "Daily Work Log - App Overview.pptx");
const img = (f) => path.join(IMG, f);

// Colours taken from the app itself: night navy, safety yellow, the app's blues, done-green, delay-red.
const THEME = {
  name: "Daily Work Log",
  headFontFace: "Arial",
  bodyFontFace: "Calibri",
  colors: {
    dk1: "0F1A2B", lt1: "FFFFFF", dk2: "174A8B", lt2: "EEF4FB",
    accent1: "F2C200", accent2: "1268D6", accent3: "1DB04B", accent4: "E5484D", accent5: "A9C8EA", accent6: "46566C",
    hlink: "1268D6", folHlink: "174A8B",
  },
};
const HEX = THEME.colors;

async function icon(Comp, color, size = 256) {
  const svg = ReactDOMServer.renderToStaticMarkup(React.createElement(Comp, { color: "#" + color, size: String(size) }));
  const buf = await sharp(Buffer.from(svg)).png().toBuffer();
  return "image/png;base64," + buf.toString("base64");
}

(async () => {
  const pres = new pptxgen();
  pres.layout = "LAYOUT_WIDE"; // 13.33 x 7.5
  pres.author = "Tanner Tibbetts";
  pres.company = "Daily Work Log";
  pres.title = "Daily Work Log: how the app works";
  pres.theme = { headFontFace: THEME.headFontFace, bodyFontFace: THEME.bodyFontFace };
  const C = pres.SchemeColor;
  const W = 13.333;

  // ----- layouts -----
  pres.defineSlideMaster({
    title: "Title dark",
    background: { color: C.text1 },
    objects: [
      { placeholder: { options: { name: "title", type: "title", x: 0.7, y: 2.0, w: 7.4, h: 1.6, fontSize: 54, bold: true, color: C.background1, valign: "bottom", align: "left", margin: 0 }, text: "" } },
      { placeholder: { options: { name: "body", type: "body", x: 0.7, y: 3.75, w: 7.2, h: 1.0, fontSize: 24, color: C.accent1, valign: "top", align: "left", margin: 0 }, text: "" } },
    ],
  });
  pres.defineSlideMaster({
    title: "Section",
    background: { color: C.text1 },
    objects: [
      { placeholder: { options: { name: "title", type: "title", x: 0.9, y: 2.7, w: 11.5, h: 1.2, fontSize: 44, bold: true, color: C.background1, valign: "bottom", align: "left", margin: 0 }, text: "" } },
      { placeholder: { options: { name: "body", type: "body", x: 0.9, y: 4.0, w: 11.5, h: 0.8, fontSize: 20, color: C.accent5, valign: "top", align: "left", margin: 0 }, text: "" } },
    ],
  });
  pres.defineSlideMaster({
    title: "Content",
    background: { color: C.background1 },
    margin: [0.5, 0.6, 0.6, 0.6],
    objects: [
      { placeholder: { options: { name: "title", type: "title", x: 0.6, y: 0.35, w: 12.1, h: 0.85, fontSize: 34, bold: true, color: C.text1, valign: "middle", margin: 0 }, text: "" } },
      { text: { text: "Daily Work Log  ·  How the app works", options: { x: 0.6, y: 7.05, w: 6, h: 0.3, fontSize: 10, color: C.accent6, margin: 0 } } },
    ],
    slideNumber: { x: 12.2, y: 7.05, w: 0.5, h: 0.3, fontSize: 10, color: C.accent6, align: "right" },
  });

  // ----- helpers -----
  const PH = 720 / 1560; // manual screenshots are phone-shaped
  let n = 0;
  const nm = (s) => `${s}-${++n}`;
  // A phone screenshot in a dark rounded frame (the deck's motif).
  function phone(slide, file, x, y, h, caption) {
    const w = h * PH;
    const pad = 0.07;
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: x - pad, y: y - pad, w: w + 2 * pad, h: h + 2 * pad, rectRadius: 0.18,
      fill: { color: C.text1 }, line: { color: C.text1, width: 0 },
      shadow: { type: "outer", color: "000000", opacity: 0.25, blur: 8, offset: 3, angle: 90 },
      objectName: nm("phone frame"),
    });
    slide.addImage({ path: img(file), x, y, w, h, altText: caption || file, objectName: nm("screenshot") });
    if (caption) {
      slide.addText(caption, { x: x - 0.3, y: y + h + 0.12, w: w + 0.6, h: 0.32, fontSize: 11, color: C.accent6, align: "center", isTextBox: true, margin: 0, objectName: nm("caption") });
    }
    return w;
  }
  function bullets(slide, items, opts) {
    const runs = items.map((t, i) => {
      const parts = Array.isArray(t) ? t : [t];
      return parts.map((p, j) => ({
        text: typeof p === "string" ? p : p.text,
        options: {
          ...(typeof p === "string" ? {} : p.options),
          ...(j === 0 ? { bullet: { indent: 16 } } : {}),
          ...(j === parts.length - 1 && i < items.length - 1 ? { breakLine: true } : {}),
        },
      }));
    }).flat();
    slide.addText(runs, { fontSize: 16, color: C.text1, paraSpaceAfter: 6, valign: "top", isTextBox: true, margin: 0, ...opts, objectName: nm("list") });
  }
  function steps(slide, items, opts) {
    const runs = items.map((t, i) => ({ text: t, options: { bullet: { type: "number", indent: 20 }, ...(i < items.length - 1 ? { breakLine: true } : {}) } }));
    slide.addText(runs, { fontSize: 15, color: C.text1, paraSpaceAfter: 5, valign: "top", isTextBox: true, margin: 0, ...opts, objectName: nm("steps") });
  }
  // A light panel quoting the user manual.
  function manualBox(slide, heading, x, y, w, h) {
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, rectRadius: 0.12, fill: { color: C.background2 }, line: { color: C.accent5, width: 1 }, objectName: nm("manual panel") });
    slide.addImage({ data: icons.book, x: x + 0.22, y: y + 0.2, w: 0.32, h: 0.32, objectName: nm("manual icon") });
    slide.addText(heading, { x: x + 0.65, y: y + 0.17, w: w - 0.85, h: 0.38, fontSize: 13, bold: true, color: C.text2, isTextBox: true, margin: 0, valign: "middle", objectName: nm("manual heading") });
    return { x: x + 0.3, y: y + 0.7, w: w - 0.55, h: h - 0.85 };
  }
  // Icon in a yellow circle.
  function badge(slide, data, x, y, d = 0.62) {
    slide.addShape(pres.shapes.OVAL, { x, y, w: d, h: d, fill: { color: C.accent1 }, line: { color: C.accent1, width: 0 }, objectName: nm("badge") });
    slide.addImage({ data, x: x + d * 0.2, y: y + d * 0.2, w: d * 0.6, h: d * 0.6, objectName: nm("badge icon") });
  }
  function card(slide, x, y, w, h, fill) {
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, rectRadius: 0.14, fill: { color: fill || C.background2 }, line: { color: fill || C.background2, width: 0 }, objectName: nm("card") });
  }
  const navy = HEX.dk1;
  const icons = {
    book: await icon(Md.MdMenuBook, HEX.dk2),
    checklist: await icon(Md.MdChecklist, navy),
    crew: await icon(Md.MdGroups, navy),
    track: await icon(Md.MdMoreTime, navy),
    report: await icon(Md.MdPictureAsPdf, navy),
    phone: await icon(Md.MdPhoneAndroid, navy),
    apple: await icon(Md.MdPhoneIphone, navy),
    sync: await icon(Md.MdSyncAlt, navy),
    lock: await icon(Md.MdLockOutline, navy),
    camera: await icon(Md.MdPhotoCamera, navy),
    text: await icon(Md.MdSms, navy),
    file: await icon(Md.MdUploadFile, navy),
    mic: await icon(Md.MdMic, navy),
    verified: await icon(Md.MdVerifiedUser, navy),
    key: await icon(Md.MdVpnKey, navy),
    backup: await icon(Md.MdBackup, navy),
    swap: await icon(Md.MdSwapHoriz, navy),
    store: await icon(Md.MdStorefront, navy),
    arrow: await icon(Md.MdArrowForward, HEX.accent5),
    flow: [Md.MdLogin, Md.MdFolderOpen, Md.MdPlaylistAdd, Md.MdTaskAlt, Md.MdHowToReg, Md.MdSend],
  };
  icons.flowData = [];
  for (const F of icons.flow) icons.flowData.push(await icon(F, navy));

  // ===================== 1. Title =====================
  pres.addSection({ title: "Introduction" });
  let s = pres.addSlide({ masterName: "Title dark", sectionTitle: "Introduction" });
  s.addText("Daily Work Log", { placeholder: "title" });
  s.addText("Field work, logged on your phone", { placeholder: "body" });
  s.addText("How the app works: the flow, every feature, and the user manual\nVersion 4.57  ·  November 2026  ·  Created by Tanner Tibbetts", {
    x: 0.7, y: 4.8, w: 7.4, h: 0.9, fontSize: 15, color: C.accent5, isTextBox: true, margin: 0, valign: "top", objectName: nm("subtitle"),
  });
  phone(s, "intro.png", 9.3, 0.7, 6.1);
  s.addNotes("Daily Work Log replaces the paper checklist. It keeps a foreman's whole day in one place: daily checklists, crew, texts, overtime, attendance, notes, sea can moves, links, contacts and documents, and turns it into a clean report. Everything is saved on the phone as you go; there is no Save button to remember.");

  // ===================== 2. Overview =====================
  s = pres.addSlide({ masterName: "Content", sectionTitle: "Introduction" });
  s.addText("One app for the whole shift", { placeholder: "title" });
  const pillars = [
    [icons.checklist, "Plan the day", "A daily log (checklist) per shift. Fields are tasks, colour-coded South, North or Unzoned."],
    [icons.crew, "Run the crew", "Assign people to fields and the app texts them. Notify sends a note or photo to anyone."],
    [icons.track, "Track the details", "Each day's overtime and attendance, notes with tags, sea can moves, links, contacts, documents."],
    [icons.report, "Send the report", "PDF, Word or Excel, emailed to the report list. Everything ticked for the day goes in."],
  ];
  pillars.forEach(([ic, h, t], i) => {
    const x = 0.6 + (i % 2) * 4.3, y = 1.55 + Math.floor(i / 2) * 2.55;
    card(s, x, y, 4.0, 2.25);
    badge(s, ic, x + 0.3, y + 0.3);
    s.addText(h, { x: x + 1.1, y: y + 0.3, w: 2.7, h: 0.62, fontSize: 20, bold: true, color: C.text1, valign: "middle", isTextBox: true, margin: 0, objectName: nm("pillar head") });
    s.addText(t, { x: x + 0.3, y: y + 1.08, w: 3.45, h: 1.0, fontSize: 15, color: C.text1, valign: "top", isTextBox: true, margin: 0, objectName: nm("pillar text") });
  });
  phone(s, "project.png", 10.05, 1.45, 5.05, "A project, Checklists tab");
  s.addNotes("From the manual, What the app does: A project is a job or area. Each project has daily logs, also called checklists. A checklist holds fields; a field is one task. You tick fields off as they get done and your name and the time are stamped on each one. You can add photos and notes to a field and assign crew to it; the app can text them. At the end of the day you send a PDF, Word or Excel report, or email it to your report list.");

  // ===================== 3. Two versions =====================
  s = pres.addSlide({ masterName: "Content", sectionTitle: "Introduction" });
  s.addText("Two versions, one set of work", { placeholder: "title" });
  const cols = [
    [icons.phone, "Classic Android app", "Version 4.57", ["The original app, written in Java", "Built on your own phone in Termux from a zip", "Owner approval: new phones ask you for access", "Your crew numbers built in"]],
    [icons.sync, "Shared by both", "Same data, same manual", ["Same worklog file and backup file", "A backup from one opens in the other", "The same 70-topic user manual", "Every feature goes into both"]],
    [icons.apple, "New app", "iPhone and Android, version 5.24", ["Written in Flutter", "Ready for the App Store (built by Codemagic)", "Installs over the classic app as an update", "App Store copy needs no sign-in"]],
  ];
  cols.forEach(([ic, h, sub, items], i) => {
    const x = 0.6 + i * 4.1, mid = i === 1;
    card(s, x, 1.55, 3.85, 5.1, mid ? C.text1 : C.background2);
    badge(s, ic, x + 0.3, 1.85);
    s.addText(h, { x: x + 1.1, y: 1.8, w: 2.6, h: 0.42, fontSize: 19, bold: true, color: mid ? C.background1 : C.text1, isTextBox: true, margin: 0, valign: "middle", objectName: nm("col head") });
    s.addText(sub, { x: x + 1.1, y: 2.22, w: 2.6, h: 0.32, fontSize: 13, color: mid ? C.accent1 : C.text2, isTextBox: true, margin: 0, valign: "middle", objectName: nm("col sub") });
    bullets(s, items, { x: x + 0.35, y: 2.95, w: 3.2, h: 3.4, fontSize: 15, color: mid ? C.background1 : C.text1 });
  });
  s.addNotes("Every feature goes into both versions. They read and write the same worklog file, so the new app installs over the classic one as an update and keeps everything. Where they work differently, the user manual says so with a 'new app only' or 'classic app only' mark.");

  // ===================== 4. Flow =====================
  s = pres.addSlide({ masterName: "Content", sectionTitle: "Introduction" });
  s.addText("How a day flows", { placeholder: "title" });
  const flow = [
    ["Open and sign in", "Enter, then your PIN or approval (owner copies only)"],
    ["Pick a project", "A job or area, with its own crew and tabs"],
    ["Start the daily log", "Blank, carried over, copied, or from a template"],
    ["Work the fields", "Tick, photos, load securement, delays, texts"],
    ["Overtime and attendance", "Each day keeps its own, filled from the day before"],
    ["Send the report", "PDF, Word or Excel, to the report email list"],
  ];
  const fw = 1.86, gap = 0.17;
  flow.forEach(([h, t], i) => {
    const x = 0.6 + i * (fw + gap), y = 2.0;
    card(s, x, y, fw, 3.7, i === flow.length - 1 ? C.text1 : C.background2);
    badge(s, icons.flowData[i], x + 0.2, y + 0.25, 0.7);
    s.addText(String(i + 1), { x: x + fw - 0.65, y: y + 0.3, w: 0.45, h: 0.55, fontSize: 30, bold: true, color: i === flow.length - 1 ? C.accent1 : C.accent5, align: "right", isTextBox: true, margin: 0, objectName: nm("step number") });
    s.addText(h, { x: x + 0.2, y: y + 1.15, w: fw - 0.35, h: 0.85, fontSize: 17, bold: true, color: i === flow.length - 1 ? C.background1 : C.text1, valign: "top", isTextBox: true, margin: 0, objectName: nm("flow head") });
    s.addText(t, { x: x + 0.2, y: y + 2.05, w: fw - 0.35, h: 1.5, fontSize: 14, color: i === flow.length - 1 ? C.accent5 : C.text1, valign: "top", isTextBox: true, margin: 0, objectName: nm("flow text") });
  });
  s.addText("Everything saves by itself as you go. There is no Save button to remember.", { x: 0.6, y: 6.05, w: 12, h: 0.45, fontSize: 16, italic: true, color: C.text2, isTextBox: true, margin: 0, objectName: nm("tagline") });
  s.addNotes("This is the path through one shift. The following slides take each step in turn, quoting the in-app user manual.");

  // ===================== Section: Getting started =====================
  pres.addSection({ title: "Getting started" });
  s = pres.addSlide({ masterName: "Section", sectionTitle: "Getting started" });
  s.addText("Getting started", { placeholder: "title" });
  s.addText("Opening the app, access and sign-in, projects and tabs", { placeholder: "body" });

  // ----- sign in -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "Getting started" });
  s.addText("Opening the app and signing in", { placeholder: "title" });
  phone(s, "intro.png", 0.75, 1.5, 4.9, "Opening screen");
  phone(s, "signin.png", 3.35, 1.5, 4.9, "Asking for access");
  let box = manualBox(s, "From the manual: Asking for access (first time)", 6.1, 1.5, 6.6, 3.15);
  steps(s, [
    'Tap "Enter" on the opening screen.',
    'Under "1. Ask for access", type your full name.',
    'Tap "Request access". The request is emailed (or texted) to the owner.',
    "Wait for the owner to send back an approval code, then paste it in.",
  ], { ...box });
  bullets(s, [
    "Each approval lasts one year, for that phone and that name",
    'Choose a PIN, or tick "Keep me signed in on this phone"',
    "Copies without an owner key, like the App Store one, go straight in",
  ], { x: 6.1, y: 4.95, w: 6.6, h: 1.7, fontSize: 15 });
  s.addNotes("The first time you use the app on a phone, the app owner has to approve it. You ask by email or text, and it only takes a minute. Use the same name you will use in the app: the approval only works for this phone and this name. Forgot your PIN? The owner approves you again.");

  // ----- projects and tabs -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "Getting started" });
  s.addText("Projects and their six tabs", { placeholder: "title" });
  phone(s, "projects.png", 0.75, 1.5, 4.9, "Your projects");
  phone(s, "project.png", 3.35, 1.5, 4.9, "Inside a project");
  const tabs = [
    ["Checklists", "your daily logs, newest first"],
    ["Notify", "text crew a note or photo"],
    ["Links", "web links with calendar reminders, and Documents"],
    ["Contacts", "company contacts not on your crew"],
    ["Notes", "your notes and photos, with tags"],
    ["Sea Can Movements", "where each sea can came from and went"],
  ];
  tabs.forEach(([t, d], i) => {
    const x = 6.1 + (i % 2) * 3.35, y = 1.5 + Math.floor(i / 2) * 1.25;
    card(s, x, y, 3.15, 1.05);
    s.addText([{ text: t, options: { bold: true, fontSize: 16, breakLine: true } }, { text: d, options: { fontSize: 14 } }],
      { x: x + 0.2, y: y + 0.12, w: 2.8, h: 0.85, color: C.text1, valign: "top", isTextBox: true, margin: 0, objectName: nm("tab") });
  });
  bullets(s, [
    "Search on Checklists looks through every day of the project, sea can numbers too",
    'Hide tabs you don\'t use with "Show or hide tabs" in the project ⋯ menu',
  ], { x: 6.1, y: 5.35, w: 6.6, h: 1.2, fontSize: 15 });
  s.addNotes("Overtime and Attendance used to be project tabs. Since version 4.54 they live inside each daily log, so every day keeps its own and an old day's report shows what was entered that day.");

  // ===================== Section: The daily log =====================
  pres.addSection({ title: "The daily log" });
  s = pres.addSlide({ masterName: "Section", sectionTitle: "The daily log" });
  s.addText("The daily log", { placeholder: "title" });
  s.addText("Starting a checklist, working the fields, crew and texts", { placeholder: "body" });

  // ----- starting -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "The daily log" });
  s.addText("Starting a daily log", { placeholder: "title" });
  box = manualBox(s, "From the manual: Starting a daily log", 0.6, 1.5, 6.3, 2.75);
  steps(s, [
    'Open the project and tap "Checklists".',
    'Tap the big "+" at the bottom.',
    "Pick the date (today is already picked) and tap OK.",
    'Choose how to start it from the "Start the … log with" menu.',
  ], { ...box });
  s.addText("Ways to start it", { x: 0.6, y: 4.5, w: 6, h: 0.4, fontSize: 18, bold: true, color: C.text1, isTextBox: true, margin: 0, objectName: nm("subhead") });
  bullets(s, [
    [{ text: "Blank checklist: ", options: { bold: true } }, "an empty list"],
    [{ text: "Carry over marked fields: ", options: { bold: true } }, 'only fields marked "Carry over to next shift"'],
    [{ text: "Carry over unfinished: ", options: { bold: true } }, "everything not done on the last log, with notes"],
    [{ text: "Copy all fields, or a template: ", options: { bold: true } }, "a fresh copy of a saved list"],
  ], { x: 0.6, y: 5.0, w: 6.4, h: 1.8, fontSize: 15 });
  phone(s, "log.png", 7.6, 1.45, 5.15, "A new daily log");
  s.addNotes("The new log starts fresh: nothing is ticked and there are no photos. Each copied field keeps its title, area, assigned people and Photos Required setting. The crew from the last checklist comes along. The day's overtime and attendance are filled in from the day before.");

  // ----- reading a checklist -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "The daily log" });
  s.addText("Reading a checklist", { placeholder: "title" });
  phone(s, "day_sheets.png", 0.75, 1.45, 5.15, "Progress, crew, Overtime and Attendance");
  phone(s, "format_log.png", 3.45, 1.45, 5.15, "Colour-coded field cards");
  bullets(s, [
    "The ring shows how much is done, and turns into a green check at 100%",
    '"3/10 fields completed" counts the ticked fields; the person button sets the crew',
    "Overtime and Attendance buttons open this day's own sheets",
    'Filters: "Assigned to Me", "Incomplete", "Complete", "Unassigned"',
    '"New Field", or the blue mic, adds a field',
  ], { x: 6.35, y: 1.5, w: 6.35, h: 3.2, fontSize: 15 });
  const zones = [["South", "FCE8EF", "F0BFD0"], ["North", "D4F0FF", "5DB8EE"], ["Unzoned", "FFF5D1", "EED98C"], ["Done", "F3FBF6", "1DB04B"]];
  s.addText("Card colours", { x: 6.35, y: 4.9, w: 6, h: 0.4, fontSize: 17, bold: true, color: C.text1, isTextBox: true, margin: 0, objectName: nm("subhead") });
  zones.forEach(([z, f, l], i) => {
    const x = 6.35 + i * 1.6;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y: 5.4, w: 1.45, h: 0.7, rectRadius: 0.1, fill: { color: f }, line: { color: l, width: 2 }, objectName: nm("zone swatch") });
    s.addText(z, { x, y: 5.4, w: 1.45, h: 0.7, fontSize: 15, bold: true, color: C.text1, align: "center", valign: "middle", isTextBox: true, margin: 0, objectName: nm("zone label") });
  });
  s.addNotes("From the manual, Field card colours: South cards are pale pink, North pale blue, Unzoned pale yellow, no area white. When a field is done, the card gets a green border. Small labels show Load secured, the area, Next shift and Photos Required. Delays show in red, cleared delays in green, and words marked 'on hold' are highlighted.");

  // ----- your own area names -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "The daily log" });
  s.addText("Your own names for the areas", { placeholder: "title" });
  phone(s, "rename_areas.png", 0.75, 1.45, 5.15, "Rename areas");
  phone(s, "renamed_log.png", 3.45, 1.45, 5.15, "Cards with the new names");
  bullets(s, [
    [{ text: "Fit the work front: ", options: { bold: true } }, "South, North and Unzoned can become Garage 1, Garage 2, Yard, or any name"],
    [{ text: "Each project has its own: ", options: { bold: true } }, "different sites can use different names"],
    [{ text: "Colours stay: ", options: { bold: true } }, "pink, blue and yellow, so the cards still read at a glance"],
    [{ text: "Everywhere: ", options: { bold: true } }, "field cards, the area boxes, and the report headings in PDF, Word and Excel"],
  ], { x: 6.35, y: 1.5, w: 6.35, h: 2.9, fontSize: 16, paraSpaceAfter: 10 });
  box = manualBox(s, "From the manual: Renaming the areas", 6.35, 4.45, 6.35, 2.3);
  steps(s, [
    'Tap "Rename areas" in the project ⋯ menu, beside "Area" on a field, or in "New field".',
    "Type the new names. Leave a box empty to keep the usual name.",
    'Tap "Save". "Use usual names" brings back South, North and Unzoned.',
  ], { ...box, fontSize: 14 });
  s.addNotes("New in version 4.57. Fields keep their area when you rename it; only the names change. The renaming is per project, so one job can use Garage 1 and Garage 2 while another keeps North and South.");

  // ----- working a field -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "The daily log" });
  s.addText("Working a field", { placeholder: "title" });
  phone(s, "task.png", 0.75, 1.45, 5.15, "Edit Field");
  phone(s, "delay_field.png", 3.45, 1.45, 5.15, "A delay and its go-ahead");
  const feats = [
    ["Tick it done", "Your name and the time are stamped on it"],
    ["Photos Required", "It won't tick until a photo is added"],
    ["Load securement", "Take the photos and email them in one go"],
    ["Carry over to next shift", "Offered when the next log starts"],
    ["Task is delayed", "Reason in red; Delay is cleared in green"],
    ["Notes with formatting", "Bold, underline and colour; on hold stands out"],
  ];
  feats.forEach(([h, t], i) => {
    const x = 6.35 + (i % 2) * 3.2, y = 1.5 + Math.floor(i / 2) * 1.6;
    card(s, x, y, 3.0, 1.4);
    s.addText([{ text: h, options: { bold: true, fontSize: 16, breakLine: true } }, { text: t, options: { fontSize: 14 } }],
      { x: x + 0.2, y: y + 0.15, w: 2.65, h: 1.15, color: C.text1, valign: "top", isTextBox: true, margin: 0, objectName: nm("feature") });
  });
  s.addNotes("From the manual, Completing a field: tap the round grey circle at the left of the card; it turns green with a tick. If a field has Photos Required and no photo yet, it won't tick: 'This field needs a photo before it can be completed'. Marking a delay: tap Task is delayed, type the reason, tap Save. When you get the OK, tap Delay is cleared and, if you like, who gave the go-ahead. Both show on the checklist and in every report.");

  // ----- adding fields fast -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "The daily log" });
  s.addText("Adding fields without typing", { placeholder: "title" });
  const ways = [
    [icons.camera, "Photo of a task list", "The app reads the printed list"],
    [icons.camera, "North/South zone sheet", "Reads the zone sheet and sets each area"],
    [icons.text, "Text screenshot", "Turn an assignment text into a field"],
    [icons.file, "Excel or CSV file", "One field per row"],
    [icons.mic, "Speak it", "The blue mic button adds a field"],
  ];
  ways.forEach(([ic, h, t], i) => {
    const y = 1.5 + i * 1.02;
    badge(s, ic, 0.6, y + 0.05, 0.6);
    s.addText([{ text: h, options: { bold: true, fontSize: 17, breakLine: true } }, { text: t, options: { fontSize: 14 } }],
      { x: 1.4, y, w: 5.2, h: 0.85, color: C.text1, valign: "middle", isTextBox: true, margin: 0, objectName: nm("way") });
  });
  box = manualBox(s, "From the manual: Adding fields from a photo or file", 6.9, 1.5, 3.1, 4.95);
  steps(s, [
    "Open the checklist.",
    "Tap the camera icon at the top.",
    "Pick a photo, a screenshot or a file.",
    'Check the list on "Add fields", then tap "Add … fields".',
  ], { ...box, fontSize: 14 });
  phone(s, "import_review.png", 10.45, 1.45, 4.9, "Review before adding");
  s.addNotes("Tip from the manual: hold the phone straight above the page, close up, in good light. Older Excel files (.xls) can't be read; save as .xlsx or .csv first. A photo taken for this is deleted after it is read.");

  // ----- crew -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "The daily log" });
  s.addText("Managing the crew", { placeholder: "title" });
  phone(s, "manage_crew.png", 0.75, 1.45, 5.15, "Manage crew, in groups");
  phone(s, "crew_details.png", 3.45, 1.45, 5.15, "A person's details");
  bullets(s, [
    [{ text: "Groups you name: ", options: { bold: true } }, "Crew, Techs, Supervision, Night shift…; drag people between them"],
    [{ text: "Each person: ", options: { bold: true } }, "phone, Employee #, BP #, DISN #, level (Apprentice 1st–4th term or Journeyman), trade, union local"],
    [{ text: "Paste crew numbers: ", options: { bold: true } }, "paste rows from Excel; names are matched even with small typos"],
    [{ text: "Assignment texts: ", options: { bold: true } }, "people ticked for texts get one when you assign them a field"],
    [{ text: "Rename once: ", options: { bold: true } }, "a new name changes everywhere in the project"],
  ], { x: 6.35, y: 1.5, w: 6.35, h: 5.2, fontSize: 16, paraSpaceAfter: 10 });
  s.addNotes("From the manual, How assignment texts work: when you assign people to a field, each new person ticked for texts in Manage crew gets a text with the checklist, the project and the field. On Android the text is sent straight from the app with 5 seconds to Undo; on iPhone, Messages opens with the text ready. The person can screenshot the text into their own app, and their app texts you back when they mark it complete.");

  // ----- notify -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "The daily log" });
  s.addText("Notify: a note or photo to the crew", { placeholder: "title" });
  box = manualBox(s, "From the manual: Sending a note to crew", 0.6, 1.5, 6.3, 3.6);
  steps(s, [
    'Open the project and tap "Notify".',
    'Tap the big "+" at the bottom.',
    'Tick the people, or "Select all", or a whole group, and tap "Assign".',
    'Type your note in "Write your note…".',
    'To add photos, tap "Photos".',
    'Tap "Send note".',
  ], { ...box });
  bullets(s, [
    "Android: texted straight from the app",
    "iPhone and photos: Messages or the share menu opens, ready to send",
    'Every note ends with "From:", your name, the project and your number',
  ], { x: 0.6, y: 5.35, w: 6.3, h: 1.4, fontSize: 15 });
  phone(s, "notify.png", 7.6, 1.45, 5.15, "Writing a note");
  phone(s, "notify_group.png", 10.3, 1.45, 5.15, "Picking a whole group");

  // ===================== Section: Tracking =====================
  pres.addSection({ title: "Tracking" });
  s = pres.addSlide({ masterName: "Section", sectionTitle: "Tracking" });
  s.addText("Tracking the day", { placeholder: "title" });
  s.addText("Overtime, attendance, notes, documents, contacts, sea cans and link reminders", { placeholder: "body" });

  // ----- overtime -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "Tracking" });
  s.addText("Overtime: each day keeps its own", { placeholder: "title" });
  phone(s, "overtime.png", 0.75, 1.45, 5.15, "The day's Overtime sheet");
  bullets(s, [
    "Yes or No for Fri, Sat and Sun; hours 1 to 10; Confirmed Yes, No or Training",
    "A comment under each person, and notes under the table",
    "Confirmed Yes lights the whole row green, No red, so it reads without sliding sideways",
    "A new day starts from the day before, so sign-ups carry on through the week",
    "Changing today never changes an old day",
  ], { x: 3.75, y: 1.5, w: 4.3, h: 4.9, fontSize: 15, paraSpaceAfter: 9 });
  s.addShape(pres.shapes.RECTANGLE, { x: 8.45, y: 1.45, w: 4.3, h: 4.3 * 384 / 1496, fill: { color: C.background1 }, line: { color: HEX.accent5, width: 1 }, objectName: nm("report frame") });
  s.addImage({ path: img("ot_table.png"), x: 8.45, y: 1.45, w: 4.3, h: 4.3 * 384 / 1496, objectName: nm("report table"), altText: "Overtime table in the PDF report, Yes row green and No row red" });
  s.addText("In the report: confirmed Yes rows shaded pale green, No pale red", { x: 8.45, y: 2.65, w: 4.3, h: 0.6, fontSize: 12, color: C.accent6, isTextBox: true, margin: 0, objectName: nm("caption") });
  s.addNotes("From the manual: each daily log has its own Overtime sheet for the weekend. When you start a new day it is filled in from the day before, so sign-ups carry on through the week. Changing it on one day never changes another day, so an old day's report always shows what was on the sheet that day. Tick 'Include in this day's report' to add it to that day's report. For the next weekend, tap Clear answers.");

  // ----- attendance -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "Tracking" });
  s.addText("Attendance: who's here today", { placeholder: "title" });
  phone(s, "attendance.png", 0.75, 1.45, 5.15, "The day's Attendance sheet");
  s.addText("Answers for each person", { x: 3.75, y: 1.5, w: 8.9, h: 0.4, fontSize: 18, bold: true, color: C.text1, isTextBox: true, margin: 0, objectName: nm("subhead") });
  const st = [["Yes", "E3F6EA", HEX.accent3], ["No", "FDECEC", HEX.accent4], ["Called in", HEX.lt2, HEX.dk1], ["AWOL", "FDECEC", HEX.accent4], ["Vacation", HEX.lt2, HEX.dk1], ["Off", HEX.lt2, HEX.dk1], ["Training", HEX.lt2, HEX.dk1], ["B72", HEX.lt2, HEX.dk1], ["Step up", HEX.lt2, HEX.dk1], ["Late", HEX.lt2, HEX.dk1], ["Left early", HEX.lt2, HEX.dk1]];
  st.forEach(([t, f, c], i) => {
    const x = 3.75 + (i % 6) * 1.5, y = 2.05 + Math.floor(i / 6) * 0.65;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w: 1.38, h: 0.5, rectRadius: 0.25, fill: { color: f }, line: { color: f, width: 0 }, objectName: nm("status chip") });
    s.addText(t, { x, y, w: 1.38, h: 0.5, fontSize: 14, bold: true, color: c, align: "center", valign: "middle", isTextBox: true, margin: 0, objectName: nm("status") });
  });
  bullets(s, [
    "A comment for each person, for example \"Left at 1pm, doctor\"",
    'A count under the table, like "Yes 5 · Vacation 1 · Late 1"',
    "A new day copies the names and notes, with the answers blank",
    "Each day keeps its own answers, so an old report shows who was there that day",
    "Export just the attendance to PDF or Excel, or tick it into the day's report",
  ], { x: 3.75, y: 3.6, w: 8.9, h: 3.0, fontSize: 16, paraSpaceAfter: 8 });

  // ----- other tabs -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "Tracking" });
  s.addText("Notes, documents, contacts and sea cans", { placeholder: "title" });
  const four = [
    ["note_tags.png", "Notes", "Tag Safety concern, Training or Crew concern; tick into the day's report"],
    ["documents.png", "Documents", "Photo (with crop) or upload a file; open, rename, share"],
    ["contacts.png", "Contacts", "Call, text, email or open a link; shared by every project"],
    ["seacans.png", "Sea Can Movements", "From and to (letter, row, level) and weight; exports in the site sheet's layout"],
  ];
  four.forEach(([f, h, t], i) => {
    const x = 0.75 + i * 3.15;
    phone(s, f, x, 1.45, 3.4);
    s.addText([{ text: h, options: { bold: true, fontSize: 16, breakLine: true } }, { text: t, options: { fontSize: 13 } }],
      { x: x - 0.05, y: 5.05, w: 2.75, h: 1.7, color: C.text1, valign: "top", isTextBox: true, margin: 0, objectName: nm("tab text") });
  });
  s.addNotes("Notes save by themselves; tick 'Include in today's report' to put a note in the day's report, and a tagged note shows its tag in orange bold. Documents are reached from the Documents button on the Links tab. The sea can list can be exported to PDF, Excel, CSV or Word, and added to the daily report.");

  // ----- link reminders -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "Tracking" });
  s.addText("Link reminders in your calendar", { placeholder: "title" });
  box = manualBox(s, "From the manual: Reminders on a link", 0.6, 1.5, 6.3, 3.05);
  steps(s, [
    'Tap ⋮ on the link and tap "Edit link".',
    'Tick "Remind me in my calendar".',
    'Pick the "Date" and "Time".',
    'Under "Repeat": One time only, Every day, Every week, Every month or Every year.',
    'Pick an "Alert", and a "Second alert" if you want two.',
    'Tap "Save". The first time, allow calendar access.',
  ], { ...box, fontSize: 14 });
  bullets(s, [
    "The app writes it straight into the phone's calendar, with the link in its notes",
    "Alerts from at the time up to 1 week before, so a BBO never gets missed",
    "Edit it to update the calendar; untick it or delete the link to take it out",
  ], { x: 0.6, y: 4.85, w: 6.3, h: 1.8, fontSize: 15, paraSpaceAfter: 8 });
  phone(s, "link_reminder.png", 7.6, 1.45, 5.15, "Setting a weekly reminder");
  phone(s, "link_reminder_card.png", 10.3, 1.45, 5.15, "The reminder on the link");
  s.addNotes("New in version 4.56. Works on Android and iPhone. A repeating reminder keeps the same time of day when the clocks change. If calendar access was turned off, the app says so: turn on Calendar for Daily Work Log in the phone's Settings and tap Save again.");

  // ===================== Section: Reports & ownership =====================
  pres.addSection({ title: "Reports and ownership" });
  s = pres.addSlide({ masterName: "Section", sectionTitle: "Reports and ownership" });
  s.addText("Reports and ownership", { placeholder: "title" });
  s.addText("The day's report, the owner's tools, the user manual and the App Store", { placeholder: "body" });

  // ----- report -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "Reports and ownership" });
  s.addText("The day's report", { placeholder: "title" });
  const rh = 5.4, rw = rh * 935 / 1210;
  s.addShape(pres.shapes.RECTANGLE, { x: 0.75, y: 1.4, w: rw, h: rh, fill: { color: C.background1 }, line: { color: HEX.accent5, width: 1 }, shadow: { type: "outer", color: "000000", opacity: 0.18, blur: 6, offset: 2, angle: 90 }, objectName: nm("page shadow") });
  s.addImage({ path: img("report1.png"), x: 0.75, y: 1.4, w: rw, h: rh, altText: "Page 1 of a sample PDF report", objectName: nm("report page") });
  bullets(s, [
    "Project, checklist name, location, who made it and when",
    "Progress bar and the crew",
    "Fields grouped South, North, Unzoned, with stamps, delays, load securement, notes and photos",
    'Ticked "Also today" items and notes',
    "The day's own overtime and attendance, and the sea cans, when ticked",
  ], { x: 5.3, y: 1.5, w: 4.2, h: 5.0, fontSize: 15, paraSpaceAfter: 9 });
  const fmts = [["PDF", "with photos side by side"], ["Word", "the same, to edit"], ["Excel", "a row per field"], ["Email", "PDF to the report list"]];
  fmts.forEach(([h, t], i) => {
    const y = 1.5 + i * 1.3;
    card(s, 9.85, y, 2.85, 1.1, i === 3 ? C.text1 : C.background2);
    s.addText([{ text: h, options: { bold: true, fontSize: 20, breakLine: true, color: i === 3 ? C.accent1 : C.text2 } }, { text: t, options: { fontSize: 14, color: i === 3 ? C.background1 : C.text1 } }],
      { x: 10.05, y: y + 0.1, w: 2.5, h: 0.9, valign: "middle", isTextBox: true, margin: 0, objectName: nm("format") });
  });
  s.addNotes("From the manual, Exporting a report: open the checklist and tap the three dots, then Export to PDF, Export to Word or Export to Excel. When it's ready, pick Email to report list (PDF), Share or email, or Save to phone. Emailing: tap Email report; if the report email list is empty, type the addresses first. The subject is the checklist name and project.");

  // ----- owner tools -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "Reports and ownership" });
  s.addText("The owner's tools and ownership proof", { placeholder: "title" });
  phone(s, "approvals.png", 0.75, 1.45, 5.15, "Approvals / hand over");
  const own = [
    [icons.key, "Approve people", "Paste a request, tap Approve, send the code back. Each approval lasts a year."],
    [icons.swap, "Hand over ownership", "Move the owner role to a new phone, or to a buyer, with a 6-digit code."],
    [icons.backup, "Back up and restore", "All work in one file that opens in either app, on Android or iPhone."],
    [icons.verified, "Prove it's yours", "© notice on screen; a hidden tag and mark in every report file; a signed ownership proof."],
  ];
  own.forEach(([ic, h, t], i) => {
    const x = 3.75 + (i % 2) * 4.5, y = 1.5 + Math.floor(i / 2) * 2.6;
    card(s, x, y, 4.25, 2.35);
    badge(s, ic, x + 0.3, y + 0.3);
    s.addText(h, { x: x + 1.1, y: y + 0.3, w: 3.0, h: 0.62, fontSize: 18, bold: true, color: C.text1, valign: "middle", isTextBox: true, margin: 0, objectName: nm("own head") });
    s.addText(t, { x: x + 0.3, y: y + 1.08, w: 3.7, h: 1.15, fontSize: 15, color: C.text1, valign: "top", isTextBox: true, margin: 0, objectName: nm("own text") });
  });
  s.addNotes("From the manual, Copyright and ownership proof: Daily Work Log was created by Tanner Tibbetts. Every PDF, Word and Excel file the app makes carries a hidden tag in its file properties. An ownership proof is a statement signed on the owner's phone with the app's owner key; only that phone can make one, and anyone can check it under Support, Ownership proof.");

  // ----- manual -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "Reports and ownership" });
  s.addText("The user manual is built in", { placeholder: "title" });
  const groups = [["Projects & checklists", 15], ["Fields", 15], ["Getting started", 11], ["Tabs", 9], ["Help", 7], ["Crew & texting", 5], ["App owner", 5], ["Reports", 3]];
  s.addChart(pres.charts.BAR, [{ name: "Topics", labels: groups.map((g) => g[0]), values: groups.map((g) => g[1]) }], {
    x: 0.5, y: 1.45, w: 6.6, h: 5.3, barDir: "bar",
    chartColors: [HEX.dk2], showValue: true, dataLabelPosition: "outEnd", dataLabelColor: HEX.dk1, dataLabelFontSize: 12, dataLabelFontFace: "+mn-lt",
    catAxisOrientation: "maxMin", catAxisLabelColor: HEX.dk1, catAxisLabelFontSize: 13, catAxisLabelFontFace: "+mn-lt",
    valAxisHidden: true, valGridLine: { style: "none" }, catGridLine: { style: "none" }, showLegend: false,
    showTitle: true, title: "70 topics in 8 groups", titleColor: HEX.dk1, titleFontSize: 16, titleFontFace: "+mn-lt",
    objectName: nm("manual chart"),
  });
  box = manualBox(s, "From the manual: Using this user manual", 7.5, 1.5, 5.25, 2.6);
  bullets(s, [
    'Tap "Support", then "User manual" on the opening screen, sign-in screen or profile More…',
    "Search for any word; topics link to each other",
    "Pictures of the real screens; marks for new-app-only or classic-only features",
  ], { ...box, fontSize: 14 });
  phone(s, "search.png", 9.25, 4.3, 2.55);
  s.addText("Updated with every change to the app", { x: 7.5, y: 4.35, w: 1.6, h: 2.4, fontSize: 15, italic: true, color: C.text2, valign: "middle", isTextBox: true, margin: 0, objectName: nm("manual note") });
  s.addNotes("Whenever anything in the app changes, the manual changes in the same update: sections are edited or added, button labels are quoted exactly as the app shows them, and screenshots are re-taken. An automatic check makes sure every link and picture in the manual works.");

  // ----- App Store -----
  s = pres.addSlide({ masterName: "Content", sectionTitle: "Reports and ownership" });
  s.addText("Ready for the iPhone App Store", { placeholder: "title" });
  const stats = [["41", "steps on the check-off sheet, from Apple membership to Release"], ["7", "App Store screenshots made at Apple's iPhone size"], ["0", "data collected: everything stays on the phone"]];
  stats.forEach(([v, t], i) => {
    const x = 0.6 + i * 4.15;
    card(s, x, 1.55, 3.9, 2.35, i === 2 ? C.text1 : C.background2);
    s.addText(v, { x: x + 0.3, y: 1.65, w: 3.3, h: 1.2, fontSize: 64, bold: true, color: i === 2 ? C.accent1 : C.text2, isTextBox: true, margin: 0, valign: "middle", objectName: nm("stat") });
    s.addText(t, { x: x + 0.3, y: 2.85, w: 3.35, h: 0.9, fontSize: 15, color: i === 2 ? C.background1 : C.text1, isTextBox: true, margin: 0, valign: "top", objectName: nm("stat label") });
  });
  const path4 = [[icons.store, "Apple membership"], [icons.phone, "Codemagic builds it"], [icons.apple, "Test on TestFlight"], [icons.verified, "Apple reviews and you release"]];
  path4.forEach(([ic, t], i) => {
    const x = 0.6 + i * 3.15;
    badge(s, ic, x, 4.55, 0.7);
    s.addText(t, { x: x + 0.85, y: 4.5, w: 1.95, h: 0.8, fontSize: 15, bold: true, color: C.text1, valign: "middle", isTextBox: true, margin: 0, objectName: nm("path step") });
    if (i < path4.length - 1) s.addImage({ data: icons.arrow, x: x + 2.75, y: 4.72, w: 0.36, h: 0.36, objectName: nm("arrow") });
  });
  bullets(s, [
    "iPhone-only build, no encryption question, privacy policy and store text ready",
    "No Mac needed: Codemagic builds the app online and sends it to Apple",
    "The App Store copy needs no sign-in, so Apple's reviewer can go straight in",
  ], { x: 0.6, y: 5.55, w: 12.1, h: 1.3, fontSize: 15, paraSpaceAfter: 5 });

  // ===================== Closing =====================
  s = pres.addSlide({ masterName: "Title dark", sectionTitle: "Reports and ownership" });
  s.addText("Everything stays on your phone", { placeholder: "title" });
  s.addText("One place for the whole shift, from the first tick to the emailed report", { placeholder: "body" });
  const close = [["2", "apps, one set of work"], ["70", "manual topics"], ["4.57", "current version"]];
  close.forEach(([v, t], i) => {
    const x = 0.7 + i * 2.55;
    s.addText([{ text: v, options: { fontSize: 40, bold: true, color: C.accent1, breakLine: true } }, { text: t, options: { fontSize: 14, color: C.accent5 } }],
      { x, y: 5.0, w: 2.4, h: 1.3, valign: "top", isTextBox: true, margin: 0, objectName: nm("closing stat") });
  });
  phone(s, "overtime.png", 9.3, 0.7, 6.1);
  s.addText("© 2026 Tanner Tibbetts. All rights reserved.", { x: 0.7, y: 6.75, w: 7, h: 0.3, fontSize: 11, color: C.accent5, isTextBox: true, margin: 0, objectName: nm("copyright") });

  await pres.writeFile({ fileName: OUT });
  await applyTheme(OUT, THEME);
  console.log("wrote", OUT);
})().catch((e) => { console.error(e); process.exit(1); });
