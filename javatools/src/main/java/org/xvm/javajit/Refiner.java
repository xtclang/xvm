package org.xvm.javajit;


import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import org.xvm.asm.Version;
import org.xvm.asm.VersionTree;

import org.xvm.asm.constants.ModuleConstant;

import static org.xvm.util.Handy.require;


/**
 * A Module Refiner is the "decision maker" for selecting among various version, dependency, and
 * conditional-define options for Module(s) being fed to a Linker.
 *
 * This class can be extended to provide customized refinement decisions for TypeSystem linking.
 */
public class Refiner {
    public static final Refiner DefaultRefiner = new Refiner();

    /**
     * Select a Version of a Module to use. An implementation may choose to throw an Exception
     * or return `null` to indicate that none of the available Module Versions is acceptable,
     * which will kill the linking process.
     *
     * @param module   the identifier of the Module (never null)
     * @param versions the available Module Versions that do not conflict with previously
     *                 discovered Version constraints (never null, never empty)
     * @param prefs    a list of preferred versions (never null, may be empty)
     *
     * @return the Version of the Module to use
     */
    @NotNull
    public Version whichVersion(ModuleConstant module, VersionTree<?> versions, List<Version> prefs) {
        require("module", module);
        require("versions", versions);
        require("prefs", prefs);

        // Select the version with the best release category. Categories are ordered by stability:
        // CI < Dev < QA < alpha < beta < rc < GA (most stable)
        return versions.stream()
                       .max(Comparator.comparing(Version::getReleaseCategory))
                       .orElseThrow();
    }

    /**
     * Make a decision whether to define or exclude the specified name.
     *
     * @param name a condition name that can be defined or not (never null)
     *
     * @return `true` to define the name; `false` to make the name _not defined_
     */
    public boolean shouldDefine(String name) {
        require("name", name);
        return false;
    }

    /**
     * Determine if the specified Module dependency should be used or excluded.
     *
     * @param module  the depended-upon Module (never null)
     * @param desired `true` iff the dependency is marked as "desired"
     *
     * @return `true` iff the dependency should be used; `false` to _disallow_ the dependency
     */
    public boolean shouldUse(ModuleConstant module, boolean desired) {
        require("module", module);
        return desired;
    }
}

