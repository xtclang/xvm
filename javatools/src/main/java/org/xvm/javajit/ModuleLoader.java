package org.xvm.javajit;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;

import java.lang.classfile.constantpool.ClassEntry;

import java.lang.reflect.AccessFlag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.function.Predicate;

import org.xvm.asm.Constant;
import org.xvm.asm.ModuleStructure;

import static org.xvm.util.Handy.isHexit;
import static org.xvm.util.Handy.require;

/**
 * A ModuleLoader is responsible for loading exactly one module with a specified set of attributes
 * such as the module version, the absence/presence of other modules, and a set of defined (or
 * explicitly undefined) names.
 */
public class ModuleLoader
        extends ClassLoader {
    /**
     * Construct a ModuleLoader. Since modules can be dependent on other modules within the same
     * TypeSystem, the parent ClassLoader is always the TypeSystemLoader responsible for introducing
     * (bringing into existence) the module.
     *
     * @param pkg         the '.'-delimited Java package name assigned to this loader and
     *                    corresponding to the module, which in theory this loader will "own"
     *                    responsibility for loading all classes under that package, and nothing
     *                    outside of that package
     * @param parent      the TypeSystemLoader within which the module is being loaded
     * @param module      the ModuleStructure that this loader is responsible for loading
     */
    ModuleLoader(TypeSystemLoader parent, ModuleStructure module, String pkg) {
        super("module:" + pkg, parent);
        require("parent", parent);
        require("module", module);
        require("pkg", pkg);
        assert pkg.indexOf('/') < 0;
        assert pkg.indexOf('.') > 0;        // no anonymous packages
        assert module.isRefined();

        this.module     = module;
        this.pkg        = pkg;
        this.prefix     = pkg + '.';
        this.typeSystem = parent.typeSystem;
    }

    /**
     * The ModuleStructure that this ClassLoader is responsible for loading as Java classes.
     */
    public final ModuleStructure module;

    /**
     * The '.'-delimited package name assigned to this loader to load all of its classes within.
     */
    public final String pkg;

    /**
     * The '.'-delimited package prefix (as used in the JVM ClassFile Specification) assigned to
     * this loader to create all of its class names beginning with. The prefix will always end with
     * a '.' character. This is the same name as {@link #pkg}.
     */
    public final String prefix;

    /**
     * The TypeSystem that this ModuleLoader is working on behalf of, and which does the Java
     * ClassFile generation for the module that this ModuleLoader is responsible for loading.
     */
    public final TypeSystem typeSystem;

    // ----- ClassLoader API -----------------------------------------------------------------------

    @Override
    protected Class<?> findClass(String name)
            throws ClassNotFoundException {
        if (name.startsWith(prefix)) {
            Class clz = findLoadedClass(name);
            if (clz != null) {
                assert clz.getClassLoader() == this;
                return clz;
            }

            String suffix     = name.substring(prefix.length());
            byte[] classBytes = typeSystem.genClass(this, suffix);
            if (classBytes == null) {
                throw new ClassNotFoundException(name);
            }
            clz = defineClass(name, classBytes, 0, classBytes.length);
            loadedClasses.put(name, classBytes);
            return clz;
        } else if (getParent() instanceof TypeSystemLoader tsLoader) {
            return tsLoader.findClass(name);
        } else {
            throw new ClassNotFoundException(name);
        }
    }

    @Override
    public String toString() {
        return module.toString();
    }

    // ----- JIT support ---------------------------------------------------------------------------

    /**
     * @return a constant at the specified index from the ConstantPool associated with this module
     */
    public Constant getConstant(int index) {
        return module.getConstantPool().getConstant(index);
    }

    /**
     * @return the xvm this module loader belongs to
     */
    public Xvm getXvm() {
        return typeSystem.xvm;
    }

    /**
     * @return the context for the current fiber
     */
    public Ctx getCtx() {
        return getXvm().getCtx();
    }

    // ----- debugging -----------------------------------------------------------------------------

    public void dump(File dir, Predicate<String> filter) {
        // TODO: REMOVE

        // the "dumping" itself causes the classes to be transitively loaded;
        // limit the number of cycles
        int iters = 2;
        do {
            Map<String, byte[]> currentlyLoaded = loadedClasses;
            loadedClasses = new HashMap<>();
            for (Map.Entry<String, byte[]> entry : currentlyLoaded.entrySet()) {
                String className = entry.getKey();
                if (!filter.test(className)) {
                    continue;
                }

                ClassModel model       = ClassFile.of().parse(entry.getValue());
                boolean    isInterface = (model.flags().flagsMask() & ClassFile.ACC_INTERFACE) != 0;
                String     fileName    = className.replace('/', '.');
                File       file        = new File(dir, fileName + ".jasm");

                try (PrintStream out = new PrintStream(new FileOutputStream(file))) {
                    out.println("\n**** " + (isInterface ? "interface " : "class ") +
                            className.replace('/', '.'));

                    model.superclass().ifPresent(ce ->
                            out.println("Extends: " + ce.asInternalName().replace('/', '.')));

                    List<ClassEntry> interfaces = model.interfaces();
                    if (!interfaces.isEmpty()) {
                        out.println("Implements:");
                        interfaces.stream()
                                .map(iface -> "  " + iface.asInternalName().replace('/', '.'))
                                .forEach(out::println);
                    }

                    out.println("Fields:");
                    model.fields()
                            .stream()
                            .map(f -> "  " + f.fieldName() + " " + f.fieldTypeSymbol().descriptorString())
                            .forEach(out::println);

                    out.println("Methods:");
                    model.methods().stream().map(m -> "  " + m.methodName() +
                                    m.methodTypeSymbol().displayDescriptor() +
                                    (m.flags().has(AccessFlag.STATIC) ? " static" : "") +
                                    (m.code().isPresent() ? "\n" + render(m.code().get().toDebugString()) : "")).
                            forEach(out::println);
                } catch (FileNotFoundException e) {
                    System.err.println(e.getMessage());
                }
            }
        } while (!loadedClasses.isEmpty() && --iters > 0);
    }

    /**
     * Fix JDK output.
     *
     * @param s  a String resulting from the ClassFile API
     *
     * @return a more readable String (sans slash-u encodings)
     */
    public static String render(String s) {
        StringBuilder sb = null;
        for (int i = 0, c = s.length(); i < c; ++i) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 5 < c && s.charAt(i+1) == 'u' && isHexit(s.charAt(i+2))
                    && isHexit(s.charAt(i+3)) && isHexit(s.charAt(i+4)) && isHexit(s.charAt(i+5))) {
                if (sb == null) {
                    sb = new StringBuilder(c).append(s, 0, i);
                }
                sb.append((char) Integer.parseInt(s, i+2, i+6, 16));
                i += 5;
            } else if (sb != null) {
                sb.append(ch);
            }
        }
        return sb == null ? s : sb.toString();
    }

    private Map<String, byte[]> loadedClasses = new HashMap<>();
}
