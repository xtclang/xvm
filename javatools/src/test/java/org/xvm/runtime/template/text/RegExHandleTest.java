package org.xvm.runtime.template.text;


import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import org.xvm.runtime.template.text.xRegEx.RegExHandle;

import org.xvm.util.Lazy;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class RegExHandleTest {
    @Test
    public void compiledPatternIsCachedPerHandle() {
        RegExHandle handle = new RegExHandle(null, "a+", 0);

        Pattern pattern = handle.getPattern();

        assertSame(pattern, handle.getPattern());
        assertTrue(pattern.matcher("aaa").matches());
    }

    @Test
    public void compiledPatternCacheIsFinalLazy() throws Exception {
        Field field = RegExHandle.class.getDeclaredField("pattern");

        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Lazy.class.isAssignableFrom(field.getType()));
        assertThrows(NoSuchFieldException.class,
                () -> RegExHandle.class.getDeclaredField("m_pattern"));
    }
}
