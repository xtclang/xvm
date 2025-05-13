package org.xvm.javajit;


import org.xvm.asm.ModuleStructure;

import static org.xvm.util.Handy.append;


/**
 * A ClassLoader that represents a collection of modules that form an Ecstasy TypeSystem
 */
class TypeSystemLoader
        extends ClassLoader {
    /**
     * Create a TypeSystemLoader which aggregates some set of {@link ModuleLoader} instances.
     *
     * @param name           the name assigned to the TypeSystemLoader
     * @param sharedLoaders  the loaders that are "shared into" this type system from other type
     *                       systems
     * @param javaGen        Java ClassFile generator to use for this type system's Ecstasy code
     */
    TypeSystemLoader(String name, ModuleLoader[] sharedLoaders, JavaGen javaGen) {
        super(name, TypeSystemLoader.class.getClassLoader());
        this.javaGen = javaGen;
        if (sharedLoaders == null) {
            prefixes = new String[0];
            loaders  = new ModuleLoader[0];
        } else {
            int count = sharedLoaders.length;
            prefixes  = new String[count];
            loaders   = new ModuleLoader[count];
            for (ModuleLoader shared : sharedLoaders) {
                addModule(shared);
            }
        }
    }

    /**
     * Per-module path prefixes.
     */
    String[] prefixes;
    /**
     * The module ClassLoaders, each corresponding to an entry in `prefixes`.
     */
    ModuleLoader[] loaders;

    /**
     * Set to true when the ClassLoader is allowed to start loading classes.
     */
    boolean started;

    /**
     * Java ClassFile generator.
     */
    public final JavaGen javaGen;

    /**
     * Add a module to the type system. This method can only be called before the TypeSystemLoader
     * is {@link #started} using the {@link #start()} method.
     *
     * @param module  the module to include in the type system
     * @param prefix  the prefix to use for all classes from the provided module
     *
     * @return the {@link ModuleLoader} created for the module
     */
    ModuleLoader includeModule(ModuleStructure module, String prefix) {
        assert !started;
        ModuleLoader child = new ModuleLoader(this, module, prefix, javaGen);
        addModule(child);
        return child;
    }

    /**
     * Add the passed module to the `loaders` and `prefixes`
     *
     * @param loader  the ModuleLoader for the module to add to this type system
     */
    private void addModule(ModuleLoader loader) {
        assert !started;
        assert loader != null;
        String prefix = loader.prefix;
        assert prefix != null;
        for (int i = 0, c = loaders.length; i < c; ++i) {
            ModuleLoader thatLoader = loaders[i];
            if (thatLoader == null) {
                // the arrays were presized and contain nulls
                loaders [i] = loader;
                prefixes[i] = prefix;
                return;
            } else {
                assert loader != thatLoader;
                assert !prefix.startsWith(thatLoader.prefix) && !thatLoader.prefix.startsWith(prefix);
            }
        }
        prefixes = append(prefixes, prefix);
        loaders  = append(loaders,  loader);
    }

    /**
     * Signals the `TypeSystemLoader` that it is now allowed to start loading classes when requested
     * to do so. Up until this point, all `ClassLoader` requests to the `TypeSystemLoader` are
     * delegated to the parent without any evaluation by the `TypeSystemLoader`.
     */
    void start() {
        if (!started) {
            // make sure everything looks correct, e.g. all pre-sided elements filled
            assert loaders.length > 0 && loaders[loaders.length-1] != null;
            assert prefixes.length == loaders.length && prefixes[prefixes.length-1] != null;
            // note: eventually, we can optimize the prefix path search by picking a discriminator
            //       at this point, since all the prefixes are known; for example, selecting a
            //       particular offset in the path of a char that (all or mostly) differs from
            //       prefix to prefix
            started = true;
        }
    }

    @Override
    protected Class<?> findClass(String name)
            throws ClassNotFoundException {
        if (started) {
            for (int i = 0, c = prefixes.length; i < c; ++i) {
                if (name.startsWith(prefixes[i])) {
                    return loaders[i].findClass(name);
                }
            }
        }
        throw new ClassNotFoundException(name);
    }
}
