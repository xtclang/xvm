package org.xvm.asm.constants;

import java.io.DataOutput;
import java.io.IOException;

import java.util.List;
import java.util.Set;

import org.xvm.asm.ComponentResolver.ResolutionCollector;
import org.xvm.asm.ComponentResolver.ResolutionResult;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.Register;

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
    public boolean containsAutoNarrowing(boolean fAllowVirtChild)
        {
        return false;
        }

    @Override
    public boolean isOnlyNullable()
        {
        return false;
        }

    @Override
    public TypeConstant combine(ConstantPool pool, TypeConstant that)
        {
        // an intersection of the turtle type with any other tuple is that tuple
        return that.isTuple() ? that : super.combine(pool, that);
        }

    @Override
    public ResolutionResult resolveContributedName(
            String sName, Access access, MethodConstant idMethod, ResolutionCollector collector)
        {
        return ResolutionResult.UNKNOWN;
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
    public TypeConstant resolveTypeParameter(TypeConstant typeActual, String sFormalName)
        {
        return null;
        }

    @Override
    public boolean containsUnresolved()
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
    public Access getAccess()
        {
        return Access.PUBLIC;
        }

    @Override
    public boolean isSingleUnderlyingClass(boolean fAllowInterface)
        {
        return false;
        }

    @Override
    public boolean isConstant()
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
    public boolean containsTypeParameter(boolean fAllowParams)
        {
        return false;
        }

    @Override
    public boolean containsRecursiveType()
        {
        return false;
        }

    @Override
    public boolean containsFunctionType()
        {
        return false;
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
    public void collectFormalTypes(boolean fAllowParams, Set<TypeConstant> setFormal)
        {
        }

    @Override
    public boolean containsDynamicType(Register register)
        {
        return false;
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

        TypeConstant typeArray = pool.ensureArrayType(pool.typeType());
        return typeArray.ensureTypeInfoInternal(errs);
        }

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        // the formal type sequence is a sequence (UniformIndexed) of types, a Tuple of types
        if (typeLeft.isTuple())
            {
            for (int i = 0, c = typeLeft.getParamsCount(); i < c; i++)
                {
                if (!typeLeft.getParamType(i).isTypeOfType())
                    {
                    return Relation.INCOMPATIBLE;
                    }
                }

            return Relation.IS_A;
            }

        ConstantPool pool = getConstantPool();
        return pool.ensureIndexedType(pool.typeType()).isA(typeLeft)
                ? Relation.IS_A
                : Relation.INCOMPATIBLE;
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        // the formal type sequence is assignable from a tuple, a sequence or an array of types
        if (typeRight.isTuple())
            {
            return Relation.IS_A;
            }

        ConstantPool pool = getConstantPool();
        return typeRight.isA(pool.ensureIndexedType(pool.typeType()))
                ? Relation.IS_A
                : Relation.INCOMPATIBLE;
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
    public int computeHashCode()
        {
        return 42;
        }

    @Override
    public String getValueString()
        {
        return "Tuple<...>";
        }
    }