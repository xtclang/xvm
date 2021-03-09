package org.xvm.asm.constants;

import java.io.DataInput;
import java.io.IOException;

import java.util.List;
import java.util.Set;

import org.xvm.asm.Component;
import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.Register;

import org.xvm.runtime.OpSupport;
import org.xvm.runtime.TemplateRegistry;

/**
 * Represent a type for a recursive "typedef" constant.
 */
public class RecursiveTypeConstant
        extends TerminalTypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a recursive data type.
     *
     * @param pool     the ConstantPool that will contain this Constant
     * @param constId  a TypedefConstant this recursive constant is based on
     */
    public RecursiveTypeConstant(ConstantPool pool, TypedefConstant constId)
        {
        super(pool, constId);
        }

    /**
     * Constructor used for deserialization.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param format the format of the Constant in the stream
     * @param in     the DataInput stream to read the Constant value from
     *
     * @throws IOException if an issue occurs reading the Constant value
     */
    public RecursiveTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }


    // ----- type specific methods -----------------------------------------------------------------

    /**
     * @return the underlying TypedefConstant
     */
    public TypedefConstant getTypedef()
        {
        return (TypedefConstant) ensureResolvedConstant();
        }

    /**
     * @return the underlying type that the typedef represents
     */
    protected TypeConstant getReferredToType()
        {
        return ((TypedefConstant) ensureResolvedConstant()).getReferredToType();
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isComposedOfAny(Set<IdentityConstant> setIds)
        {
        return false;
        }

    @Override
    public boolean isImmutable()
        {
        return getReferredToType().isImmutable();
        }

    @Override
    public boolean containsGenericParam(String sName)
        {
        return false;
        }

    @Override
    public TypeConstant getGenericParamType(String sName, List<TypeConstant> listParams)
        {
        return null;
        }

    @Override
    public boolean containsRecursiveType()
        {
        return true;
        }

    @Override
    public boolean isSingleDefiningConstant()
        {
        return false;
        }

    @Override
    public Constant getDefiningConstant()
        {
        throw new IllegalStateException();
        }

    @Override
    public ResolutionResult resolveContributedName(String sName, Access access, ResolutionCollector collector)
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
    public TypeConstant resolveDynamicConstraints(Register register)
        {
        return this;
        }

    @Override
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant typeFrom)
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
        // recursive type is not formalizable
        return null;
        }

    @Override
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, boolean fRetainParams, TypeConstant typeTarget)
        {
        return this;
        }

    @Override
    public boolean isTuple()
        {
        return false;
        }

    @Override
    public boolean extendsClass(IdentityConstant constClass)
        {
        return false;
        }

    @Override
    public TypeConstant.Category getCategory()
        {
        return TypeConstant.Category.OTHER;
        }

    @Override
    public boolean isSingleUnderlyingClass(boolean fAllowInterface)
        {
        return false;
        }

    @Override
    public IdentityConstant getSingleUnderlyingClass(boolean fAllowInterface)
        {
        throw new IllegalStateException();
        }

    @Override
    public boolean isExplicitClassIdentity(boolean fAllowParams)
        {
        return false;
        }

    @Override
    public Component.Format getExplicitClassFormat()
        {
        throw new IllegalStateException();
        }

    @Override
    public TypeConstant getExplicitClassInto()
        {
        throw new IllegalStateException();
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
    public Relation calculateRelation(TypeConstant typeLeft)
        {
        return getReferredToType().calculateRelation(typeLeft);
        }

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        return getReferredToType().calculateRelationToLeft(typeLeft);
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        return getReferredToType().calculateRelationToRight(typeRight);
        }

    @Override
    public Set<SignatureConstant> isInterfaceAssignableFrom(
            TypeConstant typeRight, Access accessLeft, List<TypeConstant> listLeft)
        {
        return getConstantPool().typeObject().isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               boolean fFunction, List<TypeConstant> listParams)
        {
        return getConstantPool().typeObject().containsSubstitutableMethod(signature, access, fFunction, listParams);
        }

    @Override
    public TypeConstant.Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        return TypeConstant.Usage.NO;
        }

    @Override
    public TypeConstant.Usage checkProduction(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        return TypeConstant.Usage.NO;
        }

    // ----- TypeInfo support ----------------------------------------------------------------------

    @Override
    public TypeInfo ensureTypeInfo(IdentityConstant idClass, ErrorListener errs)
        {
        return ensureTypeInfo(errs);
        }

    @Override
    public TypeInfo ensureTypeInfoInternal(ErrorListener errs)
        {
        return getConstantPool().typeObject().ensureTypeInfo(errs);
        }


    // ----- run-time support ----------------------------------------------------------------------

    @Override
    public OpSupport getOpSupport(TemplateRegistry registry)
        {
        throw new UnsupportedOperationException();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.RecursiveType;
        }

    @Override
    public boolean containsUnresolved()
        {
        return false;
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        return this;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public boolean validate(ErrorListener errs)
        {
        return false;
        }
    }
