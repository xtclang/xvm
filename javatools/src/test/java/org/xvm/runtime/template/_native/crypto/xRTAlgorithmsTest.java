package org.xvm.runtime.template._native.crypto;


import java.security.Key;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.xvm.runtime.template._native.crypto.xRTAlgorithms.KeyForm.PrivateOrSecret;


/**
 * Tests for {@link xRTAlgorithms}.
 */
public class xRTAlgorithmsTest {
    @Test
    public void testExtractRawAesKey() throws Exception {
        byte[] abRaw = new byte[32];

        for (String sAlgorithm : new String[] {
                "AES", "AES/CBC/PKCS5Padding", "AES/ECB/PKCS5Padding"}) {
            Key key = xRTAlgorithms.extractKey(abRaw, sAlgorithm, PrivateOrSecret);

            assertEquals("AES", key.getAlgorithm());
            assertArrayEquals(abRaw, key.getEncoded());
        }
    }
}
