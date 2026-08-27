package org.xvm.asm;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;

/**
 * Represents any argument for an op, including constants, registers, and pre-defined
 * references like "this".
 */
public interface Argument {
    /**
     * @return the type of the argument, which is the value of the Referent type parameter from the
     *         implicit Ref/Var that this argument represents
     */
    TypeConstant getType();

    /**
     * @return true iff this argument is known to represent an effectively final value
     */
    boolean isEffectivelyFinal();

    /**
     * Register all constants that this Argument depends on into the passed registry.
     *
     * @param registry  the code-local constant registry
     *
     * @return the Argument to use in place of this Argument
     */
    Argument registerConstants(Op.ConstantRegistry registry);

    /**
     * For debugging purposes, format the optional "arg" and arg index.
     *
     * @param arg   an optional Argument (could be null)
     * @param nArg  an argument index
     *
     * @return a String useful for debugging purposes
     */
    static String toIdString(Argument arg, int nArg) {
        if (arg instanceof Constant constant) {
            return constant.getValueString();
        }
        if (arg instanceof Register reg) {
            return reg.getIdString();
        }
        if (nArg <= Op.CONSTANT_OFFSET) {
            // PURE: a constant referenced only by index needs a frame to resolve. Do NOT read the
            // ambient ServiceContext/fiber - under a debugger that is whatever frame happens to be
            // current on the observing thread, usually NOT this op's frame, so it indexed an
            // unrelated constant array (AIOOBE silently swallowed) or printed misleading text. Render
            // a marker; frame-owning dumps resolve it via the explicit toIdString(Frame, ...) below.
            return "const:#" + Op.convertId(nArg);
        }
        return Register.getIdString(nArg);
    }

    /**
     * Forced display: resolve constant ids against an EXPLICITLY supplied frame (e.g. from
     * {@code Frame.formatFrameDetails} and other frame-owning dumps), never the ambient service
     * context.
     *
     * @param frame  the frame that owns the op, or null
     * @param arg    an optional Argument (could be null)
     * @param nArg   an argument index
     *
     * @return a String useful for debugging purposes
     */
    static String toIdString(Frame frame, Argument arg, int nArg) {
        if (arg == null && nArg <= Op.CONSTANT_OFFSET && frame != null) {
            return frame.localConstants()[Op.convertId(nArg)].getValueString();
        }
        return toIdString(arg, nArg);
    }
}
