package org.xvm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import java.net.URISyntaxException;

import java.nio.file.Files;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.DeclarationDigests;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleStructure;

/**
 * Each declaration of a module has a digest of its own API ({@link DeclarationDigests}): a change
 * that leaves a declaration's API as it was leaves its digest, whatever else changes in the module,
 * and a change of a declaration's API changes its digest alone; and no constant or component of the
 * XDK's ecstasy writes a constant that it does not register, so every digest is reproducible.
 */
class DeclarationDigestsTest {
    private static final String LIBRARY = """
            module Library {
                String describe(Int n, Int base = 1) {
                    return n > base ? "positive" : "other";
                }

                interface Shape {
                    Int area();
                }

                class Square(Int side)
                        implements Shape {
                    @Override
                    Int area() = side * side;

                    <Value> Value pick(Value first, Value second) = first;
                }

                static Int Limit = 10;
            }
            """;

    private EmbeddingSupport support;

    @BeforeEach
    void configure() throws URISyntaxException {
        var modules = DeclarationDigestsTest.class.getResource("/xdk-modules");
        assertNotNull(modules, "the build puts the compiled XDK modules in the test resources");
        support = new EmbeddingSupport().configure(new DirRepository(new File(modules.toURI()), true), null);
    }

    @Test
    void aChangeOfBodiesAndAnAddedDeclarationLeaveEveryOtherDigest() {
        String changed = LIBRARY
                .replace("return n > base ? \"positive\" : \"other\";",
                        "if (n == 42) {\n            return \"the answer\";\n        }\n"
                        + "        return n > base ? \"more\" : \"less\";")
                .replace("static Int Limit = 10;", "static Int Limit = 10;\n\n    void reset() {}");
        var before = digests(compile(LIBRARY));
        var after  = digests(compile(changed));

        Set<String> added = new TreeSet<>(after.keySet());
        added.removeAll(before.keySet());
        assertEquals(Set.of("reset", "reset()"), names(after, added));
        assertTrue(after.keySet().containsAll(before.keySet()));
        before.forEach((key, digest) -> assertEquals(digest.digest, after.get(key).digest, digest.name));
    }

    @Test
    void aChangedDefaultValueChangesThatMethodsDigestAlone() {
        var before = digests(compile(LIBRARY));
        var after  = digests(compile(LIBRARY.replace("Int base = 1", "Int base = 2")));

        assertEquals(before.keySet(), after.keySet());
        Set<String> changed = new TreeSet<>();
        before.forEach((key, digest) -> {
            if (!digest.digest.equals(after.get(key).digest)) {
                changed.add(digest.name);
            }
        });
        assertEquals(Set.of("describe(Int, Int)"), changed);
    }

    @Test
    void aChangedSignatureReplacesThatMethodsIdentity() {
        var before = digests(compile(LIBRARY));
        var after  = digests(compile(LIBRARY.replace("Int area();", "Int area(Int scale);")
                .replace("Int area() = side * side;", "Int area(Int scale) = side * side * scale;")));

        Set<String> removed = new TreeSet<>(before.keySet());
        removed.removeAll(after.keySet());
        assertEquals(Set.of("Shape.area()", "Square.area()"), names(before, removed));
        Set<String> changed = new TreeSet<>();
        before.forEach((key, digest) -> {
            if (after.containsKey(key) && !digest.digest.equals(after.get(key).digest)) {
                changed.add(digest.name);
            }
        });
        assertTrue(changed.isEmpty(), changed::toString);
    }

    @Test
    void everyDigestOfTheXdksEcstasyIsReproducible() throws IOException, URISyntaxException {
        var modules = DeclarationDigestsTest.class.getResource("/xdk-modules");
        assertNotNull(modules);
        File ecstasy = new File(new File(modules.toURI()), "ecstasy.xtc");
        assertTrue(ecstasy.isFile(), ecstasy::toString);
        byte[] bytes = Files.readAllBytes(ecstasy.toPath());

        var first  = read(bytes);
        var second = read(bytes);
        assertEquals(0, first.unstable, "the components and constants that write a constant they do not register");
        assertFalse(first.digests.isEmpty());
        assertEquals(first.digests, second.digests);
    }

    /** What one read of a module file digests, by its components' identity digests. */
    private record Read(Map<String, Declared> digests, int unstable) {
    }

    private record Declared(String name, String digest) {
    }

    private static Read read(byte[] bytes) throws IOException {
        FileStructure file;
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            file = new FileStructure(in, false, false);
        }
        var digests = DeclarationDigests.writeApiTo(file, new ByteArrayOutputStream());
        return new Read(digests(file, digests), digests.unstable());
    }

    private static Map<String, Declared> digests(byte[] module) {
        try {
            return read(module).digests;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, Declared> digests(FileStructure file, DeclarationDigests digests) {
        Map<String, Declared> all = new LinkedHashMap<>();
        try (var ignored = ConstantPool.withPool(file.getConstantPool())) {
            visit(file.getModule(), digests, all);
        }
        return all;
    }

    private static void visit(Component component, DeclarationDigests digests, Map<String, Declared> all) {
        String key = digests.of(component.getIdentityConstant());
        all.put(key, new Declared(component.getIdentityConstant().getPathString(), digests.of(component)));
        for (Component child : component.children()) {
            visit(child, digests, all);
        }
    }

    private static Set<String> names(Map<String, Declared> digests, Set<String> keys) {
        return keys.stream().map(key -> digests.get(key).name).collect(Collectors.toCollection(TreeSet::new));
    }

    private byte[] compile(String source) {
        var errs   = new ErrorList(25);
        var module = support.compile(source, null, errs);
        assertNotNull(module, () -> "compile failed: " + errs.getErrors());
        assertFalse(errs.hasSeriousErrors(), () -> "compile failed: " + errs.getErrors());
        return bytes(module);
    }

    private static byte[] bytes(ModuleStructure module) {
        var out = new ByteArrayOutputStream();
        try {
            module.getFileStructure().writeTo(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
