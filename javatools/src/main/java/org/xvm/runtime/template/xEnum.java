package org.xvm.runtime.template;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.reflect.xClass.ClassHandle;

import org.xvm.runtime.template.text.xString;


/**
 * A template for the base of all Enum classes
 */
public class xEnum
        extends xConst
    {
    public static xEnum INSTANCE;

    public xEnum(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public boolean isGenericHandle()
        {
        return true;
        }

    @Override
    public void initNative()
        {
        if (this == INSTANCE)
            {
            // all the methods are marked as native due to a "rebase"
            }
        else if (getStructure().getFormat() == Format.ENUM)
            {
            Collection<? extends Component> listAll = getStructure().children();
            List<String>     listNames   = new ArrayList<>(listAll.size());
            List<EnumHandle> listHandles = new ArrayList<>(listAll.size());

            ConstantPool pool     = f_container.getConstantPool();
            int          iOrdinal = 0;
            for (Component child : listAll)
                {
                if (child.getFormat() == Format.ENUMVALUE)
                    {
                    TypeConstant type   = ((ClassStructure) child).getCanonicalType();
                    EnumHandle   hValue = makeEnumHandle(ensureClass(pool, type, type), iOrdinal++);

                    listNames.add(child.getName());
                    listHandles.add(hValue);

                    // native enums don't require any initialization
                    if (!hValue.isStruct())
                        {
                        pool.ensureSingletonConstConstant(child.getIdentityConstant()).
                                setHandle(hValue);
                        }
                    }
                }
            m_listNames   = listNames;
            m_listHandles = listHandles;
            }
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof SingletonConstant constValue)
            {
            EnumHandle hValue = (EnumHandle) constValue.getHandle();

            if (hValue == null)
                {
                assert getStructure().getFormat() == Format.ENUMVALUE;

                xEnum templateEnum = (xEnum) getSuper();

                hValue = templateEnum.getEnumByConstant(constValue.getClassConstant());
                constValue.setHandle(hValue);

                if (hValue.isStruct())
                    {
                    return completeConstruction(frame, hValue);
                    }
                }

            return frame.pushStack(hValue);
            }

        return super.createConstHandle(frame, constant);
        }

    /**
     * Complete the construction of an Enum handle (struct) and place the result on the stack.
     *
     * @param frame    the current frame
     * @param hStruct  the enum struct handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int completeConstruction(Frame frame, EnumHandle hStruct)
        {
        assert hStruct.isStruct();

        MethodStructure ctor  = hStruct.getStructure().findConstructor(TypeConstant.NO_TYPES);
        ObjectHandle[]  ahVar = Utils.ensureSize(Utils.OBJECTS_NONE, ctor.getMaxVars());

        switch (proceedConstruction(frame, ctor, true, hStruct, ahVar, Op.A_STACK))
            {
            case Op.R_NEXT:
                hStruct.getTemplate().replaceHandle((EnumHandle) frame.peekStack());
                return Op.R_NEXT;

            case Op.R_CALL:

                Frame.Continuation contNext = frameCaller ->
                    {
                    EnumHandle hEnum = (EnumHandle) frameCaller.peekStack();
                    hEnum.getTemplate().replaceHandle(hEnum);
                    return Op.R_NEXT;
                    };
                frame.m_frameNext.addContinuation(contNext);
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        EnumHandle hEnum = (EnumHandle) hTarget;

        switch (sPropName)
            {
            case "enumeration":
                {
                IdentityConstant idValue        = (IdentityConstant) hTarget.getType().getDefiningConstant();
                ClassStructure   clzEnumeration = ((ClassStructure) idValue.getComponent()).getSuper();

                ObjectHandle hEnumeration = frame.getConstHandle(clzEnumeration.getIdentityConstant());
                return Op.isDeferred(hEnumeration)
                    ? hEnumeration.proceed(frame, frameCaller -> frame.assignValue(iReturn, frameCaller.popStack()))
                    : frame.assignValue(iReturn, hEnumeration);
                }

            case "name":
                return frame.assignValue(iReturn, xString.makeHandle(m_listNames.get(hEnum.getOrdinal())));

            case "ordinal":
                return frame.assignValue(iReturn, xInt64.makeHandle(hEnum.getOrdinal()));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        EnumHandle hEnum1 = (EnumHandle) hValue1;
        EnumHandle hEnum2 = (EnumHandle) hValue2;
        return frame.assignValue(iReturn, xBoolean.makeHandle(hEnum1.getOrdinal() == hEnum2.getOrdinal()));
        }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        EnumHandle hEnum1 = (EnumHandle) hValue1;
        EnumHandle hEnum2 = (EnumHandle) hValue2;
        return frame.assignValue(iReturn, xOrdered.makeHandle(hEnum1.getOrdinal() - hEnum2.getOrdinal()));
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        return ((EnumHandle) hValue1).getOrdinal() == ((EnumHandle) hValue2).getOrdinal();
        }

    @Override
    public int buildHashCode(Frame frame, TypeComposition clazz, ObjectHandle hTarget, int iReturn)
        {
        EnumHandle hEnum = (EnumHandle) hTarget;

        return frame.assignValue(iReturn, xInt64.makeHandle(hEnum.getOrdinal()));
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        EnumHandle hEnum = (EnumHandle) hTarget;

        return frame.assignValue(iReturn,
                xString.makeHandle(m_listNames.get(hEnum.getOrdinal())));
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Create an EnumHandle for the specified ordinal value.
     *
     * Note: this method is overridden by native enums.
     *
     * @param clz       the enum's class
     * @param iOrdinal  the ordinal value
     *
     * @return the corresponding EnumHandle
     */
    protected EnumHandle makeEnumHandle(TypeComposition clz, int iOrdinal)
        {
        // create an un-initialized struct, which will be properly initialized
        // by createConstHandle() below; overridden by native enums
        return new EnumHandle(clz.ensureAccess(Access.STRUCT), iOrdinal);
        }

    public EnumHandle getEnumByName(String sName)
        {
        int ix = m_listNames.indexOf(sName);
        return ix >= 0 ? m_listHandles.get(ix) : null;
        }

    public EnumHandle getEnumByOrdinal(int ix)
        {
        return ix >= 0 ? m_listHandles.get(ix) : null;
        }

    /**
     * @return an EnumHandle for the specified id
     */
    public EnumHandle getEnumByConstant(IdentityConstant id)
        {
        ClassStructure clzThis = getStructure();

        assert clzThis.getFormat() == Format.ENUM;

        // need an ordinal value for the enum that this represents
        int i = 0;
        for (Component child : clzThis.children())
            {
            if (child.getFormat() == Format.ENUMVALUE)
                {
                if (child.getIdentityConstant().equals(id))
                    {
                    return getEnumByOrdinal(i);
                    }
                ++i;
                }
            }
        return null;
        }

    /**
     * @return an Enum value name for the specified ordinal
     */
    public String getNameByOrdinal(int ix)
        {
        return m_listNames.get(ix);
        }

    /**
     * @return a list of enum names
     */
    public List<String> getNames()
        {
        return m_listNames;
        }

    /**
     * @return a list of enum values
     */
    public List<EnumHandle> getValues()
        {
        return m_listHandles;
        }

    /**
     * Replace a struct handle with a public const one.
     */
    private void replaceHandle(EnumHandle hEnum)
        {
        assert !hEnum.isStruct();

        hEnum.makeImmutable();

        m_listHandles.set(hEnum.m_index, hEnum);
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    /**
     * The EnumHandle.
     *
     * Note: unlike other handles, EnumHandle's template is the Enumeration (not the EnumValue).
     */
    public static class EnumHandle
                extends GenericHandle
        {
        EnumHandle(TypeComposition clz, int index)
            {
            super(clz);

            m_index    = index;
            m_fMutable = clz.isStruct();
            }

        public int getOrdinal()
            {
            return m_index;
            }

        @Override
        public boolean isNativeEqual()
            {
            return true;
            }

        @Override
        public int compareTo(ObjectHandle that)
            {
            return getOrdinal() - ((EnumHandle) that).getOrdinal();
            }

        @Override
        public int hashCode()
            {
            return m_index;
            }

        @Override
        public boolean equals(Object obj)
            {
            if (obj instanceof EnumHandle that)
                {
                return m_clazz == that.m_clazz && m_index == that.m_index;
                }
            return false;
            }

        /**
         * @return the ClassStructure for this Enum value
         */
        public ClassStructure getStructure()
            {
            xEnum  templateEnum = getTemplate();
            String sName        = templateEnum.getNameByOrdinal(m_index);
            return (ClassStructure) templateEnum.getStructure().getChild(sName);
            }

        /**
         * @return the name for this Enum value
         */
        public String getName()
            {
            return getTemplate().getNameByOrdinal(m_index);
            }

        @Override
        public xEnum getTemplate()
            {
            return (xEnum) super.getTemplate();
            }

        @Override
        public String toString()
            {
            return getName();
            }

        protected int m_index;
        }


    // ----- fields -----

    protected List<String>     m_listNames;
    protected List<EnumHandle> m_listHandles;
    protected ClassHandle      m_hEnumeration;
    }