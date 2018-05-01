package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a Class constant, which identifies a specific class structure.
 */
public class ClassConstant
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
    public ClassConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Construct a constant whose value is a class identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the module, package, class, or method that contains this class
     * @param sName        the unqualified class name
     */
    public ClassConstant(ConstantPool pool, IdentityConstant constParent, String sName)
        {
        super(pool, constParent, sName);

        if (    !( constParent.getFormat() == Format.Module
                || constParent.getFormat() == Format.Package
                || constParent.getFormat() == Format.Class
                || constParent.getFormat() == Format.Method ))
            {
            throw new IllegalArgumentException("parent module, package, class, or method required");
            }
        }


    // ----- ClassConstant methods -----------------------------------------------------------------

    /**
     * @return return the "outermost" class that represents an auto-narrowing base
     */
    public ClassConstant getOutermost()
        {
        ClassConstant    outermost = this;
        IdentityConstant parent    = outermost.getParentConstant();
        while (true)
            {
            switch (parent.getFormat())
                {
                case Class:
                    outermost = (ClassConstant) parent;
                    break;

                case Property:
                    // ignored (we'll use its parent)
                    break;

                // methods, packages, modules all "terminate" this search
                default:
                    return outermost;
                }

            parent = parent.getParentConstant();
            }
        }

    public int getDepthFromOutermost()
        {
        int cLevelsDown = 0;
        ClassConstant    outermost = this;
        IdentityConstant parent    = outermost.getParentConstant();
        while (true)
            {
            switch (parent.getFormat())
                {
                case Class:
                    ++cLevelsDown;
                    outermost = (ClassConstant) parent;
                    break;

                case Property:
                    ++cLevelsDown;
                    break;

                // methods, packages, modules all mean we've passed the outer-most
                default:
                    return cLevelsDown;
                }

            parent = parent.getParentConstant();
            }
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Class;
        }

    @Override
    public boolean isClass()
        {
        return true;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription()
        {
        Constant constParent = getNamespace();
        while (constParent instanceof ClassConstant)
            {
            constParent = ((ClassConstant) constParent).getNamespace();
            }

        return "class=" + getValueString() + ", " + constParent.getDescription();
        }
    }
