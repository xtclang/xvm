package org.xvm.javajit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.xvm.asm.ModuleStructure;

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
    protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        // HACK HACK TODO: move all native classes to resources
        LoadNative:
        if (name.startsWith("org.xtclang.")) {
            if (name.startsWith(prefix)) {
                Class clz = findLoadedClass(name);
                if (clz == null) {
                    byte[] classBytes = typeSystem.xvm.nativeTypeSystem.loadNativeClass(this, name);
                    if (classBytes != null) {
                        clz = defineClass(name, classBytes, 0, classBytes.length);
                        // loadedClasses.add(clz);
                    } else {
                        break LoadNative;
                    }
                }
                if (resolve) {
                    resolveClass(clz);
                }
                return clz;
            } else {
                ModuleLoader loader = typeSystem.xvm.nativeTypeSystem.findOwnerLoader(name);
                if (loader != null) {
                    return loader.loadClass(name, resolve);
                }
                throw new ClassNotFoundException(name);
            }
        }
        return super.loadClass(name, resolve);
    }

    @Override
    protected Class<?> findClass(String name)
            throws ClassNotFoundException {
        if (name.startsWith(prefix)) {
            String suffix     = name.substring(prefix.length());
            byte[] classBytes = typeSystem.genClass(this, suffix);
            if (classBytes == null) {
                throw new ClassNotFoundException(name);
            }
            Class clz = defineClass(name, classBytes, 0, classBytes.length);
            loadedClasses.add(clz);
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

    // ----- debugging -----------------------------------------------------------------------------

    public void dump() {
        // TODO: REMOVE

        // the "dumping" itself causes the classes to be transitively loaded;
        // limit the number of cycles
        int iters = 1;
        do {
            List<Class> currentlyLoaded = new ArrayList<>(loadedClasses);
            loadedClasses.clear();
            for (Class clz : currentlyLoaded) {
                System.out.println("\n**** Class " + clz.getName());
                Class superclass = clz.getSuperclass();
                if (superclass != null) {
                    System.out.println("Extends: " + superclass);
                }
                Class[] interfaces = clz.getInterfaces();
                if (interfaces.length > 0) {
                    System.out.println("Implements:");
                    Arrays.stream(interfaces).map(s -> "  " + s).forEach(System.out::println);
                }
                System.out.println("Fields:");
                Arrays.stream(clz.getDeclaredFields()).map(s -> "  " + s).forEach(System.out::println);
                System.out.println("Methods:");
                Arrays.stream(clz.getDeclaredMethods()).map(s -> "  " + s).forEach(System.out::println);
            }
        } while (!loadedClasses.isEmpty() && --iters > 0);
    }

    private final List<Class> loadedClasses = new ArrayList<>();
}
