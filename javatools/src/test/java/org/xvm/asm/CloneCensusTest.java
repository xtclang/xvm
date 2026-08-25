package org.xvm.asm;


import java.io.IOException;
import java.io.UncheckedIOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Set;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Repo-wide orientation ratchet for the clone retirement campaign. The {@code Object.clone()}
 * mechanism shallow-copies hidden state - inner-class outer pointers, owner references, shared
 * mutable fields - and produced this branch's real defects (the {@code MethodStructure.Source}
 * and {@code Contribution} hidden-outer bugs, the {@code SingletonConstant} lifecycle leak, the
 * {@code Parameter} source mutation). Structures, constants, tokens, and sources now copy through
 * explicit copy constructors; red on master, where {@code Component}, {@code MethodStructure},
 * {@code Token}, {@code Source}, and {@code Constant} all still implemented {@code Cloneable}.
 *
 * <p>Exactly two documented islands remain, each deliberate:
 * <ul>
 * <li>{@code compiler/ast/AstNode} - request-confined compiler scratch copies; its redesign
 *     belongs to the compiler-reentrancy branch (board row: compiler AST mutation);</li>
 * <li>{@code runtime/ObjectHandle} - the guarded view/relocation mechanism: {@code cloneAs}
 *     default-denies mutable handles, and the 2026-08-24 eradication study's verdict was that
 *     shared cells plus fail-loud guards close every gap without a facade rewrite.</li>
 * </ul>
 *
 * Everything else that calls {@code .clone()} in main sources is a Java <em>array</em> clone - a
 * plain element copy with none of the Cloneable semantics. A new {@code Cloneable} implementor or
 * {@code super.clone()} site fails this census: use a copy constructor that re-binds owner state
 * explicitly (see {@code Component.copyOf} and {@code Parameter.copyFor} for the pattern).
 */
public class CloneCensusTest {
    /** The only classes that may declare {@code Cloneable}. */
    private static final Set<String> CLONEABLE_ISLANDS = Set.of(
            "org/xvm/compiler/ast/AstNode.java",
            "org/xvm/runtime/ObjectHandle.java");

    /** The only files that may invoke {@code super.clone()}. */
    private static final Set<String> SUPER_CLONE_ISLANDS = Set.of(
            "org/xvm/compiler/ast/AstNode.java",
            "org/xvm/compiler/ast/LambdaExpression.java",
            "org/xvm/compiler/ast/NamedTypeExpression.java",
            "org/xvm/compiler/ast/NewExpression.java",
            "org/xvm/runtime/ObjectHandle.java");

    @Test
    public void cloneableIsConfinedToTheTwoDocumentedIslands() {
        assertEquals(sorted(CLONEABLE_ISLANDS), mainSourcesMatching(
                        source -> source.contains("implements Cloneable")
                               || source.contains(", Cloneable")),
                "a new Cloneable implementor must use a copy constructor instead;"
                        + " see the class javadoc for why and for the pattern to follow");
    }

    @Test
    public void superCloneIsConfinedToTheIslandFiles() {
        assertEquals(sorted(SUPER_CLONE_ISLANDS),
                mainSourcesMatching(source -> source.contains("super.clone()")),
                "a new super.clone() call re-introduces hidden shallow-copy semantics;"
                        + " use a copy constructor that re-binds owner state explicitly");
    }

    private static List<String> sorted(Set<String> expected) {
        return expected.stream().sorted().toList();
    }

    private static List<String> mainSourcesMatching(java.util.function.Predicate<String> test) {
        Path sourceRoot = sourceRoot();
        try (var files = Files.walk(sourceRoot.resolve("org/xvm"))) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> test.test(readSource(path)))
                    .map(sourceRoot::relativize)
                    .map(Path::toString)
                    .map(name -> name.replace('\\', '/'))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readSource(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path sourceRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        Path project = cwd.resolve("src/main/java");
        return Files.exists(project.resolve("org/xvm/asm/ConstantPool.java"))
                ? project
                : cwd.resolve("javatools/src/main/java");
    }
}
