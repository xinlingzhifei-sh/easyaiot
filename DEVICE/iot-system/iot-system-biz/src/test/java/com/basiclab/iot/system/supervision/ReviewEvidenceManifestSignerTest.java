package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.service.supervision.ReviewEvidenceManifestSigner;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewEvidenceManifestSignerTest {

    @Test
    void signsWithActiveKeyAndVerifiesRotatedKeyByVersionedTrustRoot() {
        String oldSecret = key("old-manifest-secret-material-0001");
        String activeSecret = key("new-manifest-secret-material-0002");
        ReviewEvidenceManifestSigner signer = new ReviewEvidenceManifestSigner(
                "key-2026-07",
                "key-2026-06=" + oldSecret + ",key-2026-07=" + activeSecret,
                "hmac-sha256-v1"
        );
        String manifestHash = "sha256:" + "a".repeat(64);

        Map<String, Object> signature = signer.sign(manifestHash, LocalDateTime.of(2026, 7, 11, 8, 0));

        assertEquals("HmacSHA256", signature.get("algorithm"));
        assertEquals("hmac-sha256-v1", signature.get("version"));
        assertEquals("key-2026-07", signature.get("keyId"));
        assertTrue(String.valueOf(signature.get("value")).matches("sha256:[0-9a-f]{64}"));
        assertTrue(signer.verify(signature, manifestHash).valid());

        ReviewEvidenceManifestSigner oldSigner = new ReviewEvidenceManifestSigner(
                "key-2026-06",
                "key-2026-06=" + oldSecret,
                "hmac-sha256-v1"
        );
        Map<String, Object> oldSignature = oldSigner.sign(manifestHash, LocalDateTime.of(2026, 6, 30, 8, 0));
        assertTrue(signer.verify(oldSignature, manifestHash).valid());
    }

    @Test
    void rejectsTamperedUnknownOrUnconfiguredSignatures() {
        ReviewEvidenceManifestSigner signer = new ReviewEvidenceManifestSigner(
                "key-1",
                "key-1=" + key("manifest-secret-material-for-key-one"),
                "hmac-sha256-v1"
        );
        String manifestHash = "sha256:" + "b".repeat(64);
        Map<String, Object> signature = signer.sign(manifestHash, LocalDateTime.of(2026, 7, 11, 8, 0));

        assertFalse(signer.verify(signature, "sha256:" + "c".repeat(64)).valid());
        Map<String, Object> unknownKey = new LinkedHashMap<>(signature);
        unknownKey.put("keyId", "unknown");
        assertEquals("unknown_signature_key", signer.verify(unknownKey, manifestHash).violation());

        ReviewEvidenceManifestSigner unavailable = new ReviewEvidenceManifestSigner("", "", "hmac-sha256-v1");
        assertThrows(IllegalStateException.class,
                () -> unavailable.sign(manifestHash, LocalDateTime.of(2026, 7, 11, 8, 0)));
        assertEquals("manifest_hmac_keyring_not_configured",
                unavailable.verify(signature, manifestHash).violation());
    }

    private static String key(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
