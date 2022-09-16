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
import org.xvm.asm.Parameter;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.RegisterConstant;
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
            ConstantPool pool = f_container.getConstantPool();

            ACTION_TEMPLATE = (xEnum) f_container.getTemplate("reflect.ClassTemplate.Composition.Action");

            CLASS_TEMPLATE_TYPE       = pool.ensureEcstasyTypeConstant("reflect.ClassTemplate");
            CLASS_TEMPLATE_ARRAY_TYPE = pool.ensureArrayType(CLASS_TEMPLATE_TYPE);
            CONTRIBUTION_ARRAY_TYPE   = pool.ensureArrayType(pool.ensureEcstasyTypeConstant(
                                            "reflect.ClassTemplate.Composition.Contribution"));
            MULTI_METHOD_ARRAY_TYPE   = pool.ensureArrayType(pool.ensureEcstasyTypeConstant(
                                            "reflect.MultiMethodTemplate"));
            METHOD_ARRAY_TYPE         = pool.ensureArrayType(pool.ensureEcstasyTypeConstant(
                                            "reflect.MethodTemplate"));
            ANNOTATION_ARRAY_TYPE     = pool.ensureArrayType(pool.ensureEcstasyTypeConstant(
                                            "reflect.AnnotationTemplate"));

            EMPTY_PARAMETER_ARRAY     = pool.ensureArrayConstant(
                                            pool.ensureArrayType(pool.ensureEcstasyTypeConstant(
                                                "reflect.TypeParameter")),
                                            Constant.NO_CONSTS);

            CREATE_CONTRIB_METHOD         = f_struct.findMethod("createContribution", 6);
            CREATE_TYPE_PARAMETERS_METHOD = f_struct.findMethod("createTypeParameters", 2);

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
        ClassStructure   clz = (ClassStructure) hComponent.getComponent();
        IdentityConstant id  = clz.getIdentityConstant();
        if (id instanceof ClassConstant idClz)
            {
            String sAlias = idClz.getImplicitImportName();
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
        Container      container = frame.f_context.f_container;
        ClassStructure clz       = (ClassStructure) hComponent.getComponent();
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
                    listTemplates.add(xRTClassTemplate.makeHandle(container, (ClassStructure) child));
                    break;
                }
            }

        ArrayHandle hArray = xArray.createImmutableArray(
                ensureClassTemplateArrayComposition(container),
                listTemplates.toArray(NO_TEMPLATES));
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: contribs.get()
     */
    public int getPropertyContribs(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        Container      container = frame.f_context.f_container;
        ClassStructure clz       = (ClassStructure) hComponent.getComponent();
        if (!clz.getFileStructure().isLinked())
            {
            return frame.raiseException(xException.illegalState(frame, "FileTemplate is not resolved"));
            }

        List<Contribution>  listContrib = clz.getContributionsAsList();
        Utils.ValueSupplier supplier    = (frameCaller, index) ->
            {
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
                    sAction = "AnnotatedBy";

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
                        ClassConstant  idAnno  = (ClassConstant) anno.getAnnotationClass();
                        ClassStructure clzAnno = (ClassStructure) idAnno.getComponent();
                        if (clzAnno == null)
                            {
                            return frameCaller.raiseException(
                                    "unknown annotation " + idAnno.getValueString());
                            }

                        TypeConstant[] atype = new TypeConstant[cParams];

                        ahParam = new ObjectHandle[cParams];
                        for (int i = 0; i < cParams; i++)
                            {
                            Constant constParam = aParam[i];

                            // default argument values will be filled later
                            if (!(constParam instanceof RegisterConstant))
                                {
                                atype[i]   = container.getType(constParam);
                                ahParam[i] = frameCaller.getConstHandle(constParam);
                                }
                            }

                        MethodStructure ctor = clzAnno.findMethod("construct", cParams, atype);
                        if (ctor == null)
                            {
                            return frameCaller.raiseException("missing annotation constructor " +
                                    idAnno.getValueString() + " with " + cParams + " parameters");
                            }

                        StringHandle[] ahNames = new StringHandle[cParams];
                        for (int i = 0; i < cParams; i++)
                            {
                            Parameter param = ctor.getParam(i);

                            ahNames[i] = xString.makeHandle(param.getName());
                            if (ahParam[i] == null)
                                {
                                if (!param.hasDefaultValue())
                                    {
                                    return frameCaller.raiseException("missing default value for parameter \"" +
                                            param.getName() + "\" at " + ctor.getIdentityConstant());
                                    }

                                ahParam[i] = frameCaller.getConstHandle(param.getDefaultValue());
                                }
                            }
                        haNames = xArray.makeStringArrayHandle(ahNames);
                        }

                    if (Op.anyDeferred(ahParam))
                        {
                        ObjectHandle haN = haNames;
                        ObjectHandle haT = haTypes;

                        Frame.Continuation stepNext = frameCaller2 ->
                            callCreateContrib(frameCaller2, hComponent, sAction, typeContrib,
                                xArray.makeObjectArrayHandle(ahParam, Mutability.Constant),
                                hDelegatee, haN, haT);

                        return new Utils.GetArguments(ahParam, stepNext).doNext(frame);
                        }

                    haParams = xArray.makeObjectArrayHandle(ahParam, Mutability.Constant);
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
                    sAction = "Incorporates";

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
                            ahTypes[i] = type.ensureTypeHandle(container);
                            i++;
                            }
                        haNames = xArray.makeStringArrayHandle(ahNames);
                        haTypes = xArray.createImmutableArray(
                                    xRTType.ensureTypeArrayComposition(container), ahTypes);
                        }
                    break;
                    }
                default:
                    throw new IllegalStateException();
                }

            return callCreateContrib(frameCaller, hComponent, sAction, typeContrib, haParams,
                    hDelegatee, haNames, haTypes);
            };

        return xArray.createAndFill(frame, ensureContribArrayComposition(container),
                listContrib.size(), supplier, iReturn);
        }

    static private int callCreateContrib(Frame frame, ComponentTemplateHandle hComponent,
                                         String sAction, TypeConstant typeContrib,
                                         ObjectHandle haParams, ObjectHandle hDelegatee,
                                         ObjectHandle haNames, ObjectHandle haTypes)
        {
        ObjectHandle[] ahVar = new ObjectHandle[CREATE_CONTRIB_METHOD.getMaxVars()];
        ahVar[0] = Utils.ensureInitializedEnum(frame, ACTION_TEMPLATE.getEnumByName(sAction));
        ahVar[1] = typeContrib.ensureTypeHandle(frame.f_context.f_container);
        ahVar[2] = haParams;
        ahVar[3] = hDelegatee;
        ahVar[4] = haNames;
        ahVar[5] = haTypes;

        return frame.call1(CREATE_CONTRIB_METHOD, hComponent, ahVar, Op.A_STACK);
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
        Container      container = frame.f_context.f_container;
        ClassStructure clz       = (ClassStructure) hComponent.getComponent();

        List<ComponentTemplateHandle> listHandles = new ArrayList<>();
        for (Component child : clz.children())
            {
            if (child instanceof MultiMethodStructure)
                {
                listHandles.add(makeComponentHandle(container, child));
                }
            }

        ArrayHandle hArray = xArray.createImmutableArray(
                ensureMultiMethodTemplateArrayComposition(container),
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
            if (child instanceof PropertyStructure prop)
                {
                listProps.add(xRTPropertyTemplate.makePropertyHandle(prop));
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

        return frame.assignValue(iReturn,
            xRTTypeTemplate.makeHandle(frame.f_context.f_container, clz.getIdentityConstant().getType()));
        }

    /**
     * Implements property: typeParams.get()
     */
    public int getPropertyTypeParams(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        Container      container = frame.f_context.f_container;
        ClassStructure clz       = (ClassStructure) hComponent.getComponent();
        List<Map.Entry<StringConstant, TypeConstant>> listParams = clz.getTypeParamsAsList();

        if (listParams.isEmpty())
            {
            return frame.assignValue(iReturn, ensureEmptyTypeParameterArray(container));
            }

        int            cParams = listParams.size();
        StringHandle[] ahName  = new StringHandle[cParams];
        ObjectHandle[] ahType  = new ObjectHandle[cParams];

        int i = 0;
        for (Map.Entry<StringConstant, TypeConstant> entry : listParams)
            {
            ahName[i]   = xString.makeHandle(entry.getKey().getValue());
            ahType[i++] = xRTTypeTemplate.makeHandle(container, entry.getValue());
            }

        ObjectHandle[] ahVar = new ObjectHandle[CREATE_TYPE_PARAMETERS_METHOD.getMaxVars()];
        ahVar[0] = xArray.makeStringArrayHandle(ahName);
        ahVar[1] = xArray.createImmutableArray(
                    xRTTypeTemplate.ensureArrayClassComposition(container), ahType);

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
     * @param container  the container for the handle
     * @param struct     the {@link ClassStructure} to obtain a {@link ComponentTemplateHandle} for
     *
     * @return the resulting {@link ComponentTemplateHandle}
     */
    public static ComponentTemplateHandle makeHandle(Container container, ClassStructure struct)
        {
        // note: no need to initialize the struct because there are no natural fields
        TypeComposition clz = INSTANCE.ensureClass(container,
                                INSTANCE.getCanonicalType(), CLASS_TEMPLATE_TYPE);
        return new ComponentTemplateHandle(clz, struct);
        }

    /**
     * @return the ClassComposition for an Array of Contributions
     */
    public static TypeComposition ensureContribArrayComposition(Container container)
        {
        return container.ensureClassComposition(CONTRIBUTION_ARRAY_TYPE, xArray.INSTANCE);
        }

    /**
     * @return the ClassComposition for an Array of ClassTemplates
     */
    public static TypeComposition ensureClassTemplateArrayComposition(Container container)
        {
        return container.ensureClassComposition(CLASS_TEMPLATE_ARRAY_TYPE, xArray.INSTANCE);
        }

    /**
     * @return the ClassComposition for an Array of MultiMethodTemplates
     */
    public static TypeComposition ensureMultiMethodTemplateArrayComposition(Container container)
        {
        return container.ensureClassComposition(MULTI_METHOD_ARRAY_TYPE, xArray.INSTANCE);
        }

    /**
     * @return the ClassComposition for an Array of MethodTemplates
     */
    public static TypeComposition ensureMethodTemplateArrayComposition(Container container)
        {
        return container.ensureClassComposition(METHOD_ARRAY_TYPE, xArray.INSTANCE);
        }

    /**
     * @return the ClassComposition for an Array of AnnotationTemplates
     */
    public static TypeComposition ensureAnnotationTemplateArrayComposition(Container container)
        {
        return container.ensureClassComposition(ANNOTATION_ARRAY_TYPE, xArray.INSTANCE);
        }

    /**
     * @return the handle for an empty Array of TypeParameters
     */
    public ArrayHandle ensureEmptyTypeParameterArray(Container container)
        {
        ArrayHandle haEmpty = (ArrayHandle) container.f_heap.getConstHandle(EMPTY_PARAMETER_ARRAY);
        if (haEmpty == null)
            {
            haEmpty = xArray.createImmutableArray(
                        container.ensureClassComposition(EMPTY_PARAMETER_ARRAY.getType(), xArray.INSTANCE),
                        Utils.OBJECTS_NONE);
            container.f_heap.saveConstHandle(EMPTY_PARAMETER_ARRAY, haEmpty);
            }
        return haEmpty;
        }


    // ----- constants -----------------------------------------------------------------------------

    public static ComponentTemplateHandle[] NO_TEMPLATES = new ComponentTemplateHandle[0];

    private static TypeConstant CLASS_TEMPLATE_TYPE;
    private static TypeConstant CLASS_TEMPLATE_ARRAY_TYPE;
    private static TypeConstant MULTI_METHOD_ARRAY_TYPE;
    private static TypeConstant METHOD_ARRAY_TYPE;
    private static TypeConstant ANNOTATION_ARRAY_TYPE;
    private static TypeConstant CONTRIBUTION_ARRAY_TYPE;

    private static ArrayConstant EMPTY_PARAMETER_ARRAY;

    public static xEnum           ACTION_TEMPLATE;
    public static MethodStructure CREATE_CONTRIB_METHOD;
    public static MethodStructure CREATE_TYPE_PARAMETERS_METHOD;
    }