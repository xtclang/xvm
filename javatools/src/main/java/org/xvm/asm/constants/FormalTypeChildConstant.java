package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;


/**
 * Represent a formal child of a generic property, type parameter or formal child constant.
 */
public class FormalTypeChildConstant
        extends PropertyConstant
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
    public FormalTypeChildConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Construct a constant that represents the class of a non-static child whose identity is
     * auto-narrowing.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the parent constant, which must be a FormalConstant
     * @param sName        the formal child name
     */
    public FormalTypeChildConstant(ConstantPool pool, IdentityConstant constParent, String sName)
        {
        super(pool, constParent, sName);
        }

    @Override
    protected void checkParent(IdentityConstant idParent)
        {
        switch (idParent.getFormat())
            {
            case FormalTypeChild:
            case TypeParameter:
                break;

            case Property:
                if (((PropertyConstant) idParent).isFormalType())
                    {
                    break;
                    }
                // fall through
            default:
                throw new IllegalArgumentException(
                    "parent does not represent a formal constant: " + idParent);
            }
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the top formal parent of this formal child
     */
    public IdentityConstant getTopParent()
        {
        IdentityConstant idParent = getParentConstant();
        while (idParent.getFormat() == Format.FormalTypeChild)
            {
            idParent = idParent.getParentConstant();
            }
        return idParent;
        }


    // ----- FormalConstant methods ----------------------------------------------------------------

    @Override
    public TypeConstant getConstraintType()
        {
        TypeConstant typeConstraint = m_typeConstraint;
        if (typeConstraint != null)
            {
            return typeConstraint;
            }

        FormalConstant idParent = (FormalConstant) getParentConstant();
        String         sName    = getName();

        typeConstraint = idParent.getConstraintType();

        // there is a possibility that this constant was constructed with some extra assumptions
        // during the compile time that are not "encoded" into the constant itself;
        // for example, the compiler may know that "CompileType" is an "Array", therefore
        // "CompileType.Element" is represented by a FormalTypeChildConstant, but that knowledge
        // is not encoded into the constant itself
        if (typeConstraint.containsGenericParam(sName))
            {
            if (typeConstraint.isTuple())
                {
                return m_typeConstraint = getConstantPool().typeTuple();
                }

            TypeConstant type = typeConstraint.getSingleUnderlyingClass(true).getFormalType().
                                    resolveFormalType(this);
            assert type.isGenericType();

            PropertyConstant idProp = (PropertyConstant) type.getDefiningConstant();
            return m_typeConstraint = idProp.getConstraintType();
            }

        return m_typeConstraint = sName.equals("OuterType") && typeConstraint.isVirtualChild()
                ? typeConstraint.getParentType()
                : getConstantPool().typeObject();
        }

    @Override
    public TypeConstant resolve(GenericTypeResolver resolver)
        {
        FormalConstant idParent   = (FormalConstant) getParentConstant();
        TypeConstant   typeParent = idParent.resolve(resolver);

        if (typeParent == null || typeParent.isFormalType())
            {
            return null;
            }

        TypeConstant typeResolved = typeParent.resolveFormalType(this);
        if (typeResolved == null)
            {
            // the formal parent can also be treated as a Type
            // (see NameResolver.resolveFormalDotName())
            typeResolved = typeParent.getType().resolveFormalType(this);
            }
        return typeResolved;
        }


    // ----- IdentityConstant methods --------------------------------------------------------------

    @Override
    public TypeConstant getType()
        {
        return getConstantPool().ensureTerminalTypeConstant(this);
        }

    @Override
    public IdentityConstant appendTrailingSegmentTo(IdentityConstant that)
        {
        return that.getConstantPool().ensureFormalTypeChildConstant(that, getName());
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.FormalTypeChild;
        }

    @Override
    public boolean isClass()
        {
        return false;
        }

    @Override
    public boolean containsUnresolved()
        {
        return false;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription()
        {
        return "parent=" + getParentConstant() + ", child=" + getName();
        }
    }
