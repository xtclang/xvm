package org.xtclang.plugin.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.List;

import org.gradle.testfixtures.ProjectBuilder;

import org.junit.jupiter.api.Test;

class DefaultXtcRunModuleTest {
    @Test
    void equalModuleNamesHaveEqualHashes() {
        final var objects = ProjectBuilder.builder().build().getObjects();
        final var first = objects.newInstance(DefaultXtcRunModule.class);
        final var second = objects.newInstance(DefaultXtcRunModule.class);
        first.getModuleName().set("Example");
        second.getModuleName().set("Example");
        second.getMethodName().set("verify");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(1, new HashSet<>(List.of(first, second)).size());
    }

    @Test
    void unconfiguredModulesKeepDistinctIdentity() {
        final var objects = ProjectBuilder.builder().build().getObjects();
        final var first = objects.newInstance(DefaultXtcRunModule.class);
        final var second = objects.newInstance(DefaultXtcRunModule.class);

        assertEquals(first, first);
        assertNotEquals(first, second);
        assertEquals(2, new HashSet<>(List.of(first, second)).size());
    }
}
