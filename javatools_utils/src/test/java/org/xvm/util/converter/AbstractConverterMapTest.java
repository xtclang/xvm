package org.xvm.util.converter;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AbstractConverterMap}.
 */
public class AbstractConverterMapTest {
    @Test
    void shouldNotCallOverridableViewFactoriesDuringConstruction() {
        ConstructorSensitiveMap map = new ConstructorSensitiveMap();

        Set<String> keys = map.keySet();
        Collection<String> values = map.values();
        Set<Entry<String, String>> entries = map.entrySet();

        assertNotSame(keys, map.keySet());
        assertNotSame(values, map.values());
        assertNotSame(entries, map.entrySet());

        map.put("hello", "world");
        assertTrue(keys.contains("hello"));
        assertTrue(values.contains("world"));
        assertEquals("world", entries.iterator().next().getValue());
    }

    /**
     * Fails deterministically on the old implementation, because the base constructor calls the
     * overridden view factories before this class initializes {@link #ready}.
     */
    private static final class ConstructorSensitiveMap
            extends AbstractConverterMap<String, String, String, String> {
        private final String ready;

        private ConstructorSensitiveMap() {
            super(new HashMap<>());
            ready = "ready";
        }

        @Override
        protected String keyDown(String key) {
            return key;
        }

        @Override
        protected String keyUp(String key) {
            return key;
        }

        @Override
        protected String valueDown(String value) {
            return value;
        }

        @Override
        protected String valueUp(String value) {
            return value;
        }

        @Override
        protected Set<String> newKeySet() {
            assertReady();
            return super.newKeySet();
        }

        @Override
        protected Collection<String> newValues() {
            assertReady();
            return super.newValues();
        }

        @Override
        protected Set<Entry<String, String>> newEntrySet() {
            assertReady();
            return super.newEntrySet();
        }

        private void assertReady() {
            if (!"ready".equals(ready)) {
                throw new IllegalStateException("view created before subclass initialization");
            }
        }
    }
}
