package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

/**
 * TODO:
 */
public class xBoolean
        extends xEnum
    {
    public static BooleanHandle TRUE;
    public static BooleanHandle FALSE;

    public xBoolean(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initDeclared()
        {
        if (f_struct.getFormat() == Component.Format.ENUM)
            {
            ConstantPool pool = f_struct.getConstantPool();

            f_templates.registerNativeTemplate(pool.typeTrue(), this);
            f_templates.registerNativeTemplate(pool.typeFalse(), this);

            FALSE = new BooleanHandle(getCanonicalClass(), false);
            TRUE = new BooleanHandle(getCanonicalClass(), true);

            pool.valTrue().setHandle(TRUE);
            pool.valFalse().setHandle(FALSE);
            }
        else
            {
            getSuper(); // this will initialize all the handles
            }
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
