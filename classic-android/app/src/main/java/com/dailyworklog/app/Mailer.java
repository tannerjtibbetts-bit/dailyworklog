package com.dailyworklog.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Sends email straight from the app through Gmail (SMTP over TLS), so load securement photos
 * go out the moment Done is tapped, without opening a mail app. Plain Java, no Android parts.
 */
final class Mailer {
    private Mailer() {}

    static final String GMAIL_HOST = "smtp.gmail.com";
    static final int GMAIL_PORT = 465;
    /** Attachments per email are kept under this, so Gmail's 25 MB limit isn't hit after encoding. */
    static final long MAX_GROUP_BYTES = 18L * 1024 * 1024;
    private static final int TIMEOUT_MS = 30000;

    static final String OFFLINE = "No internet connection, or Gmail didn't answer. Try again when you have signal.";
    static final String BAD_LOGIN = "Gmail didn't accept the address or app password. Check them in Email sending.";

    static final class Attachment {
        final String name;
        final byte[] bytes;

        Attachment(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }

    /** A sending problem, with a message in plain words for the person. */
    static final class MailException extends Exception {
        MailException(String message) {
            super(message);
        }
    }

    /** "a@x.com, b@y.com" -> [a@x.com, b@y.com]. Also splits on ";" and spaces. */
    static List<String> addresses(String typed) {
        List<String> out = new ArrayList<>();
        for (String s : typed.split("[,;\\s]+")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    static boolean validAddress(String a) {
        return a.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    /** Every address looks like an email address, and there's at least one. */
    static boolean validList(String typed) {
        List<String> a = addresses(typed);
        if (a.isEmpty()) return false;
        for (String s : a) if (!validAddress(s)) return false;
        return true;
    }

    /** Splits attachments into groups of at most limit bytes (a bigger single photo gets its own group). */
    static List<List<Attachment>> groups(List<Attachment> files, long limit) {
        List<List<Attachment>> out = new ArrayList<>();
        List<Attachment> cur = new ArrayList<>();
        long size = 0;
        for (Attachment f : files) {
            if (!cur.isEmpty() && size + f.bytes.length > limit) {
                out.add(cur);
                cur = new ArrayList<>();
                size = 0;
            }
            cur.add(f);
            size += f.bytes.length;
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }

    private static boolean ascii(String s) {
        return s.matches("^[\\x20-\\x7E]*$");
    }

    /** A header value: as is if plain ASCII, otherwise RFC 2047 encoded. */
    static String encodeHeader(String s) {
        if (ascii(s)) return s;
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    /** Base64 in 76-character lines, each ending CRLF. */
    static String base64Lines(byte[] bytes) {
        String b = Base64.getEncoder().encodeToString(bytes);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < b.length(); i += 76) out.append(b, i, Math.min(i + 76, b.length())).append("\r\n");
        return out.toString();
    }

    static String newBoundary() {
        return "dwl-" + Long.toHexString(new Random().nextLong() & 0xFFFFFFFFL) + System.nanoTime();
    }

    /** The whole email (headers and body) with CRLF line endings. */
    static String buildMessage(String fromName, String from, List<String> to, String subject, String body,
                               List<Attachment> attachments, String boundary, Date date) {
        String bnd = boundary == null ? newBoundary() : boundary;
        SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        String name = fromName.trim();
        String fromHeader = name.isEmpty() ? "<" + from + ">"
                : ascii(name) ? "\"" + name.replace('"', '\'') + "\" <" + from + ">"
                : encodeHeader(name) + " <" + from + ">";
        StringBuilder s = new StringBuilder()
                .append("From: ").append(fromHeader).append("\r\n")
                .append("To: ").append(String.join(", ", to)).append("\r\n")
                .append("Subject: ").append(encodeHeader(subject)).append("\r\n")
                .append("Date: ").append(df.format(date == null ? new Date() : date)).append(" +0000\r\n")
                .append("MIME-Version: 1.0\r\n")
                .append("Content-Type: multipart/mixed; boundary=\"").append(bnd).append("\"\r\n")
                .append("\r\n")
                .append("--").append(bnd).append("\r\n")
                .append("Content-Type: text/plain; charset=UTF-8\r\n")
                .append("Content-Transfer-Encoding: base64\r\n\r\n")
                .append(base64Lines(body.replace("\r\n", "\n").replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8)));
        if (attachments != null) {
            for (Attachment a : attachments) {
                String n = a.name.replace("\"", "");
                s.append("--").append(bnd).append("\r\n")
                        .append("Content-Type: image/jpeg; name=\"").append(n).append("\"\r\n")
                        .append("Content-Disposition: attachment; filename=\"").append(n).append("\"\r\n")
                        .append("Content-Transfer-Encoding: base64\r\n\r\n")
                        .append(base64Lines(a.bytes));
            }
        }
        s.append("--").append(bnd).append("--\r\n");
        return s.toString();
    }

    /** An SMTP reply that wasn't the one expected. */
    private static final class Reply extends Exception {
        final int code;
        final String text;

        Reply(int code, String text) {
            super(text);
            this.code = code;
            this.text = text;
        }
    }

    /** Sends one message through Gmail. */
    static void send(String user, String password, List<String> to, String message) throws MailException {
        send(GMAIL_HOST, GMAIL_PORT, true, user, password, to, message, TIMEOUT_MS);
    }

    /** Sends one message. useSsl false is only for tests against a local server. */
    static void send(String host, int port, boolean useSsl, String user, String password, List<String> to,
                     String message, int timeoutMs) throws MailException {
        Socket socket = null;
        try {
            try {
                Socket plain = new Socket();
                plain.connect(new InetSocketAddress(host, port), timeoutMs);
                plain.setSoTimeout(timeoutMs);
                socket = plain;
                if (useSsl) {
                    SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                            .createSocket(plain, host, port, true);
                    SSLParameters params = ssl.getSSLParameters();
                    params.setEndpointIdentificationAlgorithm("HTTPS");  // check it's really Gmail
                    ssl.setSSLParameters(params);
                    ssl.setSoTimeout(timeoutMs);
                    ssl.startHandshake();
                    socket = ssl;
                }
            } catch (IOException e) {
                throw new MailException(OFFLINE);
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();
            expect(in, out, null, 220);
            expect(in, out, "EHLO dailyworklog", 250);
            expect(in, out, "AUTH LOGIN", 334);
            expect(in, out, b64(user), 334);
            try {
                expect(in, out, b64(password), 235);
            } catch (Reply r) {
                throw new MailException(r.code == 535 || r.code == 534 ? BAD_LOGIN : "Gmail said: " + r.text);
            }
            expect(in, out, "MAIL FROM:<" + user + ">", 250);
            for (String a : to) {
                try {
                    expect(in, out, "RCPT TO:<" + a + ">", 250, 251);
                } catch (Reply r) {
                    throw new MailException(r.code >= 550 && r.code < 560 ? "Gmail wouldn't send to " + a + "." : "Gmail said: " + r.text);
                }
            }
            expect(in, out, "DATA", 354);
            // Lines starting with "." get an extra "." (SMTP's rule); base64 never starts with one.
            StringBuilder data = new StringBuilder();
            for (String l : message.split("\r\n", -1)) data.append(l.startsWith(".") ? "." + l : l).append("\r\n");
            String d = data.toString();
            if (message.endsWith("\r\n")) d = d.substring(0, d.length() - 2);
            out.write(d.getBytes(StandardCharsets.UTF_8));
            expect(in, out, ".", 250);
            try {
                out.write("QUIT\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
            } catch (IOException ignored) {
                // sent already
            }
        } catch (Reply r) {
            throw new MailException("Gmail said: " + r.text);
        } catch (IOException e) {
            throw new MailException(OFFLINE);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // closing anyway
                }
            }
        }
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    /** Sends a command (if any) and reads the whole reply, joining "250-..." lines. */
    private static void expect(BufferedReader in, OutputStream out, String command, Integer... codes)
            throws IOException, Reply, MailException {
        if (command != null) {
            out.write((command + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
        List<String> parts = new ArrayList<>();
        String line;
        while (true) {
            line = in.readLine();
            if (line == null) throw new MailException(OFFLINE);
            parts.add(line.length() > 4 ? line.substring(4) : "");
            if (line.length() < 4 || line.charAt(3) != '-') break;
        }
        int code;
        try {
            code = Integer.parseInt(line.substring(0, Math.min(3, line.length())));
        } catch (NumberFormatException e) {
            code = 0;
        }
        String text = (line.substring(0, Math.min(3, line.length())) + " " + String.join(" ", parts)).trim();
        if (!Arrays.asList(codes).contains(code)) throw new Reply(code, text);
    }
}
