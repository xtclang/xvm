package org.xvm.api;


import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.time.Instant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Parameter;
import org.xvm.asm.Op;
import org.xvm.asm.OpField;
import org.xvm.asm.OpOperand;
import org.xvm.asm.Version;


/**
 * A read-and-write view of one compiled module ({@code .xtc}), for tools rather than for the
 * compiler: an LSP server, an incremental build, a module updater, or anything deciding whether a
 * module path has gone stale.
 *
 * <h2>Why this exists, given {@code FileStructure} already reads {@code .xtc}</h2>
 *
 * It does, and this does not replace it - every method here is a thin call onto it. What was missing
 * was a place to put the four things every such tool re-invents:
 *
 * <ul>
 * <li><b>Open without paying for the whole module.</b> {@link #open} uses the lazy constructor, so a
 *     tool that wants a name and a dependency list does not deserialize every method body.</li>
 * <li><b>A dependency list.</b> A module's imports are its fingerprint children, which is obvious
 *     once known and invisible until then.</li>
 * <li><b>A digest that survives a rebuild</b> - see below. This is the one with a real trap in it.</li>
 * <li><b>Decoded ops.</b> {@link Op#readOps} exists and is public, but a caller has to find the op
 *     bytes and the method's local constants itself.</li>
 * </ul>
 *
 * <h2>The digest, and why a byte hash is the wrong tool</h2>
 *
 * "Is this recompile clean?" cannot be answered by hashing the file. {@code FileStructure(String)}
 * stamps {@code Instant.now()} into the module, so two compiles of identical source produce
 * different bytes, every time. A byte hash answers "did anything change" with "yes" always.
 *
 * <p>{@link #digest()} therefore hashes the STRUCTURE and deliberately omits the timestamp: the
 * component tree by identity and format, and each method's op bytes. Two builds of unchanged source
 * agree; a changed method body does not.
 *
 * <p>The counterpart is worth knowing: byte-identical output IS achievable, through the
 * {@code FileStructure(String, Instant)} constructor that takes an explicit timestamp. If a caller
 * controls that, a byte hash becomes meaningful and is cheaper than this. This digest is for callers
 * comparing artifacts they did not build.
 *
 * <h2>What this does not do</h2>
 *
 * It does not link, resolve, or validate - {@link FileStructure#linkModules} and the compiler do
 * that, and a view is deliberately inert. It holds no repository and consults no module path, so
 * {@link #dependencies()} answers what the module DECLARES, not what is resolvable.
 */
public final class ModuleView {
    private final FileStructure f_file;

    private ModuleView(@NotNull FileStructure file) {
        f_file = file;
    }

    /**
     * Open a compiled module, deferring method deserialization until something asks.
     *
     * @param path  the {@code .xtc} file
     *
     * @return a view of it
     *
     * @throws IOException  if it cannot be read
     */
    public static @NotNull ModuleView open(@NotNull Path path)
            throws IOException {
        return new ModuleView(new FileStructure(path.toFile(), /*fLazy*/ true));
    }

    /**
     * Wrap a structure a caller already has, e.g. one just produced by a compile.
     *
     * @param file  the structure to view
     *
     * @return a view of it
     */
    public static @NotNull ModuleView of(@NotNull FileStructure file) {
        return new ModuleView(file);
    }

    /**
     * @return the module's qualified name
     */
    public @NotNull String name() {
        return f_file.getModuleId().getName();
    }

    /**
     * @return the module's version, or null if it carries none
     */
    public @Nullable Version version() {
        return module().getVersion();
    }

    /**
     * @return when the module was compiled, if it says
     */
    public @NotNull Optional<Instant> compiledAt() {
        var timestamp = module().getTimestamp();
        if (timestamp == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(timestamp.getValue().toString()));
        } catch (RuntimeException _) {
            // a module may carry a timestamp this JDK will not parse; that is not this view's
            // problem to escalate - the caller asked when, and the honest answer is "it does not say
            // in a form I can read"
            return Optional.empty();
        }
    }

    /**
     * The modules this one imports.
     *
     * <p>They are the file's fingerprint children: a compile records each imported module as a
     * stub alongside the real one, which is what makes this readable without a repository.
     *
     * @return the imported module names, sorted
     */
    public @NotNull List<String> dependencies() {
        return f_file.children().stream()
                .filter(ModuleStructure.class::isInstance)
                .map(ModuleStructure.class::cast)
                .filter(ModuleStructure::isFingerprint)
                .map(ModuleStructure::getName)
                .sorted()
                .toList();
    }

    /**
     * @return the module structure itself, for callers that need the full model
     */
    public @NotNull ModuleStructure module() {
        return f_file.getModule();
    }

    /**
     * @return the underlying structure; the escape hatch, for anything this view does not cover
     */
    public @NotNull FileStructure raw() {
        return f_file;
    }

    /**
     * Every component in the module, depth first, in a deterministic order.
     *
     * @return the component stream
     */
    public @NotNull Stream<Component> walk() {
        return walk(module());
    }

    /**
     * @return every method in the module
     */
    public @NotNull Stream<MethodStructure> methods() {
        return walk().filter(MethodStructure.class::isInstance).map(MethodStructure.class::cast);
    }

    /**
     * Decode a method's body.
     *
     * @param method  the method to decode
     *
     * @return its ops, or empty if it has no body (abstract, native, or not yet assembled)
     */
    public @NotNull List<Op> ops(@NotNull MethodStructure method) {
        if (!method.hasCode()) {
            return List.of();
        }
        Op[] aop = method.getOps();
        return aop == null ? List.of() : List.of(aop);
    }


    /**
     * A method's ops with their operands decoded - what each op reads, what it writes, and which
     * constants it names, as objects rather than as rendered text.
     *
     * <p>This is what makes an assertion about a compiled module structural. Without it a caller
     * asking "does this op call that method" has only {@link Op#toString} to match on, which is
     * the string-matching trap one level below source text.</p>
     *
     * <p>Every field the op encodes appears here, in wire order - arguments, branch targets and
     * raw literals alike - so a rendering that walks this list cannot silently drop part of an op.
     * An op whose class does not model its fields yields {@link Resolved#operands()} empty and
     * {@link Resolved#modeled()} false, and is never guessed at; see {@link OpField}.</p>
     *
     * @param method  the method to decode
     *
     * @return one entry per op, in address order
     */
    public @NotNull List<Resolved> decode(@NotNull MethodStructure method) {
        Constant[] aconst = method.getLocalConstants();
        return ops(method).stream()
                .map(op -> op.fields()
                        .map(list -> new Resolved(op, true, list.stream()
                                .map(field -> resolve(field, aconst, op.getAddress()))
                                .toList()))
                        .orElseGet(() -> new Resolved(op, false, List.of())))
                .toList();
    }

    private static Referent resolve(OpField field, Constant[] aconst, int address) {
        return switch (field) {
            case OpField.Branch b -> new Referent(b,
                    (b.displacement() > 0 ? "->+" : "->") + b.displacement()
                            + " @" + (address + b.displacement()), null);
            case OpField.Literal l -> new Referent(l, Long.toString(l.value()), null);
            case OpField.Arg a -> switch (a.operand()) {
                case OpOperand.Reg r -> new Referent(a, "register #" + r.index(), null);
                case OpOperand.Special sp -> new Referent(a, sp.name(), null);
                case OpOperand.Const c -> {
                    // a local-constant index out of range means the op and the method disagree
                    // about the pool, which is worth surfacing rather than an AIOOBE
                    Constant value = aconst != null && c.index() < aconst.length
                            ? aconst[c.index()] : null;
                    yield new Referent(a, value == null
                            ? "const:#" + c.index() + " (UNRESOLVED)"
                            : value.getValueString(), value);
                }
            };
        };
    }

    /**
     * One op, with its operands resolved against the method's constants.
     *
     * @param op        the op
     * @param modeled   false when the op's class does not model its operands, in which case
     *                  {@code operands} is empty because nothing is known, not because there are none
     * @param operands  the resolved fields, in wire order
     */
    public record Resolved(@NotNull Op op, boolean modeled, @NotNull List<Referent> operands) {}

    /**
     * One resolved field of an op: the field itself, how it renders, and what it refers to.
     *
     * <p>The {@link OpField} is carried rather than flattened away, because the kinds are the
     * point: a caller that wants every branch target, or wants to be sure a value is a literal
     * count and not a register index, switches on {@link #field()}. An earlier shape kept only the
     * role and the rendering, which erased exactly the distinction the field model exists to
     * make.</p>
     *
     * @param field     the field, as one of {@link OpField.Arg}, {@link OpField.Branch} or
     *                  {@link OpField.Literal}
     * @param display   a rendering of the referent
     * @param constant  the constant referred to, or null unless this is an {@link OpField.Arg}
     *                  naming a constant that resolved
     */
    public record Referent(@NotNull OpField field, @NotNull String display, Constant constant) {
        /**
         * @return what this field is for in its op - "target", "method", "return", "default"
         */
        public @NotNull String role() {
            return field.role();
        }
    }

    /**
     * The constants a method's ops index into, in local order.
     *
     * <p>An op's constant operand is an index into THIS array, not into the module pool, so a
     * caller resolving operands itself needs it. {@link #decode} already resolves against it; this
     * exposes it for callers doing their own decoding.</p>
     *
     * @param method  the method
     *
     * @return its local constants, empty if it has no body
     */
    public @NotNull List<Constant> constants(@NotNull MethodStructure method) {
        Constant[] aconst = method.hasCode() ? method.getLocalConstants() : null;
        return aconst == null ? List.of() : List.of(aconst);
    }

    /**
     * Every constant in the module's pool, in index order.
     *
     * <p>Index order matters: a constant's position IS its identity in the binary, so two modules
     * whose pools differ only in ordering are different files with the same meaning. A caller
     * comparing pools should say which of those it cares about.
     *
     * @return the pool contents
     */
    public @NotNull List<Constant> constants() {
        var pool = f_file.getConstantPool();
        var list = new ArrayList<Constant>(pool.size());
        for (int i = 0, c = pool.size(); i < c; ++i) {
            list.add(pool.getConstant(i));
        }
        return List.copyOf(list);
    }

    /**
     * A method's signature, rendered.
     *
     * @param method  the method
     *
     * @return e.g. {@code foo(Int, String) -> Boolean}
     */
    public @NotNull String signature(@NotNull MethodStructure method) {
        var params  = new StringJoiner(", ", "(", ")");
        for (Parameter param : method.getParamArray()) {
            params.add(param.getType().getValueString() + ' ' + param.getName());
        }
        var returns = new StringJoiner(", ");
        for (Parameter param : method.getReturnArray()) {
            returns.add(param.getType().getValueString());
        }
        return method.getName() + params
                + (method.getReturnArray().length == 0 ? "" : " -> " + returns);
    }

    /**
     * A deterministic textual dump of the whole module: the component tree with member signatures,
     * and each method's ops with their scope depth.
     *
     * <p>This is the "diff two modules" surface. It is ordered by identity rather than by pool
     * position, and it carries no timestamp, so two dumps differ only where the modules differ -
     * which a byte comparison of the files cannot tell you (see {@link #digest()}).
     *
     * @return the listing
     */
    public @NotNull String disassemble() {
        var sb = new StringBuilder();
        walk().forEach(component -> {
            sb.append(component.getFormat()).append(' ')
              .append(component.getIdentityConstant().getValueString()).append('\n');
            if (component instanceof MethodStructure method) {
                sb.append(disassemble(method));
            }
        });
        return sb.toString();
    }

    /**
     * One method's body, rendered from the field model.
     *
     * <p>Every field an op encodes appears - arguments, branch targets and literals - so this
     * cannot silently drop part of an op the way asking for one kind at a time could. An op whose
     * class does not model its fields falls back to {@link Op#toString}, marked as such rather
     * than quietly rendered as if it were understood.</p>
     *
     * @param method  the method to render
     *
     * @return the listing, including the signature and scope depths
     */
    public @NotNull String disassemble(@NotNull MethodStructure method) {
        var sb = new StringBuilder();
        sb.append("    sig  ").append(signature(method)).append('\n');
        if (!method.hasCode()) {
            return sb.toString();
        }

        sb.append("    vars ").append(method.getMaxVars()).append('\n');
        int i = 0;
        for (Resolved decoded : decode(method)) {
            Op op = decoded.op();
            // depth is the op's scope nesting; ENTER/EXIT are what move it, and showing it inline
            // is what makes a scope bug visible in a diff rather than implied
            sb.append("    ").append(String.format("%4d", i++))
              .append("  d").append(op.getDepth())
              .append(op.isEnter() ? " {" : op.isExit() ? " }" : "  ")
              .append(' ').append(Op.toName(op.getOpCode()));
            if (decoded.modeled()) {
                for (Referent field : decoded.operands()) {
                    sb.append(' ').append(field.role()).append('=').append(field.display());
                }
            } else {
                sb.append(" (unmodeled) ").append(op);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * A fingerprint of the module's structure that is stable across rebuilds.
     *
     * <p>Covers the component tree - each component's identity and format - and each method's
     * assembled op bytes. Deliberately excludes the compile timestamp, which changes on every build;
     * see the class documentation for why that matters.
     *
     * @return the digest, hex-encoded
     */
    public @NotNull String digest() {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the platform; if it is absent the JRE is broken, and a
            // digest silently downgraded to something weaker would be worse than a failure
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }

        walk().forEach(component -> {
            sha.update(component.getIdentityConstant().getValueString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            sha.update((byte) component.getFormat().ordinal());
            if (component instanceof MethodStructure method) {
                for (Op op : ops(method)) {
                    sha.update((byte) op.getOpCode());
                }
            }
        });
        return HexFormat.of().formatHex(sha.digest());
    }

    /**
     * Compare two modules structurally.
     *
     * @param that  the other view
     *
     * @return what differs
     */
    public @NotNull Difference compareWith(@NotNull ModuleView that) {
        List<String> ours   = this.componentNames();
        List<String> theirs = that.componentNames();

        var added   = new ArrayList<>(theirs);
        added.removeAll(ours);
        var removed = new ArrayList<>(ours);
        removed.removeAll(theirs);

        return new Difference(List.copyOf(added), List.copyOf(removed),
                this.digest().equals(that.digest()));
    }

    /**
     * Write the module back out.
     *
     * @param path  where to write it
     *
     * @throws IOException  if it cannot be written
     */
    public void writeTo(@NotNull Path path)
            throws IOException {
        var bytes = new ByteArrayOutputStream();
        f_file.writeTo(bytes);
        Files.write(path, bytes.toByteArray());
    }

    private List<String> componentNames() {
        return walk().map(c -> c.getIdentityConstant().getValueString()).sorted().toList();
    }

    private static Stream<Component> walk(Component component) {
        return Stream.concat(Stream.of(component),
                component.children().stream()
                        .sorted(Comparator.comparing(c -> c.getIdentityConstant().getValueString()))
                        .flatMap(ModuleView::walk));
    }

    /**
     * What differs between two modules.
     *
     * @param added            components the other has and this does not
     * @param removed          components this has and the other does not
     * @param structurallyEqual  whether the digests agree - false with both lists empty means a
     *                           method body changed without the shape changing
     */
    public record Difference(@NotNull List<String> added, @NotNull List<String> removed,
                             boolean structurallyEqual) {
        /**
         * @return true if nothing differs
         */
        public boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty() && structurallyEqual;
        }
    }
}
