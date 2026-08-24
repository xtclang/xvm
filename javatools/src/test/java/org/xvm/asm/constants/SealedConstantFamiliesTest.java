package org.xvm.asm.constants;


import java.lang.reflect.Modifier;

import java.util.Arrays;
import java.util.Set;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the sealed constant families (sealed-hierarchy audit, stage 0). Sealing turns the
 * hand-maintained convention "nobody adds a condition/pseudo/frame-dependent constant kind
 * without reviewing every dispatch site" into a javac obligation: an unlisted subclass no longer
 * compiles, and exhaustive pattern switches over these trees (see
 * {@code SimulatedLinkerContext.extractRequiredConditions}) fail to compile when a kind is
 * added, instead of silently dropping it at runtime. These assertions keep the permits lists
 * from being quietly widened or unsealed without updating the audit.
 */
public class SealedConstantFamiliesTest {
    @Test
    public void conditionalConstantTreeIsSealed() {
        assertPermits(ConditionalConstant.class,
                MultiCondition.class, NamedCondition.class, NotCondition.class,
                PresentCondition.class, VersionMatchesCondition.class, VersionedCondition.class);
        assertPermits(MultiCondition.class, AllCondition.class, AnyCondition.class);
    }

    @Test
    public void pseudoConstantTreeIsSealed() {
        assertPermits(PseudoConstant.class,
                ChildClassConstant.class, DeferredValueConstant.class, ExpressionConstant.class,
                KeywordConstant.class, ParentClassConstant.class, SignatureConstant.class,
                ThisClassConstant.class, UnresolvedNameConstant.class);
    }

    @Test
    public void frameDependentConstantTreeIsSealed() {
        assertPermits(FrameDependentConstant.class,
                HandleConstant.class, MethodBindingConstant.class, RegisterConstant.class);
    }

    @Test
    public void typeInfoIsSealed() {
        assertPermits(TypeInfo.class, TypeInfoReal.class);
    }

    // ----- helpers -------------------------------------------------------------------------------

    private static void assertPermits(Class<?> root, Class<?>... leaves) {
        assertTrue(root.isSealed(), root.getSimpleName() + " must be sealed");
        assertEquals(setOf(leaves), setOf(root.getPermittedSubclasses()),
                root.getSimpleName() + " permits list drifted; update the sealed-hierarchy"
                        + " audit if this is intentional");
        for (Class<?> leaf : leaves) {
            assertTrue(leaf.isSealed() || Modifier.isFinal(leaf.getModifiers()),
                    leaf.getSimpleName() + " must be final or sealed; a non-sealed leaf"
                            + " reopens the family");
        }
    }

    private static Set<String> setOf(Class<?>... classes) {
        return Arrays.stream(classes).map(Class::getName).collect(Collectors.toSet());
    }
}
