package org.xvm.runtime;


import org.junit.jupiter.api.Test;

import org.xvm.runtime.template.text.xString.StringHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Covers the typed native binding in isolation: the conversion each arity performs before calling
 * a handler, without needing a running container.
 *
 * <p>The point of the binding is that a native method body is typed by the compiler rather than by
 * a hand-written cast, and that the one remaining conversion is <b>checked</b>. These tests pin
 * both halves: the handler receives the declared handle types, and a handle that does not match
 * the declaration fails immediately, at the binding, naming the mismatch - rather than reaching a
 * handler body and failing somewhere less obvious.</p>
 *
 * <p>They exist because the integration path is only exercised for the arities that some Ecstasy
 * code happens to call. {@code invokeNativeN}'s zero-argument form is bound the same way as the
 * others, but the only caller of the one native using it - {@code OSStorage.instance()} - is the
 * runtime compiler's unknown-implementation branch, so no simple probe reaches it.</p>
 */
public class NativeBindingTest {
    /**
     * A stand-in handle. {@link ObjectHandle} is abstract and its real subclasses need a
     * TypeComposition, so these tests use a minimal concrete handle of their own; the binding only
     * ever calls {@link Class#cast} on it.
     */
    private static class TestHandle
            extends ObjectHandle {
        protected TestHandle() {
            super(null);
        }
    }

    /** A second handle type, to prove a mismatch is actually detected rather than waved through. */
    private static class OtherHandle
            extends ObjectHandle {
        protected OtherHandle() {
            super(null);
        }
    }

    private static final NativeType<TestHandle>  SELF  =
            NativeType.of("test.Self", TestHandle.class);
    private static final NativeType<OtherHandle> OTHER =
            NativeType.of("test.Other", OtherHandle.class);

    @Test
    public void aTypeCarriesBothItsEcstasyNameAndItsHandleClass() {
        assertEquals("test.Self", SELF.typeName());
        assertSame(TestHandle.class, SELF.handleClass());
    }

    /**
     * A half-built declaration must fail where it is written. Without this, a null slips through
     * and only surfaces later at a dispatch, with nothing left to say which declaration was wrong.
     */
    @Test
    public void aTypeMissingEitherHalfIsRejectedAtConstruction() {
        assertThrows(NullPointerException.class,
                () -> NativeType.of(null, TestHandle.class), "a type with no name");
        assertThrows(NullPointerException.class,
                () -> NativeType.of("test.Self", null), "a type with no handle class");
    }

    /**
     * {@link NativeSignature} exists so no caller writes the {@code null} that the underlying
     * lookup uses for "no filter" - 140 of the tree's 392 native declarations pass it for both
     * halves. The nulls live here, in one place, and each factory says which filters apply.
     */
    @Test
    public void aSignatureNamesWhichFiltersApplyInsteadOfPassingNull() {
        assertNull(NativeSignature.BY_NAME.paramTypes());
        assertNull(NativeSignature.BY_NAME.returnTypes());

        var params = NativeSignature.params("text.String");
        assertEquals(1, params.paramTypes().length);
        assertEquals("text.String", params.paramTypes()[0]);
        assertNull(params.returnTypes(), "params() must not constrain the returns");

        var returns = NativeSignature.returns("numbers.Int64");
        assertNull(returns.paramTypes(), "returns() must not constrain the parameters");
        assertEquals("numbers.Int64", returns.returnTypes()[0]);

        var both = NativeSignature.of(new String[] {"text.String"}, new String[] {"Boolean"});
        assertEquals("text.String", both.paramTypes()[0]);
        assertEquals("Boolean", both.returnTypes()[0]);
    }

    /** The zero-argument arity, whose integration path no probe program reaches. */
    @Test
    public void zeroArityPassesTheReceiverTyped() {
        var hThis = new TestHandle();
        var seen  = new TestHandle[1];

        NativeMethod.BoundN bound = NativeMethod.bind(SELF, (frame, hTarget, iReturn) -> {
            seen[0] = hTarget;                       // typed: no cast in this body
            return iReturn;
        });

        assertEquals(42, bound.dispatch(null, hThis, new ObjectHandle[0], 42));
        assertSame(hThis, seen[0]);
    }

    /**
     * A zero-argument native may be called with a null receiver - {@code OSStorage.instance()} is,
     * being static - and {@link Class#cast} passes null through, so that must keep working.
     */
    @Test
    public void zeroArityToleratesANullReceiver() {
        var seen = new TestHandle[] {new TestHandle()};

        NativeMethod.BoundN bound = NativeMethod.bind(SELF, (frame, hTarget, iReturn) -> {
            seen[0] = hTarget;
            return iReturn;
        });

        assertEquals(7, bound.dispatch(null, null, new ObjectHandle[0], 7));
        assertNull(seen[0], "a null receiver must reach the handler as null, not throw");
    }

    @Test
    public void twoArityPassesBothArgumentsTyped() {
        var hThis = new TestHandle();
        var hOne  = new TestHandle();
        var hTwo  = new OtherHandle();
        var seen  = new ObjectHandle[3];

        NativeMethod.BoundN bound = NativeMethod.bind(SELF, SELF, OTHER,
                (frame, hTarget, hArg1, hArg2, iReturn) -> {
                    seen[0] = hTarget;
                    seen[1] = hArg1;
                    seen[2] = hArg2;                 // all three typed, no casts
                    return iReturn;
                });

        bound.dispatch(null, hThis, new ObjectHandle[] {hOne, hTwo}, 1);

        assertSame(hThis, seen[0]);
        assertSame(hOne,  seen[1]);
        assertSame(hTwo,  seen[2]);
    }

    @Test
    public void multiReturnArityPassesArgumentsTypedAndTheReturnRegisters() {
        var    hThis     = new TestHandle();
        var    hOther    = new OtherHandle();
        int[]  aiReturn  = {3, 4};
        var    seenRegs  = new int[1][];

        NativeMethod.BoundNN bound = NativeMethod.bindNN(SELF, SELF, OTHER,
                (frame, hTarget, hArg1, hArg2, aiRet) -> {
                    seenRegs[0] = aiRet;
                    return 0;
                });

        bound.dispatch(null, hThis, new ObjectHandle[] {new TestHandle(), hOther}, aiReturn);

        assertSame(aiReturn, seenRegs[0]);
    }

    /**
     * The conversion is checked. A handle that does not match the declared type fails at the
     * binding, and the failure names the offending class - which is the whole reason for doing it
     * once here rather than as an unchecked cast in each of the 744 native bodies.
     */
    @Test
    public void aMismatchedArgumentFailsAtTheBindingAndNamesTheType() {
        NativeMethod.BoundN bound = NativeMethod.bind(SELF, SELF, OTHER,
                (frame, hTarget, hArg1, hArg2, iReturn) ->
                        fail_shouldNotReachHandler());

        var e = assertThrows(ClassCastException.class,
                () -> bound.dispatch(null, new TestHandle(), 
                        new ObjectHandle[] {new TestHandle(), new TestHandle()}, 0),
                "the second argument is declared test.Other but a TestHandle was passed");

        assertTrue(e.getMessage().contains("OtherHandle"),
                () -> "the failure must name the declared type, but was: " + e.getMessage());
    }

    @Test
    public void aMismatchedReceiverFailsAtTheBinding() {
        NativeMethod.BoundN bound = NativeMethod.bind(SELF,
                (frame, hTarget, iReturn) -> fail_shouldNotReachHandler());

        assertThrows(ClassCastException.class,
                () -> bound.dispatch(null, new OtherHandle(), new ObjectHandle[0], 0),
                "the receiver is declared test.Self but an OtherHandle was passed");
    }

    /**
     * The shared-type form: an Ecstasy {@code Int} has two Java representations, so a handler is
     * declared against what they share rather than against either class.
     */
    @Test
    public void aSharedTypeAcceptsEveryRepresentationOfItsEcstasyType() {
        NativeType<IntegralValue> intType =
                NativeType.ofShared("numbers.Int64", IntegralValue.class);

        var hSmall = new ObjectHandle.JavaLong(null, 42L);
        IntegralValue converted = intType.cast(hSmall);

        assertTrue(converted.fitsLong(true), "a JavaLong always fits");
        assertEquals(42L, converted.longValue());

        assertThrows(ClassCastException.class, () -> intType.cast(new OtherHandle()),
                "a handle that does not carry an integral value must still be rejected");
    }

    private static int fail_shouldNotReachHandler() {
        throw new AssertionError("the handler must not be reached when a handle mismatches");
    }
}
