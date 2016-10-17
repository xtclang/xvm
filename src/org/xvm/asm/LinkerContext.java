package org.xvm.asm;


import org.xvm.asm.ConstantPool.VersionConstant;


/**
 * Represents the context that a linker has when linking a module into a
 * runtime Container. The context provides answers from the point of view of
 * the module being linked, i.e. from some "this" module's point of view --
 * including information (like version) about this module, what other modules
 * (and what versions of those modules) are available in the container, and
 * what named link-time options are specified.
 *
 * @author cp 2016.09.20
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
    public boolean isSpecified(String sName);

    /**
     * Determine if the XVM Structure specified by the passed constant is
     * present in this context.
     *
     * @param constVMStruct  a ModuleConstant, PackageConstant, ClassConstant,
     *                       PropertyConstant, or MethodConstant.
     *
     * @return true iff the specified structure is present in this context
     */
    public boolean isVisible(Constant constVMStruct);

    /**
     * Determine if the XVM Structure specified by the passed constant is
     * present in this context and its enclosing module is of the specified
     * version.
     *
     * @param constVMStruct  a ModuleConstant, PackageConstant, ClassConstant,
     *                       PropertyConstant, or MethodConstant
     * @param constVer       the version to require
     * @param fExactVer      true if the version must match identically
     *
     * @return true iff the specified structure is present in this context and
     *         is of the specified version
     */
    public boolean isVisible(Constant constVMStruct, VersionConstant constVer, boolean fExactVer);

    /**
     * Tests if the version of "this" module matches the specified version.
     *
     * @param constVer   the version to test for
     * @param fExactVer  true if the version must match identically
     *
     * @return true iff the module being loaded within this context matches
     *         the specified version
     */
    public boolean isVersionMatch(VersionConstant constVer, boolean fExactVer);
    }

