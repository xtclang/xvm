package org.xvm.asm;


import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;


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
     * @param encoded  the encoded argument, as written to the op's persistent form
     *
     * @return the decoded operand
     */
    static OpOperand decode(String role, int encoded) {
        if (encoded >= 0) {
            return new Reg(role, encoded);
        }
        if (encoded <= Op.CONSTANT_OFFSET) {
            return new Const(role, Op.CONSTANT_OFFSET - encoded);
        }
        return new Special(role, encoded, specialName(encoded));
    }

    /**
     * Decode a run of encoded arguments sharing a role, numbered from zero.
     *
     * @param role   the shared role, suffixed with each index
     * @param encoded  the encoded arguments, possibly null
     *
     * @return the decoded operands, empty if {@code encoded} is null
     */
    static List<OpOperand> decodeAll(String role, int[] encoded) {
        return encoded == null
                ? List.of()
                : IntStream.range(0, encoded.length)
                        .mapToObj(i -> decode(role + '[' + i + ']', encoded[i]))
                        .toList();
    }

    /**
     * Build an operand list from what a superclass contributed plus this level's own, which is the
     * shape almost every override has: {@code super.operands()} then a fixed few more.
     *
     * <p>Java has no {@code buildList}, and the alternative - a local {@code ArrayList} then
     * {@code List.copyOf} - spends four lines saying something structural in a mutable idiom. The
     * result is unmodifiable either way ({@link Stream#toList()}), so this is about the reading,
     * not the safety.</p>
     *
     * @param inherited  the superclass's operands, possibly absent
     * @param added      this level's operands, in wire order
     *
     * @return the combined, unmodifiable list
     */
    static List<OpOperand> concat(Optional<List<OpOperand>> inherited, OpOperand... added) {
        return Stream.concat(inherited.stream().flatMap(List::stream), Arrays.stream(added))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Join two operand runs, for the "fixed head then a variable tail" shape.
     *
     * @param head  the leading operands
     * @param tail  the trailing operands
     *
     * @return the combined, unmodifiable list
     */
    static List<OpOperand> concat(List<OpOperand> head, List<OpOperand> tail) {
        return Stream.concat(head.stream(), tail.stream()).toList();
    }

    /**
     * Build a fixed operand list in which absent positions are passed as null - a conditional
     * operand can then be written as an inline ternary instead of driving a mutable builder.
     *
     * @param maybeNull  the operands, in wire order, with null for any that this op omits
     *
     * @return the present operands, unmodifiable
     */
    static List<OpOperand> of(OpOperand... maybeNull) {
        return Arrays.stream(maybeNull).filter(Objects::nonNull).toList();
    }

    /**
     * As {@link #concat(Optional, OpOperand...)}, for a variable-length tail such as an argument
     * array.
     *
     * @param inherited  the superclass's operands, possibly absent
     * @param added      this level's operands
     *
     * @return the combined, unmodifiable list
     */
    static List<OpOperand> concat(Optional<List<OpOperand>> inherited, List<OpOperand> added) {
        return Stream.concat(inherited.stream().flatMap(List::stream), added.stream()).toList();
    }

    private static String specialName(int encoded) {
        return switch (encoded) {
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
            default                -> "?" + encoded;
        };
    }
}
