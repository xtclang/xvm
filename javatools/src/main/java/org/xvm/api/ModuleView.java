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
                sb.append("    sig  ").append(signature(method)).append('\n');
                if (method.hasCode()) {
                    sb.append("    vars ").append(method.getMaxVars()).append('\n');
                    int i = 0;
                    for (Op op : ops(method)) {
                        // depth is the op's scope nesting; ENTER/EXIT are what move it, and showing
                        // it inline is what makes a scope bug visible in a diff rather than implied
                        sb.append("    ").append(String.format("%4d", i++))
                          .append("  d").append(op.getDepth())
                          .append(op.isEnter() ? " {" : op.isExit() ? " }" : "  ")
                          .append(' ').append(op).append('\n');
                    }
                }
            }
        });
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
