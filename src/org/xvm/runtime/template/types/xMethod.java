package org.xvm.runtime.template.types;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Parameter;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Type;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TypeSet;

import org.xvm.runtime.template.Enum;
import org.xvm.runtime.template.xString;

import org.xvm.runtime.template.collections.xTuple;


/**
 * TODO:
 */
public class xMethod
        extends ClassTemplate
    {
    public static xMethod INSTANCE;
    public static Type TYPE;

    public static Enum ACCESS;

    public xMethod(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

        if (fInstance)
            {
            INSTANCE = this;
            TYPE = f_clazzCanonical.ensurePublicType();
            }
        }

    @Override
    public void initDeclared()
        {
        markNativeGetter("name");
        markNativeGetter("conditionalReturn");
        markNativeGetter("access");
        markNativeGetter("property");

        ACCESS = (Enum) f_types.getTemplate(f_sName + ".Access");
        }

    @Override
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof MethodConstant)
            {
            MethodConstant constMethod = (MethodConstant) constant;
            MethodStructure method = (MethodStructure) constMethod.getComponent();

            // TODO: assert if a function
            return new MethodHandle(f_clazzCanonical, method);
            }
        return null;
        }

    @Override
    public int invokeNativeGet(Frame frame, PropertyStructure property, ObjectHandle hTarget, int iReturn)
        {
        MethodHandle hMethod = (MethodHandle) hTarget;

        switch (property.getName())
            {
            case "name":
                return frame.assignValue(iReturn, xString.makeHandle(hMethod.f_method.getName()));

            case "access":
                Constants.Access access = hMethod.f_method.getAccess();
                // Constant.Access starts with "Struct"
                return frame.assignValue(iReturn, ACCESS.getEnumByOrdinal(access.ordinal() + 1));
            }
        return super.invokeNativeGet(frame, property, hTarget, iReturn);
        }

    public static MethodHandle makeHandle(MethodStructure method, TypeComposition clz, Type typeTarget)
        {
        TypeSet types = INSTANCE.f_types;

        Map<String, Type> mapActualClass = clz.f_mapGenericActual;

        Map<String, Type> mapActualMethod = new HashMap<>();
        mapActualMethod.put("TargetType", typeTarget);

        Map<String, Type> mapParams = new HashMap<>();
        for (int i = 0; i < method.getParamCount(); i++)
            {
            Parameter parameter = method.getParam(i);

            mapParams.put("ElementTypes[" + i + ']',
                    types.resolveType(parameter.getType(), mapActualClass));
            }
        mapActualMethod.put("ParamTypes", xTuple.INSTANCE.ensureClass(mapParams).ensurePublicType());

        Map<String, Type> mapReturns = new HashMap<>();
        for (int i = 0; i < method.getReturnCount(); i++)
            {
            Parameter parameter = method.getReturn(i);

            mapReturns.put("ElementTypes[" + i + ']',
                    types.resolveType(parameter.getType(), mapActualClass));
            }
        mapActualMethod.put("ReturnTypes", xTuple.INSTANCE.ensureClass(mapReturns).ensurePublicType());

        return new MethodHandle(INSTANCE.ensureClass(mapActualMethod), method);
        }

    public static class MethodHandle
            extends ObjectHandle
        {
        public final MethodStructure f_method;
        public final PropertyStructure f_property;

        protected MethodHandle(TypeComposition clazz, MethodStructure method)
            {
            super(clazz);

            f_method = method;
            f_property = null;
            }

        protected MethodHandle(TypeComposition clazz, PropertyStructure property)
            {
            super(clazz);

            f_method = null;
            f_property = property;
            }

        @Override
        public String toString()
            {
            return super.toString() + f_method;
            }
        }

    }
