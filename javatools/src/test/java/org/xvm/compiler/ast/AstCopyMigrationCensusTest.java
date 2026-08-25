package org.xvm.compiler.ast;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ratchet for the AST-island clone eradication (user-approved series): the compiler AST is the
 * one island where {@code Cloneable} genuinely dies. Migration design: {@code AstNode.deepCopy()}
 * walks children over a per-class {@code shallowCopy()}; converted classes provide an explicit
 * copy constructor, {@code Cloneable}, {@code clone()}, and the migration bridge are GONE -
 * {@code shallowCopy()} is abstract, so the sealed hierarchy makes coverage
 * compiler-enforced, and every copy is assertion-checked field-by-field so a copy
 * constructor cannot silently omit a field (the hazard that made the prior audit keep the
 * island).
 *
 * <p>This census pins the end state: no {@code super.clone()}, no {@code Cloneable}, no
 * {@code clone()} declaration, and no direct {@code .clone()} calls on AST nodes may ever
 * return to the island.</p>
 */
public class AstCopyMigrationCensusTest {
    @Test
    public void singleCloneBridgeUntilEradication() throws IOException {
        List<Path> sources = astSources();
        assertTrue(sources.size() > 80, "expected the full AST package, found " + sources.size());

        int cSuperClone = 0;
        int cCloneable  = 0;
        int cCloneDecl  = 0;
        for (Path path : sources) {
            for (String line : Files.readString(path).split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("*") || trimmed.startsWith("//")) {
                    continue; // javadoc/comments may cite the mechanism
                }
                cSuperClone += count(line, "super.clone()");
                cCloneable  += count(line, "implements Cloneable");
                cCloneDecl  += count(line, "public final AstNode clone()");
            }
        }

        assertEquals(0, cSuperClone,
                "the AST island is clone-free: no super.clone() may return; every concrete"
                        + " node class has an explicit copy constructor behind shallowCopy()");
        assertEquals(0, cCloneable,
                "Cloneable is eradicated from the AST island");
        assertEquals(0, cCloneDecl,
                "clone() is eradicated; deepCopy() is the only copy entry point");
    }

    @Test
    public void noDirectAstCloneCallsRemain() throws IOException {
        // every trial-copy entry point goes through deepCopy(); a direct node.clone() call
        // would bypass the parity-checked walk. Java ARRAY clones (atype.clone() etc.) are a
        // different mechanism and remain legal.
        for (Path path : astSources()) {
            String src = Files.readString(path);
            for (String line : src.split("\n")) {
                if (line.contains(".clone()") && !line.contains("super.clone()")
                        && !line.trim().startsWith("*") && !line.trim().startsWith("//")) {
                    assertTrue(isArrayClone(line),
                            "direct AstNode clone() call must use deepCopy(): "
                                    + path.getFileName() + ": " + line.trim());
                }
            }
        }
    }

    private static boolean isArrayClone(String line) {
        // the array-clone receivers in this package are locals/fields holding Java arrays;
        // keep this list explicit so a new AstNode clone cannot hide behind it
        return line.contains("aType") || line.contains("atype") || line.contains("aVal")
                || line.contains("aRetTypes") || line.contains("aLVal")
                || line.contains("aAstLVal") || line.contains("toConstants()");
    }

    private static int count(String s, String needle) {
        int c = 0;
        for (int i = s.indexOf(needle); i >= 0; i = s.indexOf(needle, i + 1)) {
            ++c;
        }
        return c;
    }

    private static List<Path> astSources() throws IOException {
        Path root = sourceRoot().resolve("org/xvm/compiler/ast");
        assertTrue(Files.isDirectory(root), "AST source dir not found: " + root);
        try (Stream<Path> stream = Files.list(root)) {
            return stream.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static Path sourceRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        Path project = cwd.resolve("src/main/java");
        if (Files.exists(project.resolve("org/xvm/compiler/ast/AstNode.java"))) {
            return project;
        }
        return cwd.resolve("javatools/src/main/java");
    }
}
