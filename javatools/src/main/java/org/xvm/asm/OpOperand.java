package org.xvm.asm;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/**
 * One decoded operand of an {@link Op}: the role it plays in that op, and what it refers to.
 *
 * <p>This exists so that a compiled module can be inspected as data rather than as text.
 * {@code Op} already exposes its opcode, address and scope depth, but nothing that says what it
 * <em>operates on</em>: the operands live in per-subclass protected fields with no uniform
 * accessor, so a caller asking "what method does this call target" had nothing to read but
 * {@link Op#toString}. Matching on that string is the same trap the deleted source-comparison
 * tests fell into, one level down.</p>
 *
 * <h2>The encoding</h2>
 *
 * <p>An operand is written as a single packed int whose sign carries its meaning:</p>
 *
 * <ul>
 *   <li>{@code >= 0} - a register index;</li>
 *   <li>{@code <= }{@link Op#CONSTANT_OFFSET} - a reference into the method's local constants, at
 *       {@code CONSTANT_OFFSET - n};</li>
 *   <li>everything between - one of the {@code A_*} pseudo-registers, such as the stack or
 *       {@code this}.</li>
 * </ul>
 *
 * <p>{@link Op#A_LABEL} collides with {@code CONSTANT_OFFSET}: both are -16, so constant #0 and a
 * label are indistinguishable once encoded. This resolves it the way the rest of the codebase
 * already does - {@code Argument.toIdString} and {@code Frame.getConstant} both read -16 as
 * constant #0 - because a label is not an argument operand in the positions modeled here.</p>
 *
 * <h2>Coverage is deliberately partial, and says so</h2>
 *
 * <p>{@link Op#operands()} answers {@link Optional#empty()} for op classes that do not
 * model their operands, which is different from answering an empty list for an op that genuinely
 * has none. Nothing here guesses: the wire format is positional and untyped, so a non-negative int
 * is a register in one op and a count or a jump offset in another, and only the op class knows
 * which. Modeling is therefore added per class, starting with the shared bases that most ops
 * inherit from.</p>
 */
public sealed interface OpOperand {
    /**
     * @return what this operand is for, in the op that declared it - "target", "method", "return"
     */
    String role();

    /**
     * A register index.
     */
    record Reg(String role, int index) implements OpOperand {}

    /**
     * A reference to the method's local constant at {@link #index}, resolvable through
     * {@code MethodStructure.getLocalConstants()}.
     */
    record Const(String role, int index) implements OpOperand {}

    /**
     * One of the {@code A_*} pseudo-registers.
     */
    record Special(String role, int code, String name) implements OpOperand {}

    /**
     * Decode one encoded argument.
     *
     * @param role  what the operand is for
     * @param nArg  the encoded argument, as written to the op's persistent form
     *
     * @return the decoded operand
     */
    static OpOperand decode(String role, int nArg) {
        if (nArg >= 0) {
            return new Reg(role, nArg);
        }
        if (nArg <= Op.CONSTANT_OFFSET) {
            return new Const(role, Op.CONSTANT_OFFSET - nArg);
        }
        return new Special(role, nArg, specialName(nArg));
    }

    /**
     * Decode a run of encoded arguments sharing a role, numbered from zero.
     *
     * @param role   the shared role, suffixed with each index
     * @param anArg  the encoded arguments, possibly null
     *
     * @return the decoded operands, empty if {@code anArg} is null
     */
    static List<OpOperand> decodeAll(String role, int[] anArg) {
        if (anArg == null) {
            return List.of();
        }
        var list = new ArrayList<OpOperand>(anArg.length);
        for (int i = 0; i < anArg.length; i++) {
            list.add(decode(role + '[' + i + ']', anArg[i]));
        }
        return List.copyOf(list);
    }

    private static String specialName(int nArg) {
        return switch (nArg) {
            case Op.A_STACK        -> "stack";
            case Op.A_IGNORE       -> "ignore";
            case Op.A_IGNORE_ASYNC -> "ignore-async";
            case Op.A_DEFAULT      -> "default";
            case Op.A_THIS         -> "this";
            case Op.A_TARGET       -> "target";
            case Op.A_PUBLIC       -> "public";
            case Op.A_PROTECTED    -> "protected";
            case Op.A_PRIVATE      -> "private";
            case Op.A_STRUCT       -> "struct";
            case Op.A_CLASS        -> "class";
            case Op.A_SERVICE      -> "service";
            case Op.A_SUPER        -> "super";
            case Op.A_MULTI        -> "multi";
            case Op.A_TUPLE        -> "tuple";
            default                -> "?" + nArg;
        };
    }
}
