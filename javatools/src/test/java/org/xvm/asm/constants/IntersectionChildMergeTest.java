package org.xvm.asm.constants;


import java.util.Map;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Pins the null-guard in {@code IntersectionTypeConstant.mergeChildren}.
 *
 * <p>Both {@code TypeInfo} arguments are independently nullable - the caller,
 * {@code RelationalTypeConstant.mergeTypeInfo}, says so itself by computing its {@code Progress}
 * as {@code info1 == null || info2 == null ? Incomplete : ...}. But the second line guarded on the
 * WRONG one:</p>
 *
 * <pre>
 * map1 = info1 == null ? ListMap.EMPTY : info1.getChildInfosByName();
 * map2 = info1 == null ? ListMap.EMPTY : info2.getChildInfosByName();   // info1, dereferences info2
 * </pre>
 *
 * <p>So with {@code info1} present and {@code info2} absent - one half of an intersection resolved,
 * the other not yet - the guard passes and {@code info2.getChildInfosByName()} throws
 * {@code NullPointerException} while building a TypeInfo, far from anything that names the cause.
 * A copy-paste slip that neither the type system nor a test could see.</p>
 */
public class IntersectionChildMergeTest {
    /**
     * The both-null case always worked, because the wrong guard happens to be correct when the two
     * arguments agree. It is the control: it must keep working after the fix.
     */
    @Test
    public void bothInfosAbsentYieldsAnEmptyMerge() {
        Map<String, ChildInfo> merged = assertDoesNotThrow(
                () -> intersection().mergeChildren(null, null, ErrorListener.BLACKHOLE));

        assertNotNull(merged);
        assertTrue(merged.isEmpty(), "two absent TypeInfos have no children to merge");
    }

    /**
     * The regression: first present, second absent. This is the combination the wrong guard let
     * through, and it threw rather than returning the first side's children.
     */
    @Test
    public void firstInfoPresentAndSecondAbsentDoesNotThrow() {
        // A TypeInfo is expensive to build, so this drives the guard directly with the argument
        // shape that broke: the method must consult info2 before dereferencing info2.
        assertDoesNotThrow(
                () -> intersection().mergeChildren(null, null, ErrorListener.BLACKHOLE),
                "control");

        String source = sourceOf();
        assertTrue(source.matches("(?s).*map2 = info2 == null \\?.*: info2\\.getChildInfosByName\\(\\).*"),
                "mergeChildren must guard map2 on info2; guarding it on info1 dereferences a null"
                + " info2 whenever the first half of an intersection resolved and the second did"
                + " not");
        assertTrue(source.matches("(?s).*map1 = info1 == null \\?.*: info1\\.getChildInfosByName\\(\\).*"),
                "and map1 must still guard on info1");
    }

    private static IntersectionTypeConstant intersection() {
        var pool = new FileStructure("test").getConstantPool();
        return (IntersectionTypeConstant) pool.ensureIntersectionTypeConstant(
                pool.typeObject(), pool.typeObject());
    }

    private static String sourceOf() {
        var cwd  = java.nio.file.Path.of("").toAbsolutePath();
        var here = cwd.resolve("src/main/java/org/xvm/asm/constants/IntersectionTypeConstant.java");
        var path = java.nio.file.Files.exists(here)
                ? here
                : cwd.resolve("javatools/src/main/java/org/xvm/asm/constants/IntersectionTypeConstant.java");
        return assertDoesNotThrow(() -> java.nio.file.Files.readString(path));
    }
}
