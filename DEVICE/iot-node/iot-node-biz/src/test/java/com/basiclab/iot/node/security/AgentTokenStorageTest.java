package com.basiclab.iot.node.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTokenStorageTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    @Test
    void hashIsSaltedAndVerifiesOnlyTheOriginalToken() {
        String first = AgentTokenHasher.hash(TOKEN);
        String second = AgentTokenHasher.hash(TOKEN);

        assertTrue(first.startsWith("hs256$"));
        assertFalse(first.contains(TOKEN));
        assertNotEquals(first, second, "每次哈希使用独立随机盐");
        assertTrue(AgentTokenHasher.matches(first, TOKEN));
        assertTrue(AgentTokenHasher.matches(second, TOKEN));
        assertFalse(AgentTokenHasher.matches(first, TOKEN.substring(1)));
        assertFalse(AgentTokenHasher.matches(first, TOKEN + "0"));
    }

    @Test
    void hashMatchFailsClosedOnBlankOrMalformedInput() {
        String hash = AgentTokenHasher.hash(TOKEN);
        assertFalse(AgentTokenHasher.matches(null, TOKEN));
        assertFalse(AgentTokenHasher.matches(hash, ""));
        assertFalse(AgentTokenHasher.matches(hash, null));
        assertFalse(AgentTokenHasher.matches("hs256$only-two", TOKEN));
        assertFalse(AgentTokenHasher.matches("md5$abc$def", TOKEN));
        assertFalse(AgentTokenHasher.matches("hs256$!!$!!", TOKEN));
        // 旧版明文直接放进哈希列也不能当成匹配
        assertFalse(AgentTokenHasher.matches(TOKEN, TOKEN));
        assertThrows(IllegalArgumentException.class, () -> AgentTokenHasher.hash(" "));
    }

    @Test
    void cipherRoundTripsWithRandomIvAndNeverStoresPlaintext() {
        AgentTokenCipher cipher = AgentTokenCipher.fromSecret("deployment-secret");
        String first = cipher.encrypt(TOKEN);
        String second = cipher.encrypt(TOKEN);

        assertTrue(first.startsWith("gcm1$"));
        assertFalse(first.contains(TOKEN));
        assertNotEquals(first, second);
        assertEquals(TOKEN, cipher.decrypt(first));
        assertEquals(TOKEN, AgentTokenCipher.fromSecret("deployment-secret").decrypt(second));
    }

    @Test
    void cipherRejectsWrongKeyTamperingAndUnknownFormat() {
        String sealed = AgentTokenCipher.fromSecret("deployment-secret").encrypt(TOKEN);
        AgentTokenCipher other = AgentTokenCipher.fromSecret("rotated-secret");

        assertThrows(IllegalStateException.class, () -> other.decrypt(sealed));
        char last = sealed.charAt(sealed.length() - 2);
        String tampered = sealed.substring(0, sealed.length() - 2) + (last == 'A' ? 'B' : 'A') + sealed.charAt(sealed.length() - 1);
        assertThrows(IllegalStateException.class,
                () -> AgentTokenCipher.fromSecret("deployment-secret").decrypt(tampered));
        assertThrows(IllegalStateException.class,
                () -> AgentTokenCipher.fromSecret("deployment-secret").decrypt("plain$" + TOKEN));
    }

    @Test
    void cipherIsUnavailableWithoutSecret() {
        assertNull(AgentTokenCipher.fromSecret(""));
        assertNull(AgentTokenCipher.fromSecret(null));
    }
}
