package org.xvm.javajit;

import java.lang.constant.ClassDesc;

/**
 * Describes a detached body: a virtual child instance method's Ecstasy implementation emitted as a
 * static method on the child's implementation class with an explicit leading receiver. The
 * receiver type is independent of the class that owns the static method.
 *
 * <p>Suppose B declares children C0 and C1, with C1 extending C0, and D extends B and declares
 * same-name children D.C0 and D.C1. The D.C0.foo() body can run on a D.C1 object, but the Java
 * implementation D_C1 extends B_C1, not D_C0. A detached body with an interface receiver permits
 * D.C0's body to execute on the differently named child D.C1:
 *
 *     static R s$foo(D_iC0 thi$, Ctx ctx, ...)
 *
 * <p>A base child has no same-name super child in its enclosing parent's inheritance hierarchy.
 * Both B.C0 and B.C1 are base children, despite B.C1 extending the differently named B.C0;
 * these are the "hard-left" classes in the virtual-child design. Moreover, D.C0 and D.C1 are
 * derived children, even if D does not explicitly declare them (phantoms).
 *
 * <p>Hard-left bodies, such as B.C0.foo(), can use their concrete implementation receiver.
 * This descriptor places that receiver in parameter/slot 0 and Ctx in parameter/slot 1, while
 * retaining instance-method semantics during code generation so that Ecstasy "this" is available.
 */
public class JitDetachedBodyDesc extends JitMethodDesc {
    /**
     * @param method      the logical Ecstasy instance-method descriptor
     * @param receiverCD  the leading receiver of the detached body
     */
    public JitDetachedBodyDesc(JitMethodDesc method, ClassDesc receiverCD) {
        this(method, receiverCD, false);
    }

    private JitDetachedBodyDesc(JitMethodDesc method, ClassDesc receiverCD, boolean isStatic) {
        this.receiverCD = receiverCD;
        super(method.typeTarget, method.standardReturns, method.standardParams,
                method.optimizedReturns, method.optimizedParams, isStatic);
    }

    /**
     * The explicit receiver's JVM descriptor.
     */
    public final ClassDesc receiverCD;

    /**
     * Describe the Java-static boxed wrapper, rather than the Ecstasy instance body.
     */
    public JitDetachedBodyDesc staticWrapper() {
        assert !isStandardStatic;
        return new JitDetachedBodyDesc(this, receiverCD, true);
    }

    @Override
    public int getImplicitParamCount() {
        return 2; // receiver, Ctx
    }

    @Override
    protected int fillExtraClassDesc(ClassDesc[] paramCDs, int ix) {
        paramCDs[ix++] = receiverCD;
        return super.fillExtraClassDesc(paramCDs, ix);
    }

    @Override
    public int standardCtx() {
        return 1;
    }

    @Override
    public int optimizedCtx() {
        return 1;
    }
}
