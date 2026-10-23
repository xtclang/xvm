package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;

import static org.xvm.javajit.Builder.OPT;

/**
 * Describes a Java call target, its calling convention, and its dispatch mode.
 *
 * <p>The owner declares the target method, while receiverCD describes an optional explicit receiver
 * argument, not Java's implicit "this". They can differ for virtual-child super-router calls.
 *
 * <p>For example, for a parent B with children C0 and C1, the Java implementation B_C1 can declare:
 *      class B_C1 {
 *          R super$C0$foo(B_iC0 receiver, Ctx ctx, ...) { ... }
 *      }
 * Here owner is B_C1, but receiverCD is B_iC0, the interface shared by B.C0 and its same-name
 * derived children. The implicit receiver selects the composition-specific override; the explicit
 * receiver is forwarded to the selected detached body. Both receivers refer to the same object,
 * but have different Java types.
 * A detached s$foo body also has an explicit receiver: its owner is the implementation class,
 * while receiverCD is the callable type (a concrete hard-left class or a derived-child interface).
 *
 * @param owner        the class or interface declaring the target method
 * @param name         the standard target method name, without the optimized suffix
 * @param jmd          the standard and optimized calling conventions
 * @param kind         the invocation mode
 * @param isInterface  true iff the owner is a Java interface
 * @param receiverCD   an explicit receiver parameter prepended to the signature, or null
 */
public record JitCallDesc(ClassDesc owner, String name, JitMethodDesc jmd,
                          Kind kind, boolean isInterface, ClassDesc receiverCD) {
    /**
     * Obtain the target method signature, including its explicit receiver when required.
     */
    public MethodTypeDesc methodType(boolean optimized) {
        MethodTypeDesc md = optimized ? jmd.optimizedMD : jmd.standardMD;
        return receiverCD == null ? md : md.insertParameterTypes(0, receiverCD);
    }

    /**
     * @return the MethodHandle reference kind for this call target
     */
    public DirectMethodHandleDesc.Kind toKind() {
        return switch (kind) {
            case Static  -> isInterface ? DirectMethodHandleDesc.Kind.INTERFACE_STATIC
                                        : DirectMethodHandleDesc.Kind.STATIC;
            case Virtual -> isInterface ? DirectMethodHandleDesc.Kind.INTERFACE_VIRTUAL
                                        : DirectMethodHandleDesc.Kind.VIRTUAL;
            case Special -> isInterface ? DirectMethodHandleDesc.Kind.INTERFACE_SPECIAL
                                        : DirectMethodHandleDesc.Kind.SPECIAL;
        };
    }

    /**
     * Obtain a MethodHandle descriptor for this call target.
     */
    public DirectMethodHandleDesc toMethodHandle(boolean optimized) {
        return MethodHandleDesc.ofMethod(toKind(), owner, name + (optimized ? OPT : ""),
                methodType(optimized));
    }

    /**
     * @return true iff this method requires a virtual dispatch
     */
    public boolean isVirtual() {
        return kind == Kind.Virtual;
    }

    /**
     * Invoke the selected standard or optimized target method. Before calling this, all the
     * call arguments should already be on the Java stack.
     */
    public void invoke(CodeBuilder code, boolean optimized) {
        String         name = this.name + (optimized ? OPT : "");
        MethodTypeDesc md   = methodType(optimized);
        switch (kind) {
            case Static  ->
                code.invokestatic(owner, name, md, isInterface);

            case Virtual -> {
                if (isInterface) {
                    code.invokeinterface(owner, name, md);
                } else {
                    code.invokevirtual(owner, name, md);
                }
            }
            case Special ->
                code.invokespecial(owner, name, md, isInterface);
        }
    }

    /**
     * Create a call to a specific implementation with the "invokespecial" dispatch.
     */
    public static JitCallDesc special(ClassDesc owner, String name, JitMethodDesc jmd, boolean isInterface) {
        return new JitCallDesc(owner, name, jmd, Kind.Special, isInterface, null);
    }

    /**
     * Create a call to a specific implementation with the "invokestatic" dispatch.
     */
    public static JitCallDesc static_(ClassDesc owner, String name, JitMethodDesc jmd, boolean isInterface) {
        return new JitCallDesc(owner, name, jmd, Kind.Static, isInterface, null);
    }

    /**
     * Java invocation modes, independent of whether the owner is a class or an interface.
     */
    public enum Kind {Static, Virtual, Special}
}
