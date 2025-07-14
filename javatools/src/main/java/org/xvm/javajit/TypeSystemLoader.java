package org.xvm.javajit;


import java.util.Arrays;
import org.xvm.asm.ModuleStructure;

import static org.xvm.asm.Constants.ECSTASY_MODULE;
import static org.xvm.util.Handy.require;


/**
 * A ClassLoader that represents a collection of modules that form an Ecstasy TypeSystem
 */
public class TypeSystemLoader
        extends ClassLoader {
    /**
     * Create a TypeSystemLoader which aggregates some set of {@link ModuleLoader} instances.
     *
     * @param typeSystem  Java ClassFile generator to use for this type system's Ecstasy code
     * @param name        the name generated for the TypeSystemLoader
     * @param shared      the loaders that are "shared into" this type system from other type
     *                    systems and that can be delegated to in order to generate and load code
     * @param owned       the modules that this TypeSystemLoader is responsible for generating code
     *                    for and then loading that code
     */
    TypeSystemLoader(TypeSystem typeSystem, String name, ModuleLoader[] shared, ModuleStructure[] owned) {
        super("xvm:" + name, TypeSystemLoader.class.getClassLoader());
        require("typeSystem", typeSystem);
        require("name", name);
        require("shared", shared);
        require("owned", owned);

        // a type system always shares-in at least the Ecstasy module, except that the native
        // container (with nothing shared-in) is where that Ecstasy module comes from
        assert shared.length > 0 || Arrays.stream(owned).anyMatch(m -> m.getName().equals(ECSTASY_MODULE));

        this.typeSystem = typeSystem;
        this.shared     = shared;
        this.owned      = new ModuleLoader[owned.length];

        // populate the module loaders
        for (int i = 0; i < owned.length; ++i) {
            this.owned[i] = typeSystem.xvm.createModuleLoader(this, owned[i]);
        }
    }

    /**
     * The Java ClassFile generator.
     */
    public final TypeSystem typeSystem;

    /**
     * The "shared" module ClassLoaders.
     */
    public final ModuleLoader[] shared;

    /**
     * The "owned" module ClassLoaders.
     */
    public final ModuleLoader[] owned;

    @Override
    protected Class<?> findClass(String name)
            throws ClassNotFoundException {
        for (ModuleLoader loader : owned) {
            if (name.startsWith(loader.prefix)) {
                return loader.findClass(name);
            }
        }

        for (ModuleLoader loader : shared) {
            if (name.startsWith(loader.prefix)) {
                return loader.findClass(name);
            }
        }

        throw new ClassNotFoundException(name);
    }

    // ----- debugging -----------------------------------------------------------------------------

    public void dump() {
        Arrays.stream(shared).forEach(ModuleLoader::dump);
        Arrays.stream(owned).forEach(ModuleLoader::dump);
    }
}
