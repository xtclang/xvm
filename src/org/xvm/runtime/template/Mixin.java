package org.xvm.runtime.template;


import java.util.LinkedList;
import java.util.List;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.PropertyComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template._native.reflect.xRTFunction.FullyBoundHandle;


/**
 * Native functionality for annotated classes (except Ref annotations).
 */
public class Mixin
        extends xObject
    {
    public Mixin(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initDeclared()
        {
        }

    @Override
    public ClassComposition ensureClass(TypeConstant typeActual)
        {
        return getBaseTemplate(typeActual).ensureClass(typeActual);
        }

    @Override
    public int callConstructor(Frame frame, MethodStructure constructor,
                               ObjectHandle hStruct, ObjectHandle[] ahVar, int iReturn)
        {
        return new Construct(constructor, hStruct, ahVar, iReturn).proceed(frame);
        }

    /**
     * Helper class for the construction of annotated class.
     */
    protected class Construct
            implements Frame.Continuation
        {
        // passed in arguments
        private final MethodStructure  constructor;
        private final ObjectHandle     hStruct;
        private final ObjectHandle[]   ahVar;
        private final int              iReturn;

        // internal fields
        private TypeConstant           typeNext;
        private int                    ixStep;
        private List<FullyBoundHandle> listFinalizers;

        public Construct(MethodStructure constructor,
                         ObjectHandle    hStruct,
                         ObjectHandle[]  ahVar,
                         int             iReturn)
            {
            this.constructor = constructor;
            this.hStruct     = hStruct;
            this.ahVar       = ahVar;
            this.iReturn     = iReturn;

            ClassComposition clz = (ClassComposition) hStruct.getComposition();
            assert clz.getType().getAccess() == Access.STRUCT;

            typeNext = clz.getType().getUnderlyingType();
            assert typeNext.isAnnotated();
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            while (true)
                {
                int iResult;
                switch (ixStep++)
                    {
                    case 0: // call auto-generated initializer
                        {
                        MethodStructure methodAI = hStruct.getComposition().ensureAutoInitializer();
                        if (methodAI != null)
                            {
                            iResult  = frameCaller.call1(methodAI, hStruct, Utils.OBJECTS_NONE, Op.A_IGNORE);
                            break;
                            }
                        ixStep++;
                        // fall through
                        }

                    case 1: // call the the annotation constructor(s)
                        {
                        AnnotatedTypeConstant typeAnno   = (AnnotatedTypeConstant) typeNext;
                        Mixin                 mixin      = (Mixin) f_templates.getTemplate(typeAnno.getAnnotationClass());
                        Annotation            anno       = typeAnno.getAnnotation();
                        Constant[]            aconstArgs = anno.getParams();

                        MethodStructure constructAnno = mixin.f_struct.findMethod("construct", aconstArgs.length);
                        ObjectHandle[]  ahArgs        = new ObjectHandle[constructAnno.getMaxVars()];
                        for (int i = 0, c = aconstArgs.length; i < c; i++)
                            {
                            ahArgs[i] = frameCaller.getConstHandle(aconstArgs[i]);
                            }

                        Frame frameCtor = frameCaller.createFrame1(constructAnno, hStruct, ahArgs, Op.A_IGNORE);

                        prepareFinalizer(frameCtor, constructAnno, ahArgs);

                        iResult = frameCaller.callInitialized(frameCtor);

                        typeNext = typeNext.getUnderlyingType();
                        if (typeNext instanceof AnnotatedTypeConstant)
                            {
                            // repeat step 1
                            ixStep  = 1;
                            }
                        break;
                        }

                    case 2: // call the base constructor
                        {
                        if (constructor != null)
                            {
                            Frame frameCtor = frameCaller.createFrame1(constructor, hStruct, ahVar, Op.A_IGNORE);

                            prepareFinalizer(frameCtor, constructor, ahVar);

                            iResult = frameCaller.callInitialized(frameCtor);
                            break;
                            }
                        ixStep++;
                        // fall through
                        }

                    case 3: // check unassigned
                        {
                        List<String> listUnassigned;
                        if ((listUnassigned = hStruct.validateFields()) != null)
                            {
                            return frameCaller.raiseException(
                                xException.unassignedFields(frameCaller, listUnassigned));
                            }
                        ixStep++;
                        // fall through
                        }

                    case 4: // post-construction validation
                        iResult = postValidate(frameCaller, hStruct);
                        break;

                    case 5:
                        {
                        ObjectHandle hPublic = hStruct.ensureAccess(Access.PUBLIC);
                        if (listFinalizers == null)
                            {
                            return frameCaller.assignValue(iReturn, hPublic);
                            }

                        int              ix         = 0;
                        FullyBoundHandle hfnFinally = null;
                        for (FullyBoundHandle hfn : listFinalizers)
                            {
                            if (ix++ == 0)
                                {
                                hfnFinally = hfn;
                                }
                            else
                                {
                                hfnFinally = hfnFinally.chain(hfn);
                                }
                            }

                        return hfnFinally.callChain(frameCaller, hPublic, frame_ ->
                                    frame_.assignValue(iReturn, hPublic));
                        }

                    default:
                        throw new IllegalStateException();
                    }

                switch (iResult)
                    {
                    case Op.R_NEXT:
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalArgumentException();
                    }
                }
            }

        private void prepareFinalizer(Frame frame, MethodStructure ctor, ObjectHandle[] ahVar)
            {
            FullyBoundHandle hfn = Utils.makeFinalizer(ctor, ahVar);
            if (hfn == null)
                {
                // in case super constructors have their own finalizers
                // we need a non-null anchor
                frame.m_hfnFinally = FullyBoundHandle.NO_OP;
                }
            else
                {
                if (listFinalizers == null)
                    {
                    listFinalizers = new LinkedList<>();
                    }
                listFinalizers.add(frame.m_hfnFinally = hfn);
                }
            }
        }

    @Override
    public int invoke1(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return getBaseTemplate(hTarget.getComposition()).invoke1(frame, chain, hTarget, ahVar, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getBaseTemplate(hTarget.getComposition()).invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        return getBaseTemplate(hTarget.getComposition()).invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        return getBaseTemplate(hTarget.getComposition()).invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeSet(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hValue)
        {
        return getBaseTemplate(hTarget.getComposition()).invokeNativeSet(frame, hTarget, sPropName, hValue);
        }

    private ClassTemplate getBaseTemplate(TypeComposition clz)
        {
        return getBaseTemplate(clz instanceof PropertyComposition
            ? ((PropertyComposition) clz).getPropertyClass().getType()
            : clz.getType());
        }

    private ClassTemplate getBaseTemplate(TypeConstant type)
        {
        AnnotatedTypeConstant typeAnno = (AnnotatedTypeConstant) type;
        TypeConstant          typeBase;
        while (true)
            {
            typeBase = typeAnno.getUnderlyingType();
            if (typeBase instanceof AnnotatedTypeConstant)
                {
                typeAnno = (AnnotatedTypeConstant) typeBase;
                }
            else
                {
                break;
                }
            }

        return f_templates.getTemplate(typeBase);
        }
    }
