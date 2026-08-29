package org.xvm.asm.constants;


import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Component;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.PackageStructure;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.TypedefStructure;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Pins which {@code IdentityConstant} subclasses may narrow {@code getComponent()} — and, more
 * importantly, which one MUST NOT.
 *
 * <p>{@code ClassConstant.getComponent()} was narrowed to {@code ClassStructure} and had to be
 * reverted. A {@code ClassConstant} does <b>not</b> always name a {@code ClassStructure}: it can
 * name a {@code TypedefStructure}. The failure was
 * {@code ClassCastException: TypedefStructure cannot be cast to ClassStructure} at
 * {@code ConstantPool.getImplicitlyImportedComponent} while compiling {@code lib_ecstasy}.</p>
 *
 * <p><b>The lesson, and why this test exists.</b> A covariant return applies to EVERY caller, not
 * only the ones that were already casting. The 113 sites that cast a {@code ClassConstant}'s
 * component to {@code ClassStructure} were all in paths where it genuinely is one — but
 * {@code getImplicitlyImportedComponent} is a caller that was correctly using the supertype, and
 * narrowing forced the cast onto it too. "Nearly every caller casts to X" is NOT sufficient
 * grounds to narrow; the subclass must ALWAYS produce X.</p>
 *
 * <p>The full test suite did not catch this. Only building the XDK did, because the failing path
 * runs when the compiler resolves an implicitly imported typedef. That is now the standing rule
 * for this campaign: a narrowing change is not verified until {@code xdk:installDist} passes.</p>
 */
public class IdentityComponentNarrowingTest {
    /**
     * These subclasses each name exactly one kind of component, so narrowing is sound.
     */
    @Test
    public void soundNarrowingsAreInPlace() throws NoSuchMethodException {
        assertReturns(MethodConstant.class,      MethodStructure.class);
        assertReturns(PropertyConstant.class,    PropertyStructure.class);
        assertReturns(ModuleConstant.class,      ModuleStructure.class);
        assertReturns(PackageConstant.class,     PackageStructure.class);
        assertReturns(TypedefConstant.class,     TypedefStructure.class);
        assertReturns(MultiMethodConstant.class, MultiMethodStructure.class);
    }

    /**
     * {@code ClassConstant} must keep returning the supertype. If someone narrows it again, this
     * fails with the reason rather than the failure surfacing later as a ClassCastException in the
     * middle of compiling the core library.
     */
    @Test
    public void classConstantMustNotNarrowBecauseItCanNameATypedef() throws NoSuchMethodException {
        assertReturns(ClassConstant.class, Component.class);
        assertReturns(DecoratedClassConstant.class, Component.class);
    }

    private static void assertReturns(Class<?> clzConstant, Class<?> clzExpected)
            throws NoSuchMethodException {
        Method method = clzConstant.getMethod("getComponent");
        assertEquals(clzExpected, method.getReturnType(),
                () -> clzConstant.getSimpleName() + ".getComponent() must return "
                      + clzExpected.getSimpleName()
                      + (clzExpected == Component.class
                             ? " - it does not always name one specific structure kind"
                             : " - it names exactly that kind"));
    }
}
