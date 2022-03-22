package org.xvm.asm;


import java.util.Objects;

import org.xvm.asm.constants.TypeConstant;


/**
 * An InjectionKey is a trivial data structure.
 */
public class InjectionKey
    {
    /**
     * Create an injection key for a given name and type
     *
     * @param sName  the name
     * @param type   the type
     */
    public InjectionKey(String sName, TypeConstant type)
        {
        assert sName != null && type != null;

        f_sName = sName;
        f_type  = type;
        }

    @Override
    public boolean equals(Object o)
        {
        if (this == o)
            {
            return true;
            }

        if (!(o instanceof InjectionKey that))
            {
            return false;
            }

        return Objects.equals(this.f_sName, that.f_sName) &&
               Objects.equals(this.f_type,  that.f_type);
        }

    @Override
    public int hashCode()
        {
        return f_sName.hashCode() + f_type.hashCode();
        }

    @Override
    public String toString()
        {
        return "Key: " + f_sName + ", " + f_type.getValueString();
        }


    // ----- data fields ---------------------------------------------------------------------------

    public static final InjectionKey[] NO_INJECTIONS = new InjectionKey[0];

    public final String       f_sName;
    public final TypeConstant f_type;
    }