package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

/**
 * TODO:
 */
public class xBoolean
        extends Enum
    {
    public static BooleanHandle TRUE;
    public static BooleanHandle FALSE;

    public static TypeConstant TYPE;
    public static TypeConstant[] PARAMETERS;

    public xBoolean(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            TYPE = getCanonicalType();
            PARAMETERS = new TypeConstant[] {getTypeConstant()};
            }
        }

    @Override
    public void initDeclared()
        {
        markNativeMethod("to", VOID, STRING);

        ConstantPool pool = f_templates.f_container.f_pool;
        f_templates.registerNativeTemplate(pool.typeTrue(), this);
        f_templates.registerNativeTemplate(pool.typeFalse(), this);

        FALSE = new BooleanHandle(ensureCanonicalClass(), false);
        TRUE = new BooleanHandle(ensureCanonicalClass(), true);
        }

    @Override
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof SingletonConstant)
            {
            SingletonConstant constEnum = (SingletonConstant) constant;
            switch (constEnum.getValue().getName())
                {
                case "False":
                    return FALSE;

                case "True":
                    return TRUE;
                }
            }
        return null;
        }

    @Override
    public int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn,
                xString.makeHandle(hTarget == FALSE ? "False" : "True"));
        }

    public static BooleanHandle makeHandle(boolean f)
        {
        return f ? TRUE : FALSE;
        }

    public static BooleanHandle not(BooleanHandle hValue)
        {
        return hValue == FALSE ? TRUE : FALSE;
        }

    public static class BooleanHandle
                extends EnumHandle
        {
        BooleanHandle(TypeComposition clz, boolean f)
            {
            super(clz, f ? 1 : 0);
            }

        public boolean get()
            {
            return m_lValue != 0;
            }

        @Override
        public String toString()
            {
            return m_lValue == 0 ? "false" : "true";
            }
        }
    }
