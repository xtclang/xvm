package org.xvm.asm.constants;

import java.lang.reflect.Field;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link TypeConstant}'s process-wide recursion diagnostic suppression state.
 */
public class TypeConstantRecursionDiagnosticsTest {
    @Test
    public void recursionDiagnosticSetIsConcurrent() throws Exception {
        var field = TypeConstant.class.getDeclaredField("s_setRecursions");
        field.setAccessible(true);

        var set = recursionSet(field);

        assertInstanceOf(ConcurrentHashMap.KeySetView.class, set);
        assertFalse(set.getClass().getName().contains("HashSet"));

        var prefix = "test-recursion-" + UUID.randomUUID() + '-';
        try {
            IntStream.range(0, 2_000).parallel().forEach(i ->
                    assertTrue(set.add(prefix + i)));
        } finally {
            set.removeIf(value -> value.startsWith(prefix));
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> recursionSet(Field field) throws IllegalAccessException {
        return (Set<String>) field.get(null);
    }
}
