package org.xvm.asm;


import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;


/**
 * One field an {@link Op} encodes: everything it writes beyond its opcode, in wire order.
 *
 * <p>An op's persistent form is a flat run of packed ints whose meaning is positional, and the
 * kinds are not interchangeable. A register index, a branch displacement and a bare count all look
 * identical on the wire, and conflating them is the specific failure this type exists to prevent:
 * a reader must never be able to mistake "register #3" for "jump forward 3".</p>
 *
 * <h2>Why one accessor rather than several</h2>
 *
 * <p>These started as separate accessors - operands, then a jump displacement, then a branch table
 * - and each addition left the previous rendering silently incomplete, because a caller had to know
 * to ask for the new thing. Modeling {@code Nop}'s operands as empty, for instance, dropped the
 * line count that {@code toString} had been carrying. A single ordered list of self-describing
 * fields makes a dump total by construction: render every field and nothing can go missing.</p>
 *
 * <p>{@link Op#operands()} remains as a filtered view for callers that only care what an op reads
 * and writes, and is derived rather than separately implemented, so the two cannot drift.</p>
 */
public sealed interface OpField {
    /**
     * @return what this field is for in its op - "target", "method", "return", "default"
     */
    String role();

    /**
     * An encoded argument: a register, a constant, or a pseudo-register.
     *
     * @param operand  the decoded argument, which carries the role
     */
    record Arg(OpOperand operand) implements OpField {
        @Override
        public String role() {
            return operand.role();
        }
    }

    /**
     * A branch target, as a displacement in ops relative to the branching op.
     *
     * @param role          what the branch is for - "target", "default", "case[2]"
     * @param displacement  how far the branch lands from this op
     */
    record Branch(String role, int displacement) implements OpField {}

    /**
     * A raw value that is not a reference to anything: a count, a flag word, a line number.
     *
     * @param role   what the value is for
     * @param value  the value as written
     */
    record Literal(String role, long value) implements OpField {}

    // ----- construction -------------------------------------------------------------------------

    /**
     * @param role  what the argument is for
     * @param encoded  the encoded argument
     *
     * @return the argument field
     */
    static OpField arg(String role, int encoded) {
        return new Arg(OpOperand.decode(role, encoded));
    }

    /**
     * @param role   the shared role, suffixed with each index
     * @param encoded  the encoded arguments, possibly null
     *
     * @return the argument fields, empty if {@code encoded} is null
     */
    static List<OpField> args(String role, int[] encoded) {
        // the length prefix is encoded too. It is redundant with the element count, but this list
        // claims to be everything the op writes in wire order, and leaving it out is what made the
        // completeness check inexact and would block a round-trip.
        return encoded == null
                ? List.of()
                : concat(List.of(literal(role + ".count", encoded.length)),
                         OpOperand.decodeAll(role, encoded).stream().<OpField>map(Arg::new).toList());
    }

    /**
     * As {@link #args(String, int[])}, without the length prefix, for a run whose count the op
     * writes itself or does not write at all.
     *
     * @param role   the shared role, suffixed with each index
     * @param encoded  the encoded arguments, possibly null
     *
     * @return the argument fields
     */
    static List<OpField> argsUncounted(String role, int[] encoded) {
        return OpOperand.decodeAll(role, encoded).stream().<OpField>map(Arg::new).toList();
    }

    /**
     * @param role          what the branch is for
     * @param displacement  how far it lands from this op
     *
     * @return the branch field
     */
    static OpField branch(String role, int displacement) {
        return new Branch(role, displacement);
    }

    /**
     * @param role  the shared role, suffixed with each index
     * @param displacements   the displacements, possibly null
     *
     * @return the branch fields, empty if {@code displacements} is null
     */
    static List<OpField> branches(String role, int[] displacements) {
        return displacements == null
                ? List.of()
                : concat(List.of(literal(role + ".count", displacements.length)), branchesUncounted(role, displacements));
    }

    /**
     * As {@link #branches(String, int[])}, without the length prefix.
     *
     * @param role  the shared role, suffixed with each index
     * @param displacements   the displacements, possibly null
     *
     * @return the branch fields
     */
    static List<OpField> branchesUncounted(String role, int[] displacements) {
        return displacements == null
                ? List.of()
                : IntStream.range(0, displacements.length)
                        .mapToObj(i -> branch(role + '[' + i + ']', displacements[i]))
                        .toList();
    }

    /**
     * @param role   what the value is for
     * @param value  the raw value
     *
     * @return the literal field
     */
    static OpField literal(String role, long value) {
        return new Literal(role, value);
    }

    // ----- assembly -----------------------------------------------------------------------------

    /**
     * Build a field list from what a superclass contributed plus this level's own, which is the
     * shape most overrides have. Null entries are dropped, so a field this op omits can be written
     * as an inline ternary rather than driving a mutable builder.
     *
     * @param inherited  the superclass's fields, possibly absent
     * @param added      this level's fields, in wire order, with null for any that are omitted
     *
     * @return the combined, unmodifiable list
     */
    static List<OpField> concat(Optional<List<OpField>> inherited, OpField... added) {
        return Stream.concat(inherited.stream().flatMap(List::stream), Arrays.stream(added))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * As {@link #concat(Optional, OpField...)}, for a variable-length tail such as an argument
     * array.
     *
     * @param inherited  the superclass's fields, possibly absent
     * @param added      this level's fields
     *
     * @return the combined, unmodifiable list
     */
    static List<OpField> concat(Optional<List<OpField>> inherited, List<OpField> added) {
        return Stream.concat(inherited.stream().flatMap(List::stream), added.stream()).toList();
    }

    /**
     * Join two field runs, for the "fixed head then a variable tail" shape.
     *
     * @param head  the leading fields
     * @param tail  the trailing fields
     *
     * @return the combined, unmodifiable list
     */
    static List<OpField> concat(List<OpField> head, List<OpField> tail) {
        return Stream.concat(head.stream(), tail.stream()).toList();
    }

    /**
     * Build a fixed field list in which omitted positions are passed as null.
     *
     * @param maybeNull  the fields, in wire order, with null for any this op omits
     *
     * @return the present fields, unmodifiable
     */
    static List<OpField> of(OpField... maybeNull) {
        return Arrays.stream(maybeNull).filter(Objects::nonNull).toList();
    }
}
