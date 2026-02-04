package org.xvm.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Scope class.
 */
public class ScopeTest {

    @Test
    public void testOn() {
        String value = "test";
        Scope<String> scope = Scope.on(value);
        assertEquals("test", scope.get());
    }

    @Test
    public void testOnNull() {
        Scope<String> scope = Scope.on(null);
        assertNull(scope.get());
    }

    @Test
    public void testLet() {
        String result = Scope.on("hello")
                .let(s -> s.toUpperCase());
        assertEquals("HELLO", result);
    }

    @Test
    public void testLetTransform() {
        int length = Scope.on("hello")
                .let(String::length);
        assertEquals(5, length);
    }

    @Test
    public void testAlso() {
        List<String> sideEffects = new ArrayList<>();

        String result = Scope.on("test")
                .also(s -> sideEffects.add(s))
                .get();

        assertEquals("test", result);
        assertEquals(List.of("test"), sideEffects);
    }

    @Test
    public void testAlsoChaining() {
        List<String> log = new ArrayList<>();

        String result = Scope.on("value")
                .also(s -> log.add("first: " + s))
                .also(s -> log.add("second: " + s))
                .get();

        assertEquals("value", result);
        assertEquals(2, log.size());
        assertEquals("first: value", log.get(0));
        assertEquals("second: value", log.get(1));
    }

    @Test
    public void testApply() {
        StringBuilder sb = Scope.on(new StringBuilder())
                .apply(b -> b.append("hello"))
                .apply(b -> b.append(" "))
                .apply(b -> b.append("world"))
                .get();

        assertEquals("hello world", sb.toString());
    }

    @Test
    public void testApplyReturnsScope() {
        Scope<StringBuilder> scope = Scope.on(new StringBuilder());
        Scope<StringBuilder> returned = scope.apply(b -> b.append("x"));
        assertSame(scope, returned);
    }

    @Test
    public void testRun() {
        String result = Scope.on("hello")
                .run(s -> s + " world");
        assertEquals("hello world", result);
    }

    @Test
    public void testStaticWith() {
        String result = Scope.with("hello", s -> s.toUpperCase());
        assertEquals("HELLO", result);
    }

    @Test
    public void testStaticRun() {
        String result = Scope.run(() -> "computed");
        assertEquals("computed", result);
    }

    @Test
    public void testLetIfPresent() {
        Optional<Integer> present = Scope.on("hello")
                .letIfPresent(String::length);
        assertEquals(Optional.of(5), present);

        Optional<Integer> absent = Scope.on((String) null)
                .letIfPresent(String::length);
        assertEquals(Optional.empty(), absent);
    }

    @Test
    public void testAlsoIfPresent() {
        List<String> log = new ArrayList<>();

        // With non-null value
        Scope.on("value")
                .alsoIfPresent(s -> log.add(s))
                .get();
        assertEquals(List.of("value"), log);

        // With null value - should not execute
        log.clear();
        Scope.on((String) null)
                .alsoIfPresent(s -> log.add(s))
                .get();
        assertTrue(log.isEmpty());
    }

    @Test
    public void testApplyIfPresent() {
        List<String> log = new ArrayList<>();

        // With non-null value
        Scope.on(log)
                .applyIfPresent(l -> l.add("added"))
                .get();
        assertEquals(List.of("added"), log);

        // With null value - should not execute
        List<String> nullList = null;
        Scope.on(nullList)
                .applyIfPresent(l -> l.add("should not happen"));
        // No exception thrown
    }

    @Test
    public void testToOptional() {
        Optional<String> present = Scope.on("value").toOptional();
        assertEquals(Optional.of("value"), present);

        Optional<String> absent = Scope.on((String) null).toOptional();
        assertEquals(Optional.empty(), absent);
    }

    @Test
    public void testRequireNonNull() {
        String result = Scope.on("value").requireNonNull();
        assertEquals("value", result);
    }

    @Test
    public void testRequireNonNullThrows() {
        assertThrows(NullPointerException.class, () ->
                Scope.on((String) null).requireNonNull());
    }

    @Test
    public void testToString() {
        assertEquals("Scope[hello]", Scope.on("hello").toString());
        assertEquals("Scope[null]", Scope.on(null).toString());
        assertEquals("Scope[123]", Scope.on(123).toString());
    }

    @Test
    public void testFluentChaining() {
        // Example from documentation
        StringBuilder result = Scope.on(new StringBuilder())
                .apply(sb -> sb.append("Hello"))
                .also(sb -> System.out.println("After append: " + sb))
                .apply(sb -> sb.append(" World"))
                .get();

        assertEquals("Hello World", result.toString());
    }

    @Test
    public void testComplexChaining() {
        List<String> log = new ArrayList<>();

        String result = Scope.on("  hello world  ")
                .let(s -> Scope.on(s.trim())
                        .also(x -> log.add("trimmed: " + x))
                        .let(String::toUpperCase));

        assertEquals("HELLO WORLD", result);
        assertEquals(List.of("trimmed: hello world"), log);
    }
}