package org.xvm.asm;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.xvm.asm.constants.VersionConstant;

/**
 * The API of a module file, as {@link FileStructure#writeApiTo} writes it, and the API of each of
 * its components apart: for each component, a digest of what the API holds of the component
 * itself, its body without its children, as the component writes it, and for each constant that
 * the body refers to, the constant's own digest. A constant's digest is its bytes as it writes
 * them, which name each constant that it refers to by its position among them, together with the
 * digest of each such constant, so a constant's digest depends on its content and never on where
 * the pool puts it. Two compiles of a module give a component the same digest when what the API
 * holds of it is the same, whatever else changed, and another digest when it is not.
 *
 * <p>What a body or a constant refers to is what it registers ({@link XvmStructure#registerConstants}), which
 * is what writing it names by position. Each is written twice, with every constant that it does
 * not refer to at two different positions; one that writes the same bytes both times named no
 * other constant, and one that does not gets a digest of its own that no other compile gives it,
 * so that it counts as changed. {@link #unstable} counts those. Each part of a digest goes in
 * after its length, so that no two sequences of parts give a digest the same bytes.
 *
 * <p>A method's digest holds whether it was abstract, native and had code, which the API keeps
 * apart from the module ({@link MethodStructure#stripToApi}). No digest holds a component's
 * children, each of which has its own, nor their order, which the file's API holds.
 */
public final class DeclarationDigests {
    /**
     * Where the constants are while a component or constant that does not refer to them is
     * written: two positions that no pool reaches, and no position a component or constant gives
     * the constants it refers to.
     */
    private static final int FIRST  = 1 << 24;
    private static final int SECOND = 1 << 25;

    private final FileStructure file;
    private final ConstantPool pool;

    /** What each component and constant refers to, in the order its bytes name them. */
    private final Map<XvmStructure, List<Constant>> references = new IdentityHashMap<>();

    /** Each component's and constant's bytes, written with the constants it does not refer to at SECOND. */
    private final Map<XvmStructure, byte[]> second = new IdentityHashMap<>();

    /** The digest of each constant whose digest does not depend on where it was reached from. */
    private final Map<Constant, String> constants = new IdentityHashMap<>();

    private final Map<Component, String> components = new IdentityHashMap<>();

    private final Map<MethodStructure, Integer> flags = new IdentityHashMap<>();

    /** The components and constants whose bytes named a constant they do not register. */
    private final Set<XvmStructure> unstable = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Makes an unstable digest one that no other compile's digest equals. */
    private final byte[] salt = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);

    private final Recorder recorder;

    private DeclarationDigests(FileStructure file) {
        this.file     = file;
        this.pool     = file.getConstantPool();
        this.recorder = new Recorder(file, pool);
    }

    // ----- public API ----------------------------------------------------------------------------

    /**
     * Strip the provided file down to its API, write the API to the provided stream, as {@link
     * FileStructure#writeApiTo} does, and digest the API of each of its components. The file is
     * left stripped, and each of its constants at the position that writing the API gave it.
     *
     * @param file  a module file, as the compiler writes it, which this strips
     * @param out   the stream to write the API to
     *
     * @return the digests of the file's components and constants
     *
     * @throws IOException  if an IOException occurs while writing the API
     */
    public static DeclarationDigests writeApiTo(FileStructure file, OutputStream out)
            throws IOException {
        DeclarationDigests digests = new DeclarationDigests(file);
        file.writeApiTo(out, digests.flags::put);
        try (var ignored = ConstantPool.withPool(file.getConstantPool())) {
            digests.digestAll();
        }
        return digests;
    }

    /**
     * @param component  a component of the file, other than the file itself
     *
     * @return the digest of what the API holds of the component itself, or null for a component
     *         that the file does not hold
     */
    public String of(Component component) {
        return components.get(component);
    }

    /**
     * @param constant  a constant of the file's pool
     *
     * @return the digest of the constant's content, the same for the same constant in any other
     *         pool
     */
    public String of(Constant constant) {
        try (var ignored = ConstantPool.withPool(pool)) {
            return digest(constant, new ArrayList<>()).digest;
        }
    }

    /**
     * @param component  a component of the file
     *
     * @return the constants that what the API holds of the component itself refers to, in the
     *         order it names them
     */
    public List<Constant> references(Component component) {
        try (var ignored = ConstantPool.withPool(pool)) {
            return Collections.unmodifiableList(referencesOf(component));
        }
    }

    /**
     * @param component  a component
     *
     * @return the component's children, each followed by its conditional variants, in the order
     *         the file holds them, as the API holds each of them apart
     */
    public static List<Component> children(Component component) {
        List<Component> children = new ArrayList<>();
        for (Component child : component.children()) {
            for (Component each = child; each != null; each = each.getNextSibling()) {
                children.add(each);
            }
        }
        return children;
    }

    /**
     * @return how many of the file's components and constants named a constant they do not
     *         register when written, each of which has a digest that no other compile gives it
     */
    public int unstable() {
        return unstable.size();
    }

    // ----- digests -------------------------------------------------------------------------------

    private void digestAll() {
        Constant[] all       = pool.getConstants();
        int[]      positions = Arrays.stream(all).mapToInt(Constant::getPosition).toArray();
        try {
            List<Component> tree = FileStructure.apiTree(file).filter(c -> c != file).toList();

            place(all, SECOND);
            for (Constant constant : all) {
                second.put(constant, bytes(constant));
            }
            for (Component component : tree) {
                second.put(component, bytes(component));
            }

            place(all, FIRST);
            for (Component component : tree) {
                components.put(component, digest(component));
            }
        } finally {
            for (int i = 0; i < all.length; ++i) {
                all[i].setPosition(positions[i]);
            }
        }
    }

    private static void place(Constant[] all, int offset) {
        for (int i = 0; i < all.length; ++i) {
            all[i].setPosition(offset + i);
        }
    }

    private String digest(Component component) {
        MessageDigest digest = sha256();
        digest.update((byte) 'D');
        byte[] bytes = bytes(component);
        update(digest, bytes);
        for (Constant constant : distinct(referencesOf(component))) {
            update(digest, digest(constant, new ArrayList<>()).digest.getBytes(StandardCharsets.UTF_8));
        }
        if (component instanceof MethodStructure method) {
            update(digest, new byte[] {flags.getOrDefault(method, -1).byteValue()});
        }
        if (!Arrays.equals(bytes, second.get(component))) {
            unstable.add(component);
            update(digest, salt);
        }
        return hex(digest);
    }

    /**
     * The digest of a constant, reached through the constants on the path, which a constant that
     * refers back to one of them names by how far back it is.
     *
     * @param constant  the constant
     * @param path      the constants being digested, outermost first, which this leaves as it was
     */
    private Digest digest(Constant constant, List<Constant> path) {
        String known = constants.get(constant);
        if (known != null) {
            return new Digest(known, Integer.MAX_VALUE);
        }
        for (int i = path.size() - 1; i >= 0; --i) {
            if (path.get(i) == constant) {
                return new Digest("back " + (path.size() - i), i);
            }
        }

        int depth = path.size();
        path.add(constant);
        MessageDigest digest = sha256();
        digest.update((byte) 'C');
        int back = Integer.MAX_VALUE;
        List<String> parts = new ArrayList<>();
        for (Constant referenced : distinct(referencesOf(constant))) {
            Digest part = digest(referenced, path);
            parts.add(part.digest);
            back = Math.min(back, part.back);
        }
        path.remove(depth);

        byte[] bytes = bytes(constant);
        update(digest, bytes);
        for (String part : parts) {
            update(digest, part.getBytes(StandardCharsets.UTF_8));
        }
        // a constant that the pool does not hold was not written at the second positions
        if (!Arrays.equals(bytes, second.get(constant))) {
            unstable.add(constant);
            update(digest, salt);
        }
        String result = hex(digest);
        // one that refers back to no constant outside it is the same wherever it is reached from
        if (back >= depth) {
            constants.put(constant, result);
        }
        return new Digest(result, back);
    }

    /**
     * The bytes that a component or constant writes, with each constant it refers to at its
     * position among them, and every other constant where it was.
     */
    private byte[] bytes(XvmStructure structure) {
        List<Constant> referenced = distinct(referencesOf(structure));
        int[]          positions  = new int[referenced.size()];
        for (int i = 0; i < positions.length; ++i) {
            positions[i] = referenced.get(i).getPosition();
        }
        try {
            for (int i = 0; i < positions.length; ++i) {
                referenced.get(i).setPosition(i);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            structure.assemble(new DataOutputStream(bytes));
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            for (int i = 0; i < positions.length; ++i) {
                referenced.get(i).setPosition(positions[i]);
            }
        }
    }

    /**
     * What a component or constant registers, which is what writing it names by position. A class
     * registers its type parameters with its own pool, so they are added here.
     */
    private List<Constant> referencesOf(XvmStructure structure) {
        return references.computeIfAbsent(structure, s -> {
            List<Constant> registered = new ArrayList<>();
            recorder.registered = registered;
            s.registerConstants(recorder);
            if (s instanceof ClassStructure clazz) {
                clazz.getTypeParamsAsList().forEach(entry -> {
                    recorder.register(entry.getKey());
                    recorder.register(entry.getValue());
                });
            }
            recorder.registered = new ArrayList<>();
            return registered;
        });
    }

    private static List<Constant> distinct(List<Constant> constants) {
        Set<Constant>  seen     = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Constant> distinct = new ArrayList<>(constants.size());
        for (Constant constant : constants) {
            if (constant != null && seen.add(constant)) {
                distinct.add(constant);
            }
        }
        return distinct;
    }

    /**
     * Add one part to a digest, after its length, so that no two sequences of parts give the same bytes.
     */
    private static void update(MessageDigest digest, byte[] part) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(part.length).array());
        digest.update(part);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The first 128 bits of the digest, in hexadecimal. */
    private static String hex(MessageDigest digest) {
        return HexFormat.of().formatHex(digest.digest(), 0, 16);
    }

    /**
     * A constant's digest, and the outermost position on the path that it refers back to, or
     * {@link Integer#MAX_VALUE} when it refers back to none.
     */
    private record Digest(String digest, int back) {
    }

    /**
     * A pool that records what is registered with it, in order, and changes nothing: it returns
     * each constant as it is, so that a registration reassigns each field to what it held.
     */
    private static final class Recorder
            extends ConstantPool {
        private final ConstantPool pool;

        /** What is registered while one component or constant registers what it refers to. */
        private List<Constant> registered = new ArrayList<>();

        Recorder(FileStructure file, ConstantPool pool) {
            super(file);
            this.pool = pool;
        }

        @Override
        public <T extends Constant> T register(T constant) {
            if (constant != null) {
                registered.add(constant);
            }
            return constant;
        }

        @Override
        public VersionConstant ensureVersionConstant(Version ver) {
            // a fingerprint writes its versions as its own pool holds them
            return register(pool.ensureVersionConstant(ver));
        }
    }
}
