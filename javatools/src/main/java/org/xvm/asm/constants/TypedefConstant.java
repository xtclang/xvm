package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.TypedefStructure;


/**
 * Represent a "typedef" constant, which identifies a specific typedef structure.
 */
public class TypedefConstant
        extends NamedConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a typedef identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the structure that contains the typedef
     * @param sName        the typedef name
     */
    public TypedefConstant(ConstantPool pool, IdentityConstant constParent, String sName)
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

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public TypedefConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the underlying type that the typedef represents (note that this is a "static" type
     *         and does not reflect the type parameters of the containing runtime type)
     */
    public TypeConstant getReferredToType()
        {
        TypeConstant typeReferred = ((TypedefStructure) getComponent()).getType();
        if (!m_fInitialized)
            {
            ConstantPool    pool      = getConstantPool();
            TypedefConstant constSelf = this;

            Consumer<Constant> visitor = new Consumer<>()
                {
                public void accept(Constant constant)
                    {
                    if (constant instanceof UnresolvedTypeConstant)
                        {
                        UnresolvedTypeConstant typeU = (UnresolvedTypeConstant) constant;
                        if (typeU.isSingleDefiningConstant())
                            {
                            constant = typeU.getDefiningConstant();
                            if (constant == constSelf)
                                {
                                TypeConstant typeRecursive = new RecursiveTypeConstant(pool, constSelf);

                                typeU.resolve(typeRecursive);
                                typeU.markRecursive();
                                return;
                                }
                            }
                        }

                    if (constant instanceof TypeConstant)
                        {
                        constant.forEachUnderlying(this);
                        }
                    }
                };

            typeReferred.forEachUnderlying(visitor);

            m_fInitialized = !typeReferred.containsUnresolved();
            }
        return typeReferred.resolveTypedefs();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Typedef;
        }

    @Override
    public boolean containsUnresolved()
        {
        TypedefStructure typedef = (TypedefStructure) getComponent();
        return super.containsUnresolved() || typedef == null;
        }

    @Override
    public TypedefStructure relocateNestedIdentity(ClassStructure clz)
        {
        Component parent = getNamespace().relocateNestedIdentity(clz);
        if (parent == null)
            {
            return null;
            }

        Component that = parent.getChild(this.getName());
        return that instanceof TypedefStructure
                ? (TypedefStructure) that
                : null;
        }

    @Override
    public TypedefConstant ensureNestedIdentity(ConstantPool pool, IdentityConstant that)
        {
        return pool.ensureTypedefConstant(
                getParentConstant().ensureNestedIdentity(pool, that), getName());
        }

    @Override
    public IdentityConstant appendTrailingSegmentTo(IdentityConstant that)
        {
        return that.getConstantPool().ensureTypedefConstant(that, getName());
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription()
        {
        return "typedef name=" + getValueString();
        }

    /**
     * Indicates whether or not this typedef has been initialized.
     */
    private boolean m_fInitialized;
    }