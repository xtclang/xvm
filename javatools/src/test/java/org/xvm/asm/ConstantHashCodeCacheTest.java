package org.xvm.asm;


import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Guards the cached-hash publication contract for constants.
 */
public class ConstantHashCodeCacheTest {
    /**
     * Constant hash caching is lazy mutable state used by hash maps. It must be safely published so
     * reentrant or parallel readers do not see stale or inconsistent hash state.
     */
    @Test
    public void cachedHashIsVolatile() throws NoSuchFieldException {
        assertTrue(Modifier.isVolatile(Constant.class.getDeclaredField("m_iHash").getModifiers()));
    }
}
