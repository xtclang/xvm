package org.xvm.asm;


import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.VersionConstant;


/**
 * Represents the context that a linker has when linking a module into a runtime Container. The
 * context provides answers from the point of view of the module being linked, i.e. from some "this"
 * module's point of view -- including information (like version) about this module, what other
 * modules (and what versions of those modules) are available in the container, and what named
 * link-time options are specified.
 */
public interface LinkerContext
    {
    /**
     * Determine if the specified name is <i>defined</i> in this context.
     *
     * @param sName  the name
     *
     * @return true iff the name is defined
     */
    boolean isSpecified(String sName);

    /**
     * Determine if the XVM Structure specified by the passed constant is present in this context.
     *
     * @param constId  a ModuleConstant, PackageConstant, ClassConstant, PropertyConstant, or
     *                       MethodConstant.
     *
     * @return true iff the specified structure is present in this context
     */
    boolean isPresent(IdentityConstant constId);

    /**
     * Determine if the module specified by the passed constant is present in this context and of
     * the specified version, or a version substitutable therefor.
     *
     * @param constModule  a ModuleConstant to test for
     * @param constVer     the version to test for
     *
     * @return true iff the specified module is present in this context and of the specified version
     */
    boolean isVersionMatch(ModuleConstant constModule, VersionConstant constVer);

    /**
     * Tests if the version of "this" module matches the specified version.
     *
     * @param constVer  the version of this module to test for
     *
     * @return true iff the module being loaded within this context has the specified version
     */
    boolean isVersion(VersionConstant constVer);
    }

