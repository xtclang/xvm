package org.xvm.asm;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import org.xvm.util.ListMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the read-only accessor contract (array element exposure audit, stage 1). Eight getters
 * used the {@code assert (x = Collections.unmodifiableX(x)) != null;} idiom, so their read-only
 * wrapper existed only when assertions were enabled: under {@code -da} - the normal production
 * shape - callers received the live backing collection of the component tree, the module map, or
 * the ListMap entry storage, and a "read-only" caller could mutate structure state directly. The
 * wrappers are now unconditional; these tests hold with or without {@code -ea}, and the shape pin
 * keeps the assertion-dependent idiom from returning.
 */
public class ReadOnlyViewContractTest {
    /**
     * Mutating through a read-only accessor must throw regardless of the JVM's assertion
     * setting. Red on the old shape whenever assertions are disabled.
     */
    @Test
    public void readOnlyAccessorsRejectMutationWithoutAssertions() {
        var file = new FileStructure("test");
        assertThrows(UnsupportedOperationException.class,
                () -> file.moduleIds().clear(),
                "FileStructure.moduleIds must never hand out the live module-map keySet");

        var module = file.getModule();
        var clz    = module.createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Test", null);
        assertThrows(UnsupportedOperationException.class,
                () -> module.children().clear(),
                "Component.children must never hand out the live child-map values view");

        var pool   = file.getConstantPool();
        var method = clz.createMethod(false, Constants.Access.PUBLIC, null,
                Parameter.NO_PARAMS, "name", Parameter.NO_PARAMS, true, false);
        var multi  = (MultiMethodStructure) method.getParent();
        assertThrows(UnsupportedOperationException.class,
                () -> multi.methods().clear(),
                "MultiMethodStructure.methods must never hand out the live method-map values view");
        assertEquals(1, multi.methods().size());

        var map = new ListMap<String, String>();
        map.put("key", "value");
        assertThrows(UnsupportedOperationException.class,
                () -> map.asList().remove(0),
                "ListMap.asList must never hand out the live entry storage");
        assertEquals(1, map.size());
    }

    /**
     * The assertion-dependent wrapper idiom must not return anywhere in main sources. Red on
     * master, which had eight hits across Component, CompositeComponent, MultiMethodStructure,
     * FileStructure, ModuleStructure, and ListMap.
     */
    @Test
    public void assertionDependentWrapperIdiomIsBanished() throws IOException {
        var pattern = Pattern.compile("assert\\s*\\(\\w+\\s*=\\s*Collections\\.unmodifiable");
        for (var root : List.of(mainSourceRoot("javatools"), mainSourceRoot("javatools_utils"))) {
            try (var paths = Files.walk(root)) {
                for (var path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                    assertTrue(!pattern.matcher(Files.readString(path)).find(),
                            "assertion-dependent read-only wrapper in " + path
                                    + "; make the wrapper unconditional instead");
                }
            }
        }
    }

    // ----- helpers -------------------------------------------------------------------------------

    private static Path mainSourceRoot(String build) {
        var dir = Path.of(".").toAbsolutePath().normalize();
        while (dir != null) {
            var candidate = dir.resolve(build).resolve("src/main/java");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("cannot locate main source root for: " + build);
    }
}
