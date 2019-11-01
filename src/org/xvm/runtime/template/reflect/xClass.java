package org.xvm.runtime.template.reflect;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnumeration;
import org.xvm.runtime.template.xInt64;
import org.xvm.runtime.template.xString;


/**
 * Native Class implementation.
 */
public class xClass
        extends ClassTemplate
    {
    public static xClass INSTANCE;
// TODO move to xTemplate
//    public static xEnum  CATEGORY;
//    enum Category {MODULE, PACKAGE, CLASS, CONST, ENUM, SERVICE, MIXIN, INTERFACE}

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
        if (this == INSTANCE)
            {
// TODO move to xTemplate
//            // cache Category template
//            CATEGORY = (xEnum) f_templates.getTemplate("Class.Category");

// TODO
//            markNativeProperty("name");
//            markNativeProperty("category");
//            markNativeProperty("typeParams");
//            markNativeProperty("composition");
//            markNativeProperty("classes");
//            markNativeProperty("properties");
//            markNativeProperty("methods");
//            markNativeProperty("functions");
//            markNativeProperty("isSingleton");
//            markNativeProperty("singleton");
//
//            markNativeMethod("extends_", null, BOOLEAN);

            getCanonicalType().invalidateTypeInfo();
            }
        }

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        TypeConstant typeDate = type.getParamType(0);
        return typeDate.isA(pool().typeEnum())
            ? xEnumeration.INSTANCE
            : this;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof ClassConstant)
            {
            ConstantPool  pool   = frame.poolContext();
            ClassConstant idClz  = (ClassConstant) constant;

            TypeConstant typePublic    = idClz.getType();
            TypeConstant typeProtected = pool.ensureAccessTypeConstant(typePublic, Access.PROTECTED);
            TypeConstant typePrivate   = pool.ensureAccessTypeConstant(typePublic, Access.PRIVATE);
            TypeConstant typeStruct    = pool.ensureAccessTypeConstant(typePublic, Access.STRUCT);

            ClassComposition clz = ensureParameterizedClass(pool,
                typePublic, typeProtected, typePrivate, typeStruct);

            ClassHandle hStruct = new ClassHandle(clz.ensureAccess(Access.STRUCT));

            MethodStructure constructor = f_struct.findConstructor();
            return callConstructor(frame, constructor, clz.ensureAutoInitializer(), hStruct,
                    new ObjectHandle[constructor.getMaxVars()], Op.A_STACK);
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ClassHandle      hClass   = (ClassHandle) hTarget;
        TypeConstant     typeData = hClass.getPublicType();
        IdentityConstant idClass  = (IdentityConstant) typeData.getDefiningConstant();

        switch (idProp.getName())
            {
            case "name":
                return frame.assignValue(iReturn, xString.makeHandle(idClass.getPathString()));

// TODO move to xTemplate
//            case "category":
//                {
//                int iOrdinal;
//                switch (idClass.getComponent().getFormat())
//                    {
//                    case INTERFACE:
//                        iOrdinal = Category.INTERFACE.ordinal();
//                        break;
//                    case CLASS:
//                        iOrdinal = Category.CLASS.ordinal();
//                        break;
//                    case CONST:
//                    case ENUMVALUE:
//                        iOrdinal = Category.CONST.ordinal();
//                        break;
//                    case ENUM:
//                        iOrdinal = Category.ENUM.ordinal();
//                        break;
//                    case MIXIN:
//                        iOrdinal = Category.MIXIN.ordinal();
//                        break;
//                    case SERVICE:
//                        iOrdinal = Category.SERVICE.ordinal();
//                        break;
//                    case PACKAGE:
//                        iOrdinal = Category.PACKAGE.ordinal();
//                        break;
//                    case MODULE:
//                        iOrdinal = Category.MODULE.ordinal();
//                        break;
//                    default:
//                        throw new IllegalStateException();
//                    }
//                return frame.assignValue(iReturn, CATEGORY.getEnumByOrdinal(iOrdinal));
//                }

            case "classes":
                {
                ClassStructure clzThis = (ClassStructure) idClass.getComponent();

                List<ObjectHandle> listClasses = new ArrayList<>();
                boolean            fDeferred   = false;

                for (Component child : clzThis.children())
                    {
                    if (child instanceof ClassStructure)
                        {
                        // ObjectHandle hChild = heap.ensureConstHandle(frame, child.getIdentityConstant());
                        ObjectHandle hChild = frame.getConstHandle(child.getIdentityConstant());

                        if (hChild instanceof ObjectHandle.DeferredCallHandle)
                            {
                            fDeferred = true;
                            }
                        listClasses.add(hChild);
                        }
                    }

                ConstantPool pool = frame.poolContext();

                TypeConstant typeArray = pool.ensureParameterizedTypeConstant(
                        pool.typeArray(), pool.typeClass());
                ClassComposition clzArray = f_templates.resolveClass(typeArray);

                ObjectHandle[] ahClasses = listClasses.toArray(Utils.OBJECTS_NONE);
                return frame.assignValue(iReturn, fDeferred
                    ? new ObjectHandle.DeferredArrayHandle(clzArray, ahClasses)
                    : ((xArray) clzArray.getTemplate()).createArrayHandle(clzArray, ahClasses));
                }

            case "isSingleton":
                {
                ClassStructure clzThis = (ClassStructure) idClass.getComponent();
                return frame.assignValue(iReturn, xBoolean.makeHandle(clzThis.isSingleton()));
                }

            case "singleton":
                {
                ClassStructure clzThis = (ClassStructure) idClass.getComponent();
                if (clzThis.isSingleton())
                    {
                    SingletonConstant constEnum = frame.poolContext().ensureSingletonConstConstant(idClass);
                    ObjectHandle      hEnum     = constEnum.getHandle();
                    return hEnum == null
                        ? Utils.initConstants(frame, Collections.singletonList(constEnum),
                            frameCaller -> frameCaller.assignValue(iReturn, constEnum.getHandle()))
                        : frame.assignValue(iReturn, hEnum);
                    }
                return frame.raiseException("not a singleton");
                }
            }

        return super.getPropertyValue(frame, hTarget, idProp, iReturn);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName,
                               ObjectHandle hTarget, int iReturn)
        {
        ClassHandle  hClass   = (ClassHandle) hTarget;
        TypeConstant typeData = hClass.getPublicType();

        switch (sPropName)
            {
            case "hash":
                return frame.assignValue(iReturn, xInt64.makeHandle(typeData.hashCode()));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        ClassHandle  hClass   = (ClassHandle) hTarget;
        TypeConstant typeData = hClass.getPublicType();

        switch (method.getName())
            {
            case "extends_":
                {
                IdentityConstant idThis  = (IdentityConstant) typeData.getDefiningConstant();
                ClassStructure   clzThis = (ClassStructure) idThis.getComponent();

                ClassHandle  hClassThat  = (ClassHandle) hArg;
                IdentityConstant idThat  = (IdentityConstant) hClassThat.getPublicType().getDefiningConstant();

                return frame.assignValue(iReturn,
                    xBoolean.makeHandle(clzThis.extendsClass(idThat)));
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int callEquals(Frame frame, ClassComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        ClassHandle hThis = (ClassHandle) hValue1;
        ClassHandle hThat = (ClassHandle) hValue2;

        return frame.assignValue(iReturn, xBoolean.makeHandle(hThis == hThat));
        }

    @Override
    public int callCompare(Frame frame, ClassComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        ClassHandle hThis = (ClassHandle) hValue1;
        ClassHandle hThat = (ClassHandle) hValue2;

        return frame.assignValue(iReturn, xInt64.makeHandle(hThis.getType().compareTo(hThat.getType())));
        }


    // ----- ObjectHandle -----

    public static class ClassHandle
            extends ObjectHandle.GenericHandle
        {
        protected ClassHandle(TypeComposition clzTarget)
            {
            super(clzTarget);
            }

        public TypeConstant getPublicType()
            {
            return getType().getParamType(0);
            }

        @Override
        public String toString()
            {
            return super.toString();
            }
        }

    }
