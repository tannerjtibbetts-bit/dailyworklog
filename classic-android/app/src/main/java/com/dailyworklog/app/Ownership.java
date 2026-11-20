package com.dailyworklog.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Who owns Daily Work Log, shown in the app and hidden in every report file, and the app's own mark.
 * The mark is a made-up code that exists only in this app: if it turns up in someone else's app or
 * files, they came from this one. The same constants are in the new app (mobile/lib/about/ownership.dart).
 */
final class Ownership {
    private Ownership() {}

    static final String OWNER = "Tanner Tibbetts";
    static final int YEAR = 2026;
    static final String NOTICE = "© " + YEAR + " " + OWNER + ". All rights reserved.";

    /** Shown at the bottom of the opening, sign-in and Support screens. */
    static final String CREDIT = "App created by " + OWNER + "\n" + NOTICE;

    /** The app's own mark. Never change it: old reports carry it. */
    static final String MARK = "DWL-PG9H-XMEU-PBZ4-HNFK";

    /** Hidden in every report file's properties (and as hidden text in PDFs). */
    static final String TAG = "Made with Daily Work Log. " + NOTICE + " " + MARK;

    /** A short, readable fingerprint of an owner key (its DER bytes), e.g. "3F9A-0C21-77BE-D410". */
    static String fingerprint(byte[] keyDer) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(keyDer);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (i > 0 && i % 2 == 0) sb.append('-');
                sb.append(String.format(Locale.ROOT, "%02X", h[i] & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Word and Excel files: the docProps part that carries the tag. title is the file's title. */
    static String coreXml(String title, String by, long at) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" "
                + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<dc:title>" + esc(title) + "</dc:title><dc:creator>" + esc(by == null ? "" : by) + "</dc:creator>"
                + "<cp:keywords>Daily Work Log " + MARK + "</cp:keywords><dc:description>" + esc(TAG) + "</dc:description>"
                + "<dcterms:created xsi:type=\"dcterms:W3CDTF\">" + f.format(new Date(at)) + "Z</dcterms:created></cp:coreProperties>";
    }

    static final String APP_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">"
            + "<Application>Daily Work Log</Application><Company>" + NOTICE + "</Company></Properties>";

    /** Lines to add to [Content_Types].xml and _rels/.rels for the two parts above. */
    static final String CONTENT_TYPES = "<Override PartName=\"/docProps/core.xml\" "
            + "ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
            + "<Override PartName=\"/docProps/app.xml\" "
            + "ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>";
    static final String RELS = "<Relationship Id=\"rIdCore\" "
            + "Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" "
            + "Target=\"docProps/core.xml\"/><Relationship Id=\"rIdApp\" "
            + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" "
            + "Target=\"docProps/app.xml\"/>";

    /** Writes docProps/core.xml and docProps/app.xml (the hidden tag) into a Word or Excel file. */
    static void putParts(ZipOutputStream zip, String title, String by) throws IOException {
        zip.putNextEntry(new ZipEntry("docProps/core.xml"));
        zip.write(coreXml(title, by, System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.putNextEntry(new ZipEntry("docProps/app.xml"));
        zip.write(APP_XML.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
