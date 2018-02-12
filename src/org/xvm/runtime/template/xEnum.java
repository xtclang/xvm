package org.xvm.runtime.template;


import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.SingletonConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;


/**
 * A template for the base of all Enum classes
 */
public class xEnum
        extends xConst
    {
    public static xEnum INSTANCE;

    protected List<String> m_listNames;
    protected List<EnumHandle> m_listHandles;

    public xEnum(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        if (this == INSTANCE)
            {
            markNativeGetter("ordinal");
            markNativeGetter("name");
            }
        else if (f_struct.getFormat() == Component.Format.ENUM)
            {
            List<Component> listAll = f_struct.children();
            List<String> listNames = new ArrayList<>(listAll.size());
            List<EnumHandle> listHandles = new ArrayList<>(listAll.size());

            ConstantPool pool = f_struct.getConstantPool();
            int cValues = 0;
            for (Component child : listAll)
                {
                if (child.getFormat() == Component.Format.ENUMVALUE)
                    {
                    listNames.add(child.getName());

                    EnumHandle hValue = new EnumHandle(ensureCanonicalClass(), cValues++);
                    listHandles.add(hValue);

                    pool.ensureSingletonConstConstant(child.getIdentityConstant()).setHandle(hValue);
                    }
                }
            m_listNames = listNames;
            m_listHandles = listHandles;
            }
        else // (f_struct.getFormat() == Component.Format.ENUMVALUE)
            {
            getSuper(); // this will initialize all handles
            }
        }

    @Override
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof SingletonConstant)
            {
            return ((SingletonConstant) constant).getHandle();
            }
        return null;
        }

    @Override
    public int invokeNativeGet(Frame frame, PropertyStructure property, ObjectHandle hTarget, int iReturn)
        {
        EnumHandle hEnum = (EnumHandle) hTarget;

        switch (property.getName())
            {
            case "name":
                return frame.assignValue(iReturn,
                        xString.makeHandle(m_listNames.get((int) hEnum.getValue())));

            case "ordinal":
                return frame.assignValue(iReturn, xInt64.makeHandle(hEnum.getValue()));
            }

        return super.invokeNativeGet(frame, property, hTarget, iReturn);
        }

    @Override
    public int buildHashCode(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        EnumHandle hEnum = (EnumHandle) hTarget;

        return frame.assignValue(iReturn, xInt64.makeHandle(hEnum.getValue()));
        }

    @Override
    public int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        EnumHandle hEnum = (EnumHandle) hTarget;

        return frame.assignValue(iReturn,
                xString.makeHandle(m_listNames.get((int) hEnum.getValue())));
        }

    // ----- helper method -----

    public EnumHandle getEnumByName(String sName)
        {
        int ix = m_listNames.indexOf(sName);
        return ix >= 0 ? m_listHandles.get(ix) : null;
        }

    public EnumHandle getEnumByOrdinal(int ix)
        {
        return ix >= 0 ? m_listHandles.get(ix) : null;
        }

    // ----- ObjectHandle -----

    public static class EnumHandle
                extends JavaLong
        {
        EnumHandle(TypeComposition clz, long lIndex)
            {
            super(clz, lIndex);
            }
        }
    }
