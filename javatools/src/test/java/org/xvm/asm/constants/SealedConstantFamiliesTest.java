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

    /**
     * Stage 2: the TypeConstant tree - the most instanceof-tested hierarchy in the codebase
     * (181 occurrences) - is sealed shut: the root permits its eleven direct kinds, every
     * intermediate is sealed, every leaf final.
     */
    @Test
    public void typeConstantTreeIsSealed() {
        assertPermits(TypeConstant.class,
                AbstractDependantTypeConstant.class, AccessTypeConstant.class,
                AnnotatedTypeConstant.class, ImmutableTypeConstant.class,
                ParameterizedTypeConstant.class, PendingTypeConstant.class,
                RelationalTypeConstant.class, ServiceTypeConstant.class,
                TerminalTypeConstant.class, TypeSequenceTypeConstant.class,
                UnresolvedTypeConstant.class);
        assertPermits(AbstractDependantTypeConstant.class,
                AbstractDependantChildTypeConstant.class, PropertyClassTypeConstant.class);
        assertPermits(AbstractDependantChildTypeConstant.class,
                AnonymousClassTypeConstant.class, InnerChildTypeConstant.class,
                VirtualChildTypeConstant.class);
        assertPermits(RelationalTypeConstant.class,
                DifferenceTypeConstant.class, IntersectionTypeConstant.class,
                UnionTypeConstant.class);
        assertPermits(IntersectionTypeConstant.class, CastTypeConstant.class);
        assertPermits(TerminalTypeConstant.class, RecursiveTypeConstant.class);
    }

    /**
     * Stage 3: the IdentityConstant tree, with zero non-sealed hatches - the constructor-escape
     * probe fakes that used to subclass PropertyConstant/FormalTypeChildConstant were retired
     * because the fatal -Xlint:this-escape gate enforces their property at compile time.
     */
    @Test
    public void identityConstantTreeIsSealed() {
        assertPermits(IdentityConstant.class,
                DecoratedClassConstant.class, MethodConstant.class, ModuleConstant.class,
                NamedConstant.class, PureIdentityConstant.class);
        assertPermits(NamedConstant.class,
                ClassConstant.class, FormalConstant.class, MultiMethodConstant.class,
                PackageConstant.class, TypedefConstant.class);
        assertPermits(ClassConstant.class, NativeRebaseConstant.class);
        assertPermits(FormalConstant.class,
                DynamicFormalConstant.class, PropertyConstant.class, TypeParameterConstant.class);
        assertPermits(PropertyConstant.class, FormalTypeChildConstant.class);
    }

    /**
     * Stage 3: the ValueConstant tree, no hatches - the pool-registration deadlock latch now
     * extends the (unsealed) Constant root directly, so StringConstant is final.
     */
    @Test
    public void valueConstantTreeIsSealed() {
        assertPermits(ValueConstant.class,
                ArrayConstant.class, BFloat16Constant.class, ByteConstant.class,
                CharConstant.class, DecimalAutoConstant.class, DecimalConstant.class,
                FPNConstant.class, FSNodeConstant.class, FileStoreConstant.class,
                Float128Constant.class, Float64Constant.class, FloatConstant.class,
                IntConstant.class, LiteralConstant.class, MapConstant.class,
                MatchAnyConstant.class, RangeConstant.class, RegExConstant.class,
                SingletonConstant.class, StringConstant.class, UInt8ArrayConstant.class);
        assertPermits(FloatConstant.class,
                Float16Constant.class, Float32Constant.class, Float8e4Constant.class,
                Float8e5Constant.class);
        assertPermits(LiteralConstant.class, VersionConstant.class);
        assertPermits(SingletonConstant.class, EnumValueConstant.class);
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
