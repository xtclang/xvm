package org.xvm.asm;


import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Guards the cached-hash publication contract for constants.
 */
public class ConstantHashCodeCacheTest {
    @Test
    public void cachedHashIsVolatile() throws NoSuchFieldException {
        assertTrue(Modifier.isVolatile(Constant.class.getDeclaredField("m_iHash").getModifiers()));
    }
}
