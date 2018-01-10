package org.xvm.runtime.template;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;


/**
 * TODO:
 */
public class xClass
        extends ClassTemplate
    {
    public static xClass INSTANCE;

    public xClass(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        }

    @Override
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof ClassConstant)
            {
            constant = constant.getType();
            }
        if (constant instanceof TypeConstant)
            {
            TypeConstant typeTarget = (TypeConstant) constant;

            return m_mapHandles.computeIfAbsent(typeTarget, type ->
                new ClassHandle(f_templates.resolveClass(
                    type.getPosition(), frame.getGenericsResolver())));
            }
        return null;
        }

    @Override
    public int invokeNativeGet(Frame frame, PropertyStructure property,
                               ObjectHandle hTarget, int iReturn)
        {
        ClassHandle hThis = (ClassHandle) hTarget;

        switch (property.getName())
            {
            case "hash":
                return frame.assignValue(iReturn, xInt64.makeHandle(hThis.getType().hashCode()));
            }

        return super.invokeNativeGet(frame, property, hTarget, iReturn);
        }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        ClassHandle hThis = (ClassHandle) hValue1;
        ClassHandle hThat = (ClassHandle) hValue2;

        return frame.assignValue(iReturn, xBoolean.makeHandle(hThis == hThat));
        }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        ClassHandle hThis = (ClassHandle) hValue1;
        ClassHandle hThat = (ClassHandle) hValue2;

        return frame.assignValue(iReturn, xInt64.makeHandle(hThis.getType().compareTo(hThat.getType())));
        }

    public static class ClassHandle
            extends ObjectHandle
        {
        protected ClassHandle(TypeComposition clazzTarget)
            {
            super(clazzTarget);
            }

        @Override
        public String toString()
            {
            return super.toString();
            }
        }

    // ----- data fields -----

    /**
     * Cached ClassHandle instances.
     */
    private Map<TypeConstant, ClassHandle> m_mapHandles = new ConcurrentHashMap<>();
    }
