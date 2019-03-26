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
 * <pre>
 *  interface Tuple<ElementTypes>
 *  const Method<TargetType, ParamTypes, ReturnTypes>
 * </pre>
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
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] atypeParams)
        {
        return this;
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
    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        // for now, let's pretend it's an Array<Type>
        ConstantPool pool = ConstantPool.getCurrentPool();

        TypeConstant typeArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeType());
        return typeArray.ensureTypeInfoInternal(errs);
        }

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        // the formal type sequence is a Sequence of types
        if (typeLeft.isSingleUnderlyingClass(true))
            {
            ConstantPool     pool  = getConstantPool();
            IdentityConstant idClz = typeLeft.getSingleUnderlyingClass(true);
            if (idClz.equals(pool.clzSequence()))
                {
                switch (typeLeft.getParamsCount())
                    {
                    case 0:
                        // Iterable iter = ElementTypes;
                        return Relation.IS_A;

                    case 1:
                        if (typeLeft.getParamTypesArray()[0].equals(pool.typeType()))
                            {
                            // Iterable<Type> iter = ElementTypes;
                            return Relation.IS_A;
                            }
                        break;
                    }
                }
            }
        return Relation.INCOMPATIBLE;
        }

    @Override
    protected TypeConstant getGenericParamType(String sName, List<TypeConstant> listParams)
        {
        // since the formal type sequence is a Sequence of types (see above).
        // we need to report the "ElementType" formal type
        return sName.equals("ElementType")
                ? getConstantPool().typeType()
                : null;
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        // the formal type sequence is a tuple of types ...
        return typeRight.isTuple() ? Relation.IS_A : Relation.INCOMPATIBLE;
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


    // ----- data fields ---------------------------------------------------------------------------
    }
