package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.ClassConstant;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.ObjectHeap;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

import java.util.ArrayList;
import java.util.List;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xEnum
        extends ClassTemplate
    {
    public static xEnum INSTANCE;

    protected List<String> m_listNames;
    protected List<EnumHandle> m_listHandles;

    public xEnum(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        ClassStructure struct = f_struct;
        if (this != INSTANCE && struct.getFormat() == Component.Format.ENUM)
            {
            List<Component> listAll = struct.children();
            List<String> listNames = new ArrayList<>(listAll.size());
            List<EnumHandle> listHandles = new ArrayList<>(listAll.size());

            int cValues = 0;
            for (Component child : listAll)
                {
                if (child.getFormat() == Component.Format.ENUMVALUE)
                    {
                    listNames.add(child.getName());
                    listHandles.add(new EnumHandle(f_clazzCanonical, cValues++));
                    }
                }
            m_listNames = listNames;
            m_listHandles = listHandles;
            }
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
        {
        if (f_struct.getFormat() == Component.Format.ENUMVALUE)
            {
            xEnum templateEnum = (xEnum) getSuper();
            return templateEnum.createConstHandle(constant, heap);
            }

        if (constant instanceof ClassConstant)
            {
            ClassConstant constClass = (ClassConstant) constant;
            String sName = constClass.getName();
            int ix = m_listNames.indexOf(sName);
            if (ix >= 0)
                {
                return m_listHandles.get(ix);
                }
            }
        return null;
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    // ----- Object methods -----

    @Override
    public ObjectHandle.ExceptionHandle buildStringValue(ObjectHandle hTarget, StringBuilder sb)
        {
        EnumHandle hEnum = (EnumHandle) hTarget;
        sb.append(m_listNames.get((int) hEnum.getValue()));
        return null;
        }

    public static class EnumHandle
                extends JavaLong
        {
        EnumHandle(TypeComposition clz, long lIndex)
            {
            super(clz, lIndex);
            }
        }
    }
