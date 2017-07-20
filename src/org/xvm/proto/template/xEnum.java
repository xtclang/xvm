package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.constants.ClassTypeConstant;

import org.xvm.proto.ClassTemplate;
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
    public xEnum(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);
        }

    @Override
    public void initDeclared()
        {
        ClassStructure struct = f_struct;
        assert struct.getFormat() == Component.Format.ENUM;

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

    @Override
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
        {
        if (constant instanceof ClassTypeConstant)
            {
            ClassTypeConstant constClass = (ClassTypeConstant) constant;
            String sName = constClass.getClassConstant().getName();
            int ix = m_listNames.indexOf(sName);
            if (ix >= 0)
                {
                return m_listHandles.get(ix);
                }
            }
        return null;
        }

    public List<String> m_listNames;
    public List<EnumHandle> m_listHandles;

    public static class EnumHandle
                extends JavaLong
        {
        EnumHandle(TypeComposition clz, long lIndex)
            {
            super(clz, lIndex);
            }

        @Override
        public String toString()
            {
            return ((xEnum) f_clazz.f_template).m_listNames.get((int) m_lValue);
            }
        }
    }
