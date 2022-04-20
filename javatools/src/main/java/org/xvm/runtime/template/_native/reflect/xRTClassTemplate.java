package org.xvm.runtime.template._native.reflect;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;


/**
 * Native RTClassTemplate implementation.
 */
public class xRTClassTemplate
        extends xRTComponentTemplate
    {
    public static xRTClassTemplate INSTANCE;

    public xRTClassTemplate(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        if (this == INSTANCE)
            {
            ConstantPool   pool      = pool();
            Container      container = f_container;
            ClassStructure struct    = f_struct;

            TypeConstant typeClassTemplate = pool.ensureEcstasyTypeConstant("reflect.ClassTemplate");

            CLASS_TEMPLATE_COMP = ensureClass(getCanonicalType(), typeClassTemplate);
            CONTRIBUTION_COMP   = container.resolveClass(
                pool.ensureEcstasyTypeConstant("reflect.ClassTemplate.Composition.Contribution"));

            ACTION = (xEnum) container.getTemplate("reflect.ClassTemplate.Composition.Action");

            CREATE_CONTRIB_METHOD         = struct.findMethod("createContribution", 6);
            CREATE_TYPE_PARAMETERS_METHOD = struct.findMethod("createTypeParameters", 2);

            markNativeProperty("implicitName");
            markNativeProperty("classes");
            markNativeProperty("contribs");
            markNativeProperty("mixesInto");
            markNativeProperty("multimethods");
            markNativeProperty("properties");
            markNativeProperty("singleton");
            markNativeProperty("hasDefault");
            markNativeProperty("sourceInfo");
            markNativeProperty("type");
            markNativeProperty("typeParams");
            markNativeProperty("virtualChild");

            markNativeMethod("deannotate", null, null);
            markNativeMethod("ensureClass", null, null);

            // this native implementation explicitly incorporates the native implementation of
            // RTComponentTemplate
            super.initNative();
            }
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ComponentTemplateHandle hComponent = (ComponentTemplateHandle) hTarget;
        switch (sPropName)
            {
            case "implicitName":
                return getPropertyImplicitName(frame, hComponent, iReturn);

            case "classes":
                return getPropertyClasses(frame, hComponent, iReturn);

            case "contribs":
                return getPropertyContribs(frame, hComponent, iReturn);

            case "mixesInto":
                return getPropertyMixesInto(frame, hComponent, iReturn);

            case "multimethods":
                return getPropertyMultimethods(frame, hComponent, iReturn);

            case "properties":
                return getPropertyProperties(frame, hComponent, iReturn);

            case "singleton":
                return getPropertySingleton(frame, hComponent, iReturn);

            case "hasDefault":
                return getPropertyHasDefault(frame, hComponent, iReturn);

            case "sourceInfo":
                return getPropertySourceInfo(frame, hComponent, iReturn);

            case "type":
                return getPropertyType(frame, hComponent, iReturn);

            case "typeParams":
                return getPropertyTypeParams(frame, hComponent, iReturn);

            case "virtualChild":
                return getPropertyVirtualChild(frame, hComponent, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        ComponentTemplateHandle hComponent = (ComponentTemplateHandle) hTarget;
        switch (method.getName())
            {
            case "ensureClass":
                return invokeEnsureClass(frame, hComponent, iReturn);
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        ComponentTemplateHandle hComponent = (ComponentTemplateHandle) hTarget;
        switch (method.getName())
            {
            case "deannotate":
                return invokeDeannotate(frame, hComponent, aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: implicitName.get()
     */
    public int getPropertyImplicitName(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure   clz   = (ClassStructure) hComponent.getComponent();
        IdentityConstant idClz = clz.getIdentityConstant();
        if (idClz instanceof ClassConstant)
            {
            String sAlias = ((ClassConstant) idClz).getImplicitImportName();
            if (sAlias != null)
                {
                return frame.assignValue(iReturn, xString.makeHandle(sAlias));
                }
            }
        return frame.assignValue(iReturn, xNullable.NULL);
        }

    /**
     * Implements property: classes.get()
     */
    public int getPropertyClasses(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz = (ClassStructure) hComponent.getComponent();
        if (!clz.getFileStructure().isLinked())
            {
            return frame.raiseException(xException.illegalState(frame, "FileTemplate is not resolved"));
            }

        List<ComponentTemplateHandle> listTemplates = new ArrayList<>();
        for (Component child : clz.children())
            {
            switch (child.getFormat())
                {
                case INTERFACE:
                case CLASS:
                case CONST:
                case ENUM:
                case ENUMVALUE:
                case MIXIN:
                case SERVICE:
                    listTemplates.add(xRTClassTemplate.makeHandle((ClassStructure) child));
                    break;
                }
            }

        ArrayHandle hArray = xArray.createImmutableArray(
                ensureClassTemplateArrayComposition(),
                listTemplates.toArray(NO_TEMPLATES));
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: contribs.get()
     */
    public int getPropertyContribs(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz = (ClassStructure) hComponent.getComponent();
        if (!clz.getFileStructure().isLinked())
            {
            return frame.raiseException(xException.illegalState(frame, "FileTemplate is not resolved"));
            }

        List<Contribution>  listContrib = clz.getContributionsAsList();
        Utils.ValueSupplier supplier    = (frameCaller, index) ->
            {
            ConstantPool pool        = frameCaller.poolContext();
            Contribution contrib     = listContrib.get(index);
            TypeConstant typeContrib = contrib.getTypeConstant();
            ObjectHandle haParams    = xNullable.NULL;
            ObjectHandle hDelegatee  = xNullable.NULL; // TODO
            ObjectHandle haNames     = xNullable.NULL;
            ObjectHandle haTypes     = xNullable.NULL;

            String sAction;
            switch (contrib.getComposition())
                {
                case Annotation:
                    {
                    Annotation anno    = contrib.getAnnotation();
                    Constant[] aParam  = anno.getParams();
                    int        cParams = aParam.length;

                    ObjectHandle[] ahParam;
                    if (cParams == 0)
                        {
                        ahParam = Utils.OBJECTS_NONE;
                        }
                    else
                        {
                        ahParam = new ObjectHandle[cParams];
                        for (int i = 0; i < cParams; i++)
                            {
                            ahParam[i] = frame.getConstHandle(aParam[i]);
                            }
                        // TODO GG: handle deferred
                        }

                    haParams = xArray.makeObjectArrayHandle(ahParam, Mutability.Constant);
                    sAction  = "AnnotatedBy";
                    break;
                    }
                case Extends:
                    sAction = "Extends";
                    break;
                case Implements:
                    sAction = "Implements";
                    break;
                case Delegates:
                    sAction = "Delegates";
                    break;
                case Into:
                    sAction = "MixesInto";
                    break;
                case Incorporates:
                    {
                    Map<StringConstant, TypeConstant> mapConstraints = contrib.getTypeParams();
                    int cConstraints = mapConstraints == null ? 0 : mapConstraints.size();
                    if (cConstraints > 0)
                        {
                        StringHandle[] ahNames = new StringHandle[cConstraints];
                        TypeHandle[]   ahTypes = new TypeHandle[cConstraints];
                        int            i = 0;
                        for (Map.Entry<StringConstant, TypeConstant> entry : mapConstraints.entrySet())
                            {
                            String       sName = entry.getKey().getValue();
                            TypeConstant type  = entry.getValue();

                            ahNames[i] = xString.makeHandle(sName);
                            ahTypes[i] = type.ensureTypeHandle(pool);
                            i++;
                            }
                        haNames = xArray.makeStringArrayHandle(ahNames);
                        haTypes = xArray.createImmutableArray(
                                    xRTType.ensureTypeArrayComposition(), ahTypes);
                        }
                    sAction = "Incorporates";
                    break;
                    }
                default:
                    throw new IllegalStateException();
                }

            ObjectHandle[] ahVar = new ObjectHandle[CREATE_CONTRIB_METHOD.getMaxVars()];
            ahVar[0] = Utils.ensureInitializedEnum(frameCaller, ACTION.getEnumByName(sAction));
            ahVar[1] = typeContrib.ensureTypeHandle(pool);
            ahVar[2] = haParams;
            ahVar[3] = hDelegatee;
            ahVar[4] = haNames;
            ahVar[5] = haTypes;

            return frameCaller.call1(CREATE_CONTRIB_METHOD, hComponent, ahVar, Op.A_STACK);
            };

        return xArray.createAndFill(frame, ensureContribArrayComposition(),
                listContrib.size(), supplier, iReturn);
        }

    /**
     * Implements property: mixesInto.get()
     */
    public int getPropertyMixesInto(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        return frame.raiseException("Not implemented");
        }

    /**
     * Implements property: multimethods.get()
     */
    public int getPropertyMultimethods(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz = (ClassStructure) hComponent.getComponent();

        List<ComponentTemplateHandle> listHandles = new ArrayList<>();
        for (Component child : clz.children())
            {
            if (child instanceof MultiMethodStructure)
                {
                listHandles.add(makeComponentHandle(child));
                }
            }

        ArrayHandle hArray = xArray.createImmutableArray(
                ensureMultiMethodTemplateArrayComposition(),
                listHandles.toArray(NO_TEMPLATES));

        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: properties.get()
     */
    public int getPropertyProperties(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz = (ClassStructure) hComponent.getComponent();
        if (!clz.getFileStructure().isLinked())
            {
            return frame.raiseException(xException.illegalState(frame, "FileTemplate is not resolved"));
            }

        List<ComponentTemplateHandle> listProps = new ArrayList<>();
        for (Component child : clz.children())
            {
            if (child instanceof PropertyStructure)
                {
                listProps.add(xRTPropertyTemplate.makePropertyHandle((PropertyStructure) child));
                }
            }

        ComponentTemplateHandle[] ahProp = listProps.toArray(NO_TEMPLATES);
        ArrayHandle hArray = xArray.createImmutableArray(
                xRTPropertyTemplate.ensureArrayComposition(), ahProp);
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: singleton.get()
     */
    public int getPropertySingleton(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz        = (ClassStructure) hComponent.getComponent();
        boolean        fSingleton = clz.isSingleton();
        return frame.assignValue(iReturn, xBoolean.makeHandle(fSingleton));
        }

    /**
     * Implements property: hasDefault.get()
     */
    public int getPropertyHasDefault(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz      = (ClassStructure) hComponent.getComponent();
        boolean        fDefault = clz.getCanonicalType().getDefaultValue() != null;
        return frame.assignValue(iReturn, xBoolean.makeHandle(fDefault));
        }

    /**
     * Implements property: sourceInfo.get()
     */
    public int getPropertySourceInfo(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        return frame.raiseException("Not implemented");
        }

    /**
     * Implements property: type.get()
     */
    public int getPropertyType(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz = (ClassStructure) hComponent.getComponent();
        if (!clz.getFileStructure().isLinked())
            {
            return frame.raiseException(xException.illegalState(frame, "FileTemplate is not resolved"));
            }
        TypeConstant type = clz.getIdentityConstant().getType();
        return frame.assignValue(iReturn, xRTTypeTemplate.makeHandle(type));
        }

    /**
     * Implements property: typeParams.get()
     */
    public int getPropertyTypeParams(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz = (ClassStructure) hComponent.getComponent();
        List<Map.Entry<StringConstant, TypeConstant>> listParams = clz.getTypeParamsAsList();

        if (listParams.isEmpty())
            {
            return frame.assignValue(iReturn, ensureEmptyTypeParameterArray());
            }

        int            cParams = listParams.size();
        StringHandle[] ahName  = new StringHandle[cParams];
        ObjectHandle[] ahType  = new ObjectHandle[cParams];

        int i = 0;
        for (Map.Entry<StringConstant, TypeConstant> entry : listParams)
            {
            ahName[i]   = xString.makeHandle(entry.getKey().getValue());
            ahType[i++] = xRTTypeTemplate.makeHandle(entry.getValue());
            }

        ObjectHandle[] ahVar = new ObjectHandle[CREATE_TYPE_PARAMETERS_METHOD.getMaxVars()];
        ahVar[0] = xArray.makeStringArrayHandle(ahName);
        ahVar[1] = xArray.createImmutableArray(
                    xRTTypeTemplate.ensureArrayClassComposition(), ahType);

        return frame.call1(CREATE_TYPE_PARAMETERS_METHOD, null, ahVar, iReturn);
        }

    /**
     * Implements property: virtualChild.get()
     */
    public int getPropertyVirtualChild(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz      = (ClassStructure) hComponent.getComponent();
        boolean        fVirtual = clz.isVirtualChild();
        return frame.assignValue(iReturn, xBoolean.makeHandle(fVirtual));
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code Class<> ensureClass(Type... actualTypes)}.
     */
    protected int invokeEnsureClass(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        return frame.raiseException("Not implemented");
        }

    /**
     * Implementation for: {@code conditional (Annotation, Composition) deannotate()}.
     */
    protected int invokeDeannotate(Frame frame, ComponentTemplateHandle hComponent, int[] aiReturn)
        {
        // ClassTemplate annotations are actually contributions
        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }


    // ----- ObjectHandle support ------------------------------------------------------------------

    /**
     * Obtain a {@link ComponentTemplateHandle} for the specified {@link ClassStructure}.
     *
     * @param structClz  the {@link ClassStructure} to obtain a {@link ComponentTemplateHandle} for
     *
     * @return the resulting {@link ComponentTemplateHandle}
     */
    public static ComponentTemplateHandle makeHandle(ClassStructure structClz)
        {
        // note: no need to initialize the struct because there are no natural fields
        return new ComponentTemplateHandle(CLASS_TEMPLATE_COMP, structClz);
        }

    /**
     * @return the ClassComposition for an Array of Contributions
     */
    public static TypeComposition ensureContribArrayComposition()
        {
        TypeComposition clz = CONTRIBUTION_ARRAY_COMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeContribArray = pool.ensureArrayType(CONTRIBUTION_COMP.getType());
            CONTRIBUTION_ARRAY_COMP = clz = INSTANCE.f_container.resolveClass(typeContribArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the ClassComposition for an Array of ClassTemplates
     */
    public static TypeComposition ensureClassTemplateArrayComposition()
        {
        TypeComposition clz = CLASS_TEMPLATE_ARRAY_COMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeTemplateArray = pool.ensureArrayType(CLASS_TEMPLATE_COMP.getType());
            CLASS_TEMPLATE_ARRAY_COMP = clz = INSTANCE.f_container.resolveClass(typeTemplateArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the ClassComposition for an Array of MultiMethodTemplates
     */
    public static TypeComposition ensureMultiMethodTemplateArrayComposition()
        {
        TypeComposition clz = MULTI_METHOD_TEMPLATE_ARRAY_COMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeTemplate = pool.ensureEcstasyTypeConstant("reflect.MultiMethodTemplate");
            TypeConstant typeTemplateArray = pool.ensureArrayType(typeTemplate);
            MULTI_METHOD_TEMPLATE_ARRAY_COMP = clz = INSTANCE.f_container.resolveClass(typeTemplateArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the ClassComposition for an Array of MethodTemplates
     */
    public static TypeComposition ensureMethodTemplateArrayComposition()
        {
        TypeComposition clz = METHOD_TEMPLATE_ARRAY_COMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeTemplate = pool.ensureEcstasyTypeConstant("reflect.MethodTemplate");
            TypeConstant typeTemplateArray = pool.ensureArrayType(typeTemplate);
            METHOD_TEMPLATE_ARRAY_COMP = clz = INSTANCE.f_container.resolveClass(typeTemplateArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the ClassComposition for an Array of AnnotationTemplates
     */
    public static TypeComposition ensureAnnotationTemplateArrayComposition()
        {
        TypeComposition clz = ANNOTATION_TEMPLATE_ARRAY_COMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeTemplate = pool.ensureEcstasyTypeConstant("reflect.AnnotationTemplate");
            TypeConstant typeTemplateArray = pool.ensureArrayType(typeTemplate);
            ANNOTATION_TEMPLATE_ARRAY_COMP = clz = INSTANCE.f_container.resolveClass(typeTemplateArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the handle for an empty Array of TypeParameters
     */
    public static ObjectHandle ensureEmptyTypeParameterArray()
        {
        if (TYPE_PARAMETER_ARRAY_EMPTY == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeTypeParamArray = pool.ensureArrayType(
                                                pool.ensureEcstasyTypeConstant("reflect.TypeParameter"));
            TypeComposition clz = INSTANCE.f_container.resolveClass(typeTypeParamArray);

            TYPE_PARAMETER_ARRAY_EMPTY = xArray.createImmutableArray(clz, Utils.OBJECTS_NONE);
            }
        return TYPE_PARAMETER_ARRAY_EMPTY;
        }


    // ----- constants -----------------------------------------------------------------------------

    public static ComponentTemplateHandle[] NO_TEMPLATES = new ComponentTemplateHandle[0];

    private static TypeComposition CLASS_TEMPLATE_COMP;
    private static TypeComposition CLASS_TEMPLATE_ARRAY_COMP;
    private static TypeComposition MULTI_METHOD_TEMPLATE_ARRAY_COMP;
    private static TypeComposition METHOD_TEMPLATE_ARRAY_COMP;
    private static TypeComposition ANNOTATION_TEMPLATE_ARRAY_COMP;
    private static TypeComposition CONTRIBUTION_COMP;
    private static TypeComposition CONTRIBUTION_ARRAY_COMP;

    private static ArrayHandle     TYPE_PARAMETER_ARRAY_EMPTY;

    public static xEnum           ACTION;
    public static MethodStructure CREATE_CONTRIB_METHOD;
    public static MethodStructure CREATE_TYPE_PARAMETERS_METHOD;
    }