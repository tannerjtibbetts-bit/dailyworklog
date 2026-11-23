package com.dailyworklog.app;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ownership proof: a statement signed on the owner's phone with the app's owner key (the same key
 * that approves people). Only the phone holding that key can make one, and anyone can check it, so it
 * proves who owns Daily Work Log. Same format as the new app (mobile/lib/access/ownership_proof.dart),
 * so a proof made in one app checks in the other. See Support > Ownership proof.
 */
final class OwnershipProof {
    private OwnershipProof() {}

    static final String PREFIX = "DWL-PROOF-";
    private static final Pattern CODE =
            Pattern.compile("DWL-PROOF-([A-Za-z0-9_-]+)~([A-Za-z0-9_-]+)~([A-Za-z0-9_-]+)(?:~([A-Za-z0-9_.*-]+))?");

    /** What checking a proof found. */
    static final class Result {
        /** The signature is good: it was made with the owner key shown by fingerprint. */
        final boolean genuine;
        final String name, at, fingerprint;
        /** Whether that key is the one built into this copy of the app (null: this copy has none). */
        final Boolean sameAsThisApp;

        Result(boolean genuine, String name, String at, String fingerprint, Boolean sameAsThisApp) {
            this.genuine = genuine;
            this.name = name;
            this.at = at;
            this.fingerprint = fingerprint;
            this.sameAsThisApp = sameAsThisApp;
        }
    }

    /** What gets signed. */
    static byte[] payload(String name, String at) {
        return ("v1|proof|" + name.trim() + "|" + at + "|" + Ownership.MARK).getBytes(StandardCharsets.UTF_8);
    }

    /** The fingerprint of an owner key, e.g. "3F9A-0C21-77BE-D410". */
    static String fingerprint(PublicKey k) {
        return Ownership.fingerprint(k.getEncoded());
    }

    private static String b64url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /**
     * The proof as text to send or keep. root is the original owner key built into the app, chain the
     * handovers from it to the key that signed ("" for the original owner).
     */
    static String text(String name, String at, byte[] signature, PublicKey root, String chain) {
        String code = PREFIX + b64url(payload(name, at)) + "~" + b64url(signature) + "~" + AccessCodes.keyUrl(root)
                + (chain == null || chain.isEmpty() ? "" : "~" + chain);
        return "Daily Work Log ownership proof\n"
                + "Owner: " + name.trim() + "\n"
                + "Signed: " + at + "\n"
                + "Owner key fingerprint: " + fingerprint(root) + "\n"
                + "App mark: " + Ownership.MARK + "\n"
                + Ownership.NOTICE + "\n\n"
                + "This was signed on the app owner's phone with the owner key built into the owner's copies of "
                + "Daily Work Log. Only that phone can make it. To check it: open Daily Work Log, tap Support, "
                + "then Ownership proof, and paste this whole text under \"Check an ownership proof\".\n\n"
                + code;
    }

    /** Checks a proof pasted from anywhere. Null if there is no proof in text. */
    static Result check(String text, PublicKey builtIn) {
        Matcher m = CODE.matcher(text == null ? "" : text);
        if (!m.find()) return null;
        try {
            Base64.Decoder d = Base64.getUrlDecoder();
            byte[] data = d.decode(m.group(1));
            String[] parts = new String(data, StandardCharsets.UTF_8).split("\\|", -1);
            PublicKey root = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(d.decode(m.group(3))));
            if (parts.length != 5 || !parts[0].equals("v1") || !parts[1].equals("proof")) return null;
            PublicKey signer = AccessCodes.chainEnd(root, m.group(4));
            boolean good = false;
            if (signer != null && parts[4].equals(Ownership.MARK)) {
                try {
                    Signature s = Signature.getInstance("SHA256withECDSA");
                    s.initVerify(signer);
                    s.update(data);
                    good = s.verify(d.decode(m.group(2)));
                } catch (Exception e) {
                    good = false;
                }
            }
            return new Result(good, parts[2], parts[3], fingerprint(root),
                    builtIn == null ? null : Arrays.equals(builtIn.getEncoded(), root.getEncoded()));
        } catch (Exception e) {
            return null;
        }
    }
}
