package org.xvm.runtime;


import java.util.Set;

import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;


/**
 * TypeComposition for classes that could be fully computed based on their canonical class.
 *
 * Note: at the moment it's used only for Class and Type compositions.
 */
public class CanonicalizedTypeComposition
        extends DelegatingComposition
    {
    /**
     * Construct a CanonicalizedTypeComposition based on the canonical class and actual type.
     *
     * @param clzCanonical  the underlying ClassComposition for the canonical type
     * @param typeActual
     */
    public CanonicalizedTypeComposition(ClassComposition clzCanonical, TypeConstant typeActual)
        {
        super(clzCanonical);

        f_typeActual = typeActual;
        }

    /**
     * @return the underlying canonical ClassComposition
     */
    protected ClassComposition getCanonicalComposition()
        {
        return (ClassComposition) f_clzOrigin;
        }


    // ----- TypeComposition interface -------------------------------------------------------------

    @Override
    public TypeConstant getType()
        {
        return f_typeActual;
        }

    @Override
    public TypeConstant getBaseType()
        {
        return f_typeActual;
        }

    @Override
    public TypeComposition maskAs(TypeConstant type)
        {
        return this;
        }

    @Override
    public TypeComposition revealAs(TypeConstant type)
        {
        return this;
        }

    @Override
    public ObjectHandle ensureOrigin(ObjectHandle handle)
        {
        return handle;
        }

    @Override
    public ObjectHandle ensureAccess(ObjectHandle handle, Access access)
        {
        return access == f_typeActual.getAccess()
            ? handle
            : handle.cloneAs(ensureAccess(access));
        }

    @Override
    public TypeComposition ensureAccess(Access access)
        {
        TypeConstant typeActual = f_typeActual;
        return access == typeActual.getAccess()
            ? this
            : getCanonicalComposition().ensureCanonicalizedComposition(
                typeActual.getConstantPool().ensureAccessTypeConstant(typeActual, access));
        }

    @Override
    public boolean isStruct()
        {
        return f_typeActual.getAccess() == Access.STRUCT;
        }

    @Override
    public boolean isConst()
        {
        return f_clzOrigin.isConst();
        }

    @Override
    public CallChain getMethodCallChain(Object nidMethod)
        {
        CallChain chain = super.getMethodCallChain(nidMethod);
        if (chain.getDepth() == 0 && nidMethod instanceof SignatureConstant sig)
            {
            // we assume that the information on the canonical TypeInfo is sufficient
            ClassComposition    clzCanonical  = getCanonicalComposition();
            TypeInfo            infoCanonical = clzCanonical.getType().ensureTypeInfo();
            Set<MethodConstant> setMethods    = infoCanonical.findMethods(
                    sig.getName(), sig.getParamCount(), TypeInfo.MethodKind.Any);
            if (!setMethods.isEmpty())
                {
                chain = clzCanonical.getMethodCallChain(setMethods.iterator().next().getSignature());
                }
            }
        return chain;
        }


    // ----- data fields ---------------------------------------------------------------------------

    private final TypeConstant f_typeActual;
    }