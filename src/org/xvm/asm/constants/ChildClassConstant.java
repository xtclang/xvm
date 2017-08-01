package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.ConstantPool;


/**
 * Represent an auto-narrowing non-static child class constant.
 */
public class ChildClassConstant
        extends NamedConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public ChildClassConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Construct a constant that represents the class of a non-static child whose identity is
     * auto-narrowing.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the parent class, which must be an auto-narrowing identity constant
     * @param sName        the child name
     */
    public ChildClassConstant(ConstantPool pool, IdentityConstant constParent, String sName)
        {
        super(pool, constParent, sName);

        if (!constParent.isAutoNarrowing())
            {
            throw new IllegalArgumentException("parent is not auto-narrowing: " + constParent);
            }
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the ClassTypeConstant for the public interface of this class
     */
    public ClassTypeConstant asTypeConstant()
        {
        return getConstantPool().ensureThisTypeConstant(Access.PUBLIC);
        }


    // ----- NamedConstant methods -----------------------------------------------------------------

    @Override
    public String getDescription()
        {
        // TODO need more info, but must first figure out what we want to show and how to show it
        return "child=" + getName();
        }

    // ----- IdentityConstant methods --------------------------------------------------------------

    @Override
    public boolean isAutoNarrowing()
        {
        return true;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ChildClass;
        }

    @Override
    public Object getLocator()
        {
        return getParentConstant() instanceof SymbolicConstant      // indicates "this:class"
                ? getName()
                : null;
        }
    }
