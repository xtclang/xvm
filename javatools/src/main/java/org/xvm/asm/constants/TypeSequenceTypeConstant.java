package org.xvm.asm.constants;

import java.io.DataOutput;
import java.io.IOException;

import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;

/**
 * Type constant that represents а constraint for a formal type parameter that materializes into
 * a sequence (tuple) of types.
 * <p/>
 * Examples of such a constraint in Ecstasy are:
 *
 * <li>{@code interface Tuple<ElementTypes extends Tuple<ElementTypes>>}</li>
 * <li>{@code interface Method<Target, ParamTypes extends Tuple<ParamTypes>, ReturnTypes extends Tuple<ReturnTypes>>}</li>
 */
public class TypeSequenceTypeConstant
        extends TypeConstant
    {
    /**
     * Construct the type constant.
     */
    public TypeSequenceTypeConstant(ConstantPool pool)
        {
        super(pool);
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isShared(ConstantPool poolOther)
        {
        return true;
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        return this;
        }

    @Override
    public boolean isAutoNarrowing(boolean fAllowVirtChild)
        {
        return false;
        }

    @Override
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, boolean fRetainParams, TypeConstant typeTarget)
        {
        return this;
        }

    @Override
    public TypeConstant resolveGenerics(ConstantPool pool, GenericTypeResolver resolver)
        {
        return this;
        }

    @Override
    public TypeConstant resolveConstraints()
        {
        return this;
        }

    @Override
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] atypeParams)
        {
        return this;
        }

    @Override
    public TypeConstant[] collectGenericParameters()
        {
        // turtle type is not formalizable
        return null;
        }

    @Override
    public boolean containsUnresolved()
        {
        return false;
        }

    @Override
    public boolean isModifyingType()
        {
        return false;
        }

    @Override
    public boolean isImmutabilitySpecified()
        {
        return false;
        }

    @Override
    public boolean isImmutable()
        {
        return true;
        }

    @Override
    public boolean isAccessSpecified()
        {
        return false;
        }

    @Override
    public boolean isSingleUnderlyingClass(boolean fAllowInterface)
        {
        return false;
        }

    @Override
    public boolean isTypeOfType()
        {
        return false;
        }

    @Override
    public Category getCategory()
        {
        return Category.FORMAL;
        }

    @Override
    public boolean isTuple()
        {
        return true;
        }

    @Override
    public boolean isFormalTypeSequence()
        {
        return true;
        }

    @Override
    public boolean isGenericType()
        {
        return false;
        }

    @Override
    public boolean isTypeParameter()
        {
        return false;
        }

    @Override
    public boolean containsGenericType(boolean fAllowParams)
        {
        return fAllowParams;
        }

    @Override
    public boolean containsFormalType(boolean fAllowParams)
        {
        return fAllowParams;
        }

    @Override
    protected TypeConstant getGenericParamType(String sName, List<TypeConstant> listParams)
        {
        // since the formal type sequence is a sequence of types (see above).
        // we need to report the "Element" formal type
        return sName.equals("Element")
                ? getConstantPool().typeType()
                : null;
        }

    @Override
    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        // for now, let's pretend it's an Array<Type>
        ConstantPool pool = getConstantPool();

        TypeConstant typeArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeType());
        return typeArray.ensureTypeInfoInternal(errs);
        }

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        // the formal type sequence is a sequence of types, a Tuple of types
        if (typeLeft.isExplicitClassIdentity(true))
            {
            ConstantPool     pool  = getConstantPool();
            IdentityConstant idClz = typeLeft.getSingleUnderlyingClass(true);
            if (idClz.equals(pool.clzList()))
                {
                switch (typeLeft.getParamsCount())
                    {
                    case 0:
                        // Iterable iter = ElementTypes;
                        return Relation.IS_A;

                    case 1:
                        if (typeLeft.getParamType(0).equals(pool.typeType()))
                            {
                            // Iterable<Type> iter = ElementTypes;
                            return Relation.IS_A;
                            }
                        break;
                    }
                }
            else if (idClz.equals(pool.clzTuple()))
                {
                for (int i = 0, c = typeLeft.getParamsCount(); i < c; i++)
                    {
                    if (!typeLeft.getParamType(i).isA(pool.typeType()))
                        {
                        return Relation.INCOMPATIBLE;
                        }
                    }

                return Relation.IS_A;
                }
            }
        return Relation.INCOMPATIBLE;
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        // the formal type sequence is assignable from a tuple, a sequence or an array of types
        ConstantPool pool = getConstantPool();
        if (typeRight.isTuple())
            {
            return Relation.IS_A;
            }

        if (typeRight.isExplicitClassIdentity(true))
            {
            IdentityConstant idClz = typeRight.getSingleUnderlyingClass(true);
            if ((idClz.equals(pool.clzArray()) || idClz.equals(pool.clzList())) &&
                    typeRight.getParamType(0).equals(pool.typeType()))
                {
                return Relation.IS_A;
                }
            }

        return Relation.INCOMPATIBLE;
        }

    @Override
    protected Usage checkProduction(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        return Usage.NO;
        }

    @Override
    protected Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        return Usage.NO;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.TurtleType;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return this.equals(that) ? 0 : -1;
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        }

    @Override
    public boolean equals(Object that)
        {
        return that instanceof TypeSequenceTypeConstant;
        }

    @Override
    public int hashCode()
        {
        return 42;
        }

    @Override
    public String getValueString()
        {
        return "Tuple<...>";
        }
    }
