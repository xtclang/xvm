package org.xvm.runtime;


import java.util.Map;
import java.util.Set;

import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.xString.StringHandle;


/**
 * TypeComposition represents a fully resolved class (e.g. ArrayList<String> or
 * @Range Interval<Date>).
 */
public interface TypeComposition
    {
    /**
     * @return the OpSupport for the inception type of this TypeComposition
     */
    OpSupport getSupport();

    /**
     * @return the template for the defining class for the inception type
     */
    ClassTemplate getTemplate();

    /**
     * @return the current (revealed) type of this TypeComposition
     */
    TypeConstant getType();

    /**
     * Retrieve a TypeComposition that widens the current type to the specified type.
     *
     * Note that the underlying ClassTemplate doesn't change.
     */
    TypeComposition maskAs(TypeConstant type);

    /**
     * Retrieve a TypeComposition that widens the actual type to the specified type.
     *
     * Note that the underlying ClassTemplate doesn't change.
     */
    TypeComposition revealAs(TypeConstant type, Container container);

    /**
     * @return an inception TypeComposition
     */
    ObjectHandle ensureOrigin(ObjectHandle handle);

    /**
     * @return an equivalent ObjectHandle for the specified access
     */
    default ObjectHandle ensureAccess(ObjectHandle handle, Access access)
        {
        assert handle.getComposition() == this;

        return access == getType().getAccess()
            ? handle
            : handle.cloneAs(ensureAccess(access));
        }

    /**
     * @return an associated TypeComposition for the specified access
     */
    TypeComposition ensureAccess(Access access);

    /**
     * @return true iff the revealed type is a struct
     */
    boolean isStruct();

    /**
     * @return true iff the inception type represents a service
     */
    boolean isService();

    /**
     * @return true iff the inception type represents a const
     */
    boolean isConst();

    /**
     * Retrieve an auto-generated default initializer for struct instances of this class. Return
     * null if there are no fields to initialize.
     *
     * @return the auto-generated method structure with necessary initialization code or null
     */
    MethodStructure ensureAutoInitializer();

    // create unassigned (with a null value) entries for all fields
    Map<String, ObjectHandle> createFields();

    /**
     * @return true if the specified property is Ref annotated
     */
    boolean isRefAnnotated(String sProperty);

    /**
     * @return true if the specified property is injected
     */
    boolean isInjected(String sProperty);

    /**
     * @return true if the specified property is atomic
     */
    boolean isAtomic(String sProperty);

    /**
     * @return a call chain for the specified signature
     */
    CallChain getMethodCallChain(SignatureConstant sig);

    /**
     * @return a call chain for the specified property's getter
     */
    CallChain getPropertyGetterChain(String sProperty);

    /**
     * @return a call chain for the specified property's setter
     */
    CallChain getPropertySetterChain(String sProperty);

    /**
     * Retrieve a field value and place it to the specified register.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property id
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    default int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        return getTemplate().getFieldValue(frame, hTarget, idProp.getName(), iReturn);
        }

    /**
     * Set a field value.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property id
     * @param hValue   the new value
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    default int setFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hValue)
        {
        return getTemplate().setFieldValue(frame, hTarget, idProp.getName(), hValue);
        }

    /**
     * @return a set of field names
     */
    Set<String> getFieldNames();

    /**
     * @return an array of field name handles
     */
    StringHandle[] getFieldNameArray();

    /**
     * @return an array of field value handles
     */
    ObjectHandle[] getFieldValueArray(GenericHandle hValue);
    }
