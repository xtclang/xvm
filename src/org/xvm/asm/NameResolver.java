package org.xvm.asm;


import org.xvm.asm.constants.IdentityConstant;


/**
 * Allows a name resolution mechanism to be "plugged in".
 *
 * @author cp 2017.06.29
 */
public interface NameResolver
    {
    IdentityConstant resolveFirstName(String sName);
    }
