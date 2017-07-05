package org.xvm.asm;


import org.xvm.asm.constants.IdentityConstant;


/**
 * Allows a name resolution mechanism to be "plugged in".
 *
 * @author cp 2017.06.29
 */
public interface NameResolver
    {
    /**
     *
     * @param sName    the name to resolve
     * @param compPOV  the "point of view": the location at which the resolution is occurring from
     *
     * @return an IdentityConstant, if the name is resolvable, or null if it is not resolvable
     */
    IdentityConstant resolveFirstName(String sName, Component compPOV);
    }
