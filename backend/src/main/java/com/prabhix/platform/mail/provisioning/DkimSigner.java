package com.prabhix.platform.mail.provisioning;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** RFC 6376 DKIM signing with {@code simple/simple} canonicalization. */
@Slf4j
public final class DkimSigner {

    private DkimSigner() {
    }

    public static String sign(Map<String, String> headers,
                              byte[] body,
                              PrivateKey privateKey,
                              String domain,
                              String selector) {
        String canonicalBody = canonicalizeBody(body);
        String bodyHash = base64Sha256(canonicalBody.getBytes(StandardCharsets.UTF_8));

        Map<String, String> normalized = new LinkedHashMap<>();
        headers.forEach((name, value) ->
                normalized.put(name.toLowerCase(Locale.ROOT), unfold(value)));

        List<String> headerNames = List.of(
                "from", "to", "subject", "date", "mime-version", "content-type");
        List<String> present = headerNames.stream()
                .filter(normalized::containsKey)
                .toList();

        long timestamp = Instant.now().getEpochSecond();
        String signatureHeaderName = "dkim-signature";
        String unsignedDkim = "v=1; a=rsa-sha256; c=simple/simple; d=" + domain
                + "; s=" + selector
                + "; t=" + timestamp
                + "; h=" + String.join(":", present)
                + "; bh=" + bodyHash
                + "; b=";

        StringBuilder canonical = new StringBuilder();
        for (String name : present) {
            canonical.append(name).append(':').append(normalized.get(name)).append("\r\n");
        }
        canonical.append(signatureHeaderName).append(':').append(unsignedDkim).append("\r\n");

        byte[] signatureBytes = signRsa(canonical.toString().getBytes(StandardCharsets.UTF_8), privateKey);
        return unsignedDkim + chunkBase64(Base64.getEncoder().encodeToString(signatureBytes));
    }

    public static PrivateKey loadPrivateKey(String pkcs8Base64) {
        try {
            byte[] encoded = Base64.getDecoder().decode(pkcs8Base64);
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load DKIM private key", ex);
        }
    }

    private static String canonicalizeBody(byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        if (text.endsWith("\n")) {
            while (text.endsWith("\n\n")) {
                text = text.substring(0, text.length() - 1);
            }
        }
        return text.replace("\n", "\r\n") + "\r\n";
    }

    private static String unfold(String value) {
        return value.replaceAll("\r\n[ \t]+", " ").trim();
    }

    private static String base64Sha256(byte[] data) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(data));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static byte[] signRsa(byte[] data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (Exception ex) {
            throw new IllegalStateException("DKIM RSA signing failed", ex);
        }
    }

    private static String chunkBase64(String encoded) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < encoded.length(); i += 73) {
            chunks.add(encoded.substring(i, Math.min(encoded.length(), i + 73)));
        }
        return chunks.stream().collect(Collectors.joining("\r\n "));
    }
}
