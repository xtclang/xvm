package org.xvm.asm.constants;

import java.io.DataInput;
import java.io.IOException;

import java.util.List;
import java.util.Set;

import org.xvm.asm.Component;
import org.xvm.asm.ComponentResolver.ResolutionCollector;
import org.xvm.asm.ComponentResolver.ResolutionResult;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.Register;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.template.xBoolean;
import org.xvm.util.Hash;


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
    public boolean isService()
        {
        return false;
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
    public boolean containsFunctionType()
        {
        return false;
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
    public TypeConstant resolveConstraints(boolean fPendingOnly)
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
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, boolean fRetainParams,
                                             TypeConstant typeTarget, IdentityConstant idCtx)
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
    public TypeConstant getExplicitClassInto(boolean fResolve)
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

    @Override
    protected Object getLocator()
        {
        return null;
        }


    // ----- TypeInfo support ----------------------------------------------------------------------

    @Override
    public TypeInfo ensureTypeInfoInternal(ErrorListener errs)
        {
        return getConstantPool().typeObject().ensureTypeInfo(errs);
        }


    // ----- run-time support ----------------------------------------------------------------------

    @Override
    public ClassTemplate getTemplate(Container container)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public int callEquals(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return getReferredToType().callEquals(frame, hValue1, hValue2, iReturn);
        }

    @Override
    public int callCompare(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return getReferredToType().callCompare(frame, hValue1, hValue2, iReturn);
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

    @Override
    protected int computeHashCode()
        {
        TypedefConstant constTypedef = getTypedef();
        return Hash.of(constTypedef.getParentConstant(),
               Hash.of(constTypedef.getName()));
        }
    }