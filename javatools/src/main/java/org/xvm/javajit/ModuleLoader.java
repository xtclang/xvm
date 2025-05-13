package org.xvm.javajit;


import org.xvm.asm.ModuleStructure;


/**
 * A ModuleLoader is responsible for loading exactly one module, with a specified set of attributes
 * such as the module version, the presence of other modules, and a set of defined names.
 */
class ModuleLoader
        extends ClassLoader {
    /**
     * Construct a ModuleLoader. Since modules can be dependent on other modules within the same
     * TypeSystem, the parent ClassLoader is always the TypeSystemLoader responsible for introducing
     * (bringing into existence) the module.
     *
     * @param parent   the TypeSystemLoader within which the module is being loaded
     * @param module   the ModuleStructure that this loader is responsible for loading
     * @param prefix   the Java package prefix assigned to this loader and corresponding to the
     *                 module, which in theory this loader
     *                 will "own" responsibility for loading all classes under that package, and
     *                 nothing outside of that package
     * @param javaGen  Java ClassFile generator to use for this module's Ecstasy code
     */
    ModuleLoader(TypeSystemLoader parent, ModuleStructure module, String prefix, JavaGen javaGen) {
        super("xvm:" + prefix, parent);
        this.module  = module;
        this.prefix  = prefix;
        this.javaGen = javaGen;
    }

    /**
     * This is the ModuleStructure that this ClassLoader is responsible for loading as Java classes.
     */
    public final ModuleStructure module;

    /**
     * This is the package name assigned to this loader to load all of its classes within.
     */
    public final String prefix;

    /**
     * Java ClassFile generator.
     */
    public final JavaGen javaGen;

    @Override
    protected Class<?> findClass(String name)
            throws ClassNotFoundException {
        if (name.startsWith(prefix)) {
            byte[] classBytes = javaGen.genClass(module, prefix, name);
            if (classBytes == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, classBytes, 0, classBytes.length);
        } else if (getParent() instanceof TypeSystemLoader tsLoader) {
            return tsLoader.findClass(name);
        } else {
            throw new ClassNotFoundException(name);
        }
    }
}
