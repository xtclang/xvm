package org.xvm.asm;


import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.xvm.runtime.ObjectHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


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
    /**
     * The only classes that may declare {@code Cloneable}.
     *
     * <p>Named as compiled classes, not source files: this reads the class files, so a nested or
     * anonymous class that implemented {@code Cloneable} would be named here as itself rather than
     * hiding inside its enclosing file's text.
     */
    private static final Set<String> CLONEABLE_ISLANDS = Set.of(
            "org/xvm/runtime/ObjectHandle");

    /**
     * The only files that may invoke {@code super.clone()}. Ratcheted DOWN 2026-08-25: the
     * AST island is fully clone-free as of the copy-constructor migration: explicit copy
     * constructors behind an abstract {@code shallowCopy()}, compiler-enforced by the sealed
     * hierarchy. {@code ObjectHandle} remains the one deliberate island (views by design).
     */
    private static final Set<String> SUPER_CLONE_ISLANDS = Set.of(
            "org/xvm/runtime/ObjectHandle");

    @Test
    public void cloneableIsConfinedToTheTwoDocumentedIslands() throws Exception {
        assertEquals(sorted(CLONEABLE_ISLANDS), classesImplementingCloneable(),
                "a new Cloneable implementor must use a copy constructor instead;"
                        + " see the class javadoc for why and for the pattern to follow");
    }

    @Test
    public void superCloneIsConfinedToTheIslandFiles() throws Exception {
        assertEquals(sorted(SUPER_CLONE_ISLANDS), classesInvokingSuperClone(),
                "a new super.clone() call re-introduces hidden shallow-copy semantics;"
                        + " use a copy constructor that re-binds owner state explicitly");
    }

    private static List<String> sorted(Set<String> expected) {
        return expected.stream().sorted().toList();
    }

    /**
     * @return every compiled class that declares {@code Cloneable} among its interfaces
     */
    private static List<String> classesImplementingCloneable() throws Exception {
        return scan(model -> model.interfaces().stream()
                .anyMatch(i -> "java/lang/Cloneable".equals(i.asInternalName())));
    }

    /**
     * @return every compiled class containing an {@code invokespecial} of {@code Object.clone}
     *
     * <p>{@code super.clone()} compiles to exactly that, and nothing else does - an ordinary
     * {@code x.clone()} is {@code invokevirtual}. So this is the call, not a spelling of it.
     */
    private static List<String> classesInvokingSuperClone() throws Exception {
        return scan(model -> model.methods().stream()
                .anyMatch(method -> method.code()
                        .map(code -> code.elementList().stream()
                                .anyMatch(e -> e instanceof InvokeInstruction invoke
                                        && invoke.opcode() == Opcode.INVOKESPECIAL
                                        && "clone".equals(invoke.method().name().stringValue())
                                        && "java/lang/Object".equals(invoke.owner().asInternalName())))
                        .orElse(false)));
    }

    /**
     * Walk the compiled classes rather than the sources. Reading `.java` as text is the pattern
     * `54bcea306` deleted five tests for: it passes when the code is spelled the expected way,
     * breaks on reformatting, and would miss `implements Foo, Cloneable` wrapped across lines while
     * matching the same words inside a comment. An interface in the class file is the fact itself.
     *
     * @param test  what makes a class an offender
     *
     * @return the offending classes, as {@code package/Name}, sorted
     */
    private static List<String> scan(java.util.function.Predicate<ClassModel> test) throws Exception {
        Path anchor = Path.of(ObjectHandle.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(anchor),
                "the tree must be scannable as exploded classes, but the code source is " + anchor);

        var found = new ArrayList<String>();
        try (var files = Files.walk(anchor.resolve("org/xvm"))) {
            for (Path path : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                ClassModel model = ClassFile.of().parse(Files.readAllBytes(path));
                if (test.test(model)) {
                    String name = anchor.relativize(path).toString()
                            .replace(".class", "").replace('\\', '/');
                    // nested classes report as Outer$Inner; the island list names the outer class
                    found.add(name.contains("$") ? name.substring(0, name.indexOf('$')) : name);
                }
            }
        }
        return found.stream().distinct().sorted().toList();
    }
}
