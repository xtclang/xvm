package org.xvm.runtime;


import java.util.List;
import java.util.Map;

import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant.NestedIdentity;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * TypeComposition represents a fully resolved class (e.g. ArrayList<String> or
 * @Interval Range<Date>).
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
     * @return the underlying type of this TypeComposition; for ClassComposition the base type does
     *         not include the access aspect; for PropertyComposition - it is a BaseRef type
     */
    TypeConstant getBaseType();

    /**
     * Retrieve a TypeComposition that widens the current type to the specified type.
     *
     * Note that the underlying ClassTemplate doesn't change.
     *
     * @return the new composition or null if the operation cannot be fulfilled
     */
    TypeComposition maskAs(TypeConstant type);

    /**
     * Retrieve a TypeComposition that widens the actual type to the specified type.
     *
     * Note that the underlying ClassTemplate doesn't change.
     *
     * @return the new composition or null if the operation cannot be fulfilled
     */
    TypeComposition revealAs(TypeConstant type);

    /**
     * @return an inception TypeComposition
     */
    ObjectHandle ensureOrigin(ObjectHandle handle);

    /**
     * @return an equivalent ObjectHandle for the specified access
     */
    ObjectHandle ensureAccess(ObjectHandle handle, Access access);

    /**
     * @return an associated TypeComposition for the specified access
     */
    TypeComposition ensureAccess(Access access);

    /**
     * @return true iff the revealed type is a struct
     */
    boolean isStruct();

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

    /**
     * Create entries for all fields. Non-inflated fields will have null values; inflated
     * will contain non-initialized RefHandle objects.
     *
     * @return a map containing object fields
     */
    Map<Object, ObjectHandle> initializeStructure();

    /**
     * Check whether or not the property referred by the specified nid has a custom code or
     * Ref-annotation.
     *
     * @param nid  the property nid
     *
     * @return true iff the specified property has custom code or is Ref-annotated
     */
    boolean isInflated(Object nid);

    /**
     * Check whether or not the property referred by the specified nid is LazyVar annotated.
     *
     * @param nid  the property nid
     *
     * @return true iff the property is LazyVar annotated
     */
    boolean isLazy(Object nid);

    /**
     * Check whether or not the property referred by the specified nid is a "regular" one, meaning
     * that it's neither nested nor synthetic nor lazy.
     *
     * @param nid  the property nid
     *
     * @return true iff the property is "regular"
     */
    default boolean isRegular(Object nid)
        {
        if (nid instanceof NestedIdentity)
            {
            return false;
            }

        String sName = (String) nid;
        if (sName.charAt(0) == '$')
            {
            return false;
            }

        return !isInflated(sName) && !isLazy(sName);
        }

    /**
     * Check whether or not the property referred by the specified nid is allowed to stay
     * unassigned after the construction.
     *
     * @param nid  the property nid
     *
     * @return true if the specified property is allowed to stay unassigned
     */
    boolean isAllowedUnassigned(Object nid);

    /**
     * @return true if the specified property is injected
     */
    boolean isInjected(PropertyConstant idProp);

    /**
     * @return true if the specified property is atomic
     */
    boolean isAtomic(PropertyConstant idProp);

    /**
     * Calculate the method call chain for the specified method identity.
     *
     * @param nidMethod  the method identity (SignatureConstant or NestedIdentity)
     *
     * @return a call chain for the method
     */
    CallChain getMethodCallChain(Object nidMethod);

    /**
     * @param idProp  the property id
     *
     * @return a call chain for the specified property's getter
     */
    CallChain getPropertyGetterChain(PropertyConstant idProp);

    /**
     * @param idProp  the property nid (String | NestedIdentity)
     *
     * @return a call chain for the specified property's setter
     */
    CallChain getPropertySetterChain(PropertyConstant idProp);

    /**
     * Retrieve a field value and place it to the specified register.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property id
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    default int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        return getTemplate().getFieldValue(frame, hTarget, idProp, iReturn);
        }

    /**
     * Set a field value.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property id
     * @param hValue   the new value
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION},
     */
    default int setFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hValue)
        {
        return getTemplate().setFieldValue(frame, hTarget, idProp, hValue);
        }

    /**
     * @return a list of field names (excluding potentially unassigned and lazy)
     */
    List<String> getFieldNames();

    /**
     * @return an array of field name handles (excluding potentially unassigned and lazy)
     */
    StringHandle[] getFieldNameArray();

    /**
     * @return an array of field value handles (excluding potentially unassigned and lazy)
     */
    ObjectHandle[] getFieldValueArray(GenericHandle hValue);
    }
