package org.xvm.runtime;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * Tests for the native-template owner lookup table.
 */
public class NativeTemplatesTest {
    @Test
    public void rejectsNullContainer() {
        assertThrows(NullPointerException.class, () -> NativeTemplates.get((Container) null));
    }

    @Test
    public void rejectsNullFrame() {
        assertThrows(NullPointerException.class, () -> NativeTemplates.get((Frame) null));
    }

    @Test
    public void rejectsNullTemplate() {
        assertThrows(NullPointerException.class, () -> NativeTemplates.get((ClassTemplate) null));
    }

    @Test
    public void rejectsTemplateWithNullOwner() {
        assertThrows(NullPointerException.class, () -> NativeTemplates.get(new NullOwnerTemplate()));
    }

    @Test
    public void rejectsNullOwnerAtConstruction() {
        assertThrows(NullPointerException.class, () -> new NativeTemplates(null));
    }

    private static class NullOwnerTemplate
            extends ClassTemplate {
        NullOwnerTemplate() {
            super(null, null);
        }
    }
}
