package com.basiclab.iot.system.service.supervision;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class ReviewEvidenceManifestSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern SHA256 = Pattern.compile("sha256:[0-9a-f]{64}");

    private final String activeKeyId;
    private final Map<String, byte[]> keyring;
    private final String signatureVersion;

    public ReviewEvidenceManifestSigner(
            @Value("${yfeieye.review.evidence-manifest.active-key-id:}") String activeKeyId,
            @Value("${yfeieye.review.evidence-manifest.hmac-keys:}") String encodedKeyring,
            @Value("${yfeieye.review.evidence-manifest.signature-version:hmac-sha256-v1}") String signatureVersion) {
        this.activeKeyId = normalize(activeKeyId);
        this.keyring = parseKeyring(encodedKeyring);
        this.signatureVersion = normalize(signatureVersion);
    }

    public Map<String, Object> sign(String manifestHash, LocalDateTime signedAt) {
        byte[] key = activeSigningKey();
        requireManifestHash(manifestHash);
        Map<String, Object> signature = new LinkedHashMap<>();
        signature.put("algorithm", ALGORITHM);
        signature.put("version", signatureVersion);
        signature.put("keyId", activeKeyId);
        signature.put("signedAt", signedAt == null ? null : signedAt.toString());
        signature.put("value", hmacValue(key, signatureVersion, activeKeyId, manifestHash));
        signature.values().removeIf(value -> value == null);
        return Map.copyOf(signature);
    }

    public Verification verify(Map<String, Object> signature, String manifestHash) {
        if (keyring.isEmpty()) {
            return Verification.invalid("manifest_hmac_keyring_not_configured");
        }
        if (signature == null || signature.isEmpty()) {
            return Verification.invalid("missing_signature");
        }
        if (!ALGORITHM.equals(text(signature.get("algorithm")))) {
            return Verification.invalid("unsupported_signature_algorithm");
        }
        String version = text(signature.get("version"));
        if (!signatureVersion.equals(version)) {
            return Verification.invalid("unsupported_signature_version");
        }
        String keyId = text(signature.get("keyId"));
        byte[] key = keyring.get(keyId);
        if (key == null) {
            return Verification.invalid("unknown_signature_key");
        }
        if (!SHA256.matcher(normalize(manifestHash)).matches()) {
            return Verification.invalid("invalid_manifest_hash");
        }
        String actual = text(signature.get("value"));
        String expected = hmacValue(key, version, keyId, manifestHash);
        if (actual == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII))) {
            return Verification.invalid("signature_mismatch");
        }
        return new Verification(true, null);
    }

    private byte[] activeSigningKey() {
        if (keyring.isEmpty()) {
            throw new IllegalStateException("evidence manifest HMAC keyring is not configured");
        }
        byte[] key = keyring.get(activeKeyId);
        if (key == null) {
            throw new IllegalStateException("evidence manifest active HMAC key is not configured: " + activeKeyId);
        }
        if (signatureVersion.isEmpty()) {
            throw new IllegalStateException("evidence manifest signature version is not configured");
        }
        return key;
    }

    private static Map<String, byte[]> parseKeyring(String encodedKeyring) {
        String normalized = normalize(encodedKeyring);
        if (normalized.isEmpty()) {
            return Map.of();
        }
        Map<String, byte[]> parsed = new LinkedHashMap<>();
        for (String rawEntry : normalized.split(",")) {
            String entry = rawEntry.trim();
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                throw new IllegalArgumentException("invalid evidence manifest HMAC key entry");
            }
            String keyId = entry.substring(0, separator).trim();
            if (!KEY_ID.matcher(keyId).matches()) {
                throw new IllegalArgumentException("invalid evidence manifest HMAC key id: " + keyId);
            }
            byte[] secret;
            try {
                secret = Base64.getDecoder().decode(entry.substring(separator + 1).trim());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("invalid base64 evidence manifest HMAC key: " + keyId, exception);
            }
            if (secret.length < 32) {
                throw new IllegalArgumentException("evidence manifest HMAC key must be at least 32 bytes: " + keyId);
            }
            if (parsed.putIfAbsent(keyId, secret.clone()) != null) {
                throw new IllegalArgumentException("duplicate evidence manifest HMAC key id: " + keyId);
            }
        }
        return Map.copyOf(parsed);
    }

    private static String hmacValue(byte[] key, String version, String keyId, String manifestHash) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            byte[] digest = mac.doFinal(("yfeieye-evidence-manifest\n"
                    + version + "\n" + keyId + "\n" + manifestHash).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("unable to sign evidence manifest", exception);
        }
    }

    private static void requireManifestHash(String manifestHash) {
        if (!SHA256.matcher(normalize(manifestHash)).matches()) {
            throw new IllegalArgumentException("manifestHash must be a sha256 digest");
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record Verification(boolean valid, String violation) {
        private static Verification invalid(String violation) {
            return new Verification(false, violation);
        }
    }
}
