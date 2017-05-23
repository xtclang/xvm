package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a Package constant. A Package constant is composed of a constant identifying the Module
 * or Package which contains this package, and the unqualified name of this Package. A Module can be
 * contained within another Module (either by reference or by embedding), in which case it is
 * represented as a Package; in this case, the Package constant will have a reference to the
 * corresponding Module constant.
 */
public class PackageConstant
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
    public PackageConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Construct a constant whose value is a package identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the module or package that contains this package
     * @param sName        the unqualified package name
     */
    public PackageConstant(ConstantPool pool, Constant constParent, String sName)
        {
        super(pool, constParent, sName);

        if (  !(constParent.getFormat() == Format.Module ||
                constParent.getFormat() == Format.Package))
            {
            throw new IllegalArgumentException("parent module or package required");
            }
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Package;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription()
        {
        Constant constParent = getNamespace();
        while (constParent instanceof PackageConstant)
            {
            constParent = ((PackageConstant) constParent).getNamespace();
            }

        return "package=" + getValueString() + ", " + constParent.getDescription();
        }
    }
