package org.xvm.runtime;


import java.util.Map;
import java.util.Set;

import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;

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

    CallChain getMethodCallChain(SignatureConstant sig);

    // retrieve the call chain for the specified property
    CallChain getPropertyGetterChain(String sProperty);

    CallChain getPropertySetterChain(String sProperty);

    // return the set of field names
    Set<String> getFieldNames();

    // return an array of field name handles
    StringHandle[] getFieldNameArray();

    // return an array of field value handles
    ObjectHandle[] getFieldValueArray(GenericHandle hValue);
    }
