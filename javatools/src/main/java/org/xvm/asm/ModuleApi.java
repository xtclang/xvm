package org.xvm.asm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import java.util.stream.Stream;

/**
 * What a module that imports a compiled module compiles against: the module file without its methods' code, the
 * source text of their bodies, the time it was built and the directory it was compiled from, together with whether
 * each method is abstract, native or has code.
 *
 * <p>The compiler reads no method's code or source text from the modules it compiles against, so two module files
 * with the same API compile every module that imports them to the same bytes. A build can then skip compiling the
 * importers again after a change that leaves the API the same, such as one inside a method body.
 */
public final class ModuleApi {
    private static final int ABSTRACT = 1;
    private static final int NATIVE   = 2;
    private static final int CODE     = 4;

    private ModuleApi() {
    }

    /**
     * @param module  a module file, as the compiler writes it
     *
     * @return the API of the module
     *
     * @throws IOException if the module file cannot be read
     */
    public static byte[] of(InputStream module)
            throws IOException {
        var file = new FileStructure(module, false, false);
        try (var ignored = ConstantPool.withPool(file.getConstantPool())) {
            byte[] methods = strip(file);
            var    out     = new ByteArrayOutputStream();
            file.writeTo(out);
            out.write(methods);
            return out.toByteArray();
        }
    }

    /**
     * Write the API of a module file to another file.
     *
     * @param module  a module file, as the compiler writes it
     * @param api     the file to write the API to
     *
     * @throws IOException if the module file cannot be read or the API cannot be written
     */
    public static void write(Path module, Path api)
            throws IOException {
        byte[] bytes;
        try (InputStream in = Files.newInputStream(module)) {
            bytes = of(in);
        }
        try (OutputStream out = Files.newOutputStream(api)) {
            out.write(bytes);
        }
    }

    /**
     * Remove what the API leaves out from each module in the file.
     *
     * @return for each method, whether it is abstract, native or has code, in the order the file holds the methods,
     *         which is the same for two files whose other contents are the same
     */
    static byte[] strip(FileStructure file) {
        var flags = new ByteArrayOutputStream();
        for (Component component : tree(file).toList()) {
            switch (component) {
            case ModuleStructure module -> {
                module.setTimestamp(null);
                module.setSourceDir(null);
            }
            case MethodStructure method -> flags.write(strip(method));
            default -> {
            }
            }
        }
        return flags.toByteArray();
    }

    /**
     * Drop the code of a method and the source text of its body.
     *
     * @return whether the method is abstract, native or has code
     */
    private static int strip(MethodStructure method) {
        boolean fAbstract = method.isAbstract();
        int     flags     = (fAbstract ? ABSTRACT : 0)
                          | (method.isNative() ? NATIVE : 0)
                          | (method.hasCode() ? CODE : 0);
        // marking a method abstract drops its code; it is then marked as it was
        method.setAbstract(true);
        method.setAbstract(fAbstract);
        method.configureSource(null, 0);
        return flags;
    }

    /**
     * @return the component and every component that it holds, each with its conditional variants, in the order the
     *         file holds them
     */
    private static Stream<Component> tree(Component component) {
        return Stream.concat(Stream.of(component), component.children().stream()
                .flatMap(child -> Stream.<Component>iterate(child, Objects::nonNull, Component::getNextSibling))
                .flatMap(ModuleApi::tree));
    }
}
