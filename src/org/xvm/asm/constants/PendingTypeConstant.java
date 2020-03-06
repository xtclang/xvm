package org.xvm.asm.constants;

import java.io.DataOutput;
import java.io.IOException;

import java.util.List;

import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;

/**
 * Represents a formal type parameter constant that has not been determined yet.
 */
public class PendingTypeConstant
        extends TypeConstant
    {
    /**
     * Construct the type constant.
     */
    public PendingTypeConstant(ConstantPool pool, TypeConstant typeConstraint)
        {
        super(pool);

        m_typeConstraint = typeConstraint == null ? pool.typeObject() : typeConstraint;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public TypeConstant resolveTypedefs()
        {
        return this;
        }

    @Override
    public ResolutionResult resolveContributedName(String sName, ResolutionCollector collector)
        {
        return ResolutionResult.UNKNOWN;
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
    public boolean containsFormalType(boolean fAllowParams)
        {
        return true;
        }

    @Override
    public TypeInfo ensureTypeInfo(ErrorListener errs)
        {
        return m_typeConstraint.ensureTypeInfo(errs);
        }

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        return Relation.IS_A;
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        return typeRight.calculateRelation(m_typeConstraint);
        }

    @Override
    public boolean consumesFormalType(String sTypeName, Access access)
        {
        return false;
        }

    @Override
    protected Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        return Usage.NO;
        }

    @Override
    public boolean producesFormalType(String sTypeName, Access access)
        {
        return false;
        }

    @Override
    protected Usage checkProduction(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        return Usage.NO;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public boolean containsUnresolved()
        {
        // this will prevent this constant from being stored to the pool
        return true;
        }

    @Override
    public Format getFormat()
        {
        return Format.UnresolvedType;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return this.equals(that) ? 0 : -1;
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        throw new IllegalStateException();
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        throw new IllegalStateException();
        }

    @Override
    public boolean equals(Object that)
        {
        return that instanceof PendingTypeConstant &&
            m_typeConstraint.equals(((PendingTypeConstant) that).m_typeConstraint);
        }

    @Override
    public int hashCode()
        {
        return m_typeConstraint.hashCode();
        }

    @Override
    public String getValueString()
        {
        return "PendingTypeParameter";
        }


    // ----- data fields ---------------------------------------------------------------------------

    private TypeConstant m_typeConstraint;
    }
