package org.xvm.runtime.template;


import java.util.ArrayList;
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
    public void initNative()
        {
        }

    @Override
    public ClassComposition ensureClass(TypeConstant typeActual)
        {
        return getBaseTemplate(typeActual).ensureClass(typeActual);
        }

    @Override
    public int proceedConstruction(Frame frame, MethodStructure constructor, boolean fInitStruct,
                                   ObjectHandle hStruct, ObjectHandle[] ahVar, int iReturn)
        {
        return new Construct(constructor, fInitStruct, hStruct, ahVar, iReturn).proceed(frame);
        }

    /**
     * Helper class for the construction of annotated class.
     */
    protected class Construct
            implements Frame.Continuation
        {
        // passed in arguments
        private final MethodStructure constructor;
        private final ObjectHandle    hStruct;
        private final ObjectHandle[]  ahVar;
        private final int             iReturn;

        // internal fields
        private TypeConstant          typeNext;
        private int                   ixStep;
        private List<Frame>           listFinalizable;

        public Construct(MethodStructure constructor,
                         boolean         fInitStruct,
                         ObjectHandle    hStruct,
                         ObjectHandle[]  ahVar,
                         int             iReturn)
            {
            this.constructor = constructor;
            this.hStruct     = hStruct;
            this.ahVar       = ahVar;
            this.iReturn     = iReturn;

            TypeComposition comp = hStruct.getComposition();
            assert comp.isStruct();

            typeNext = comp.getBaseType();
            assert typeNext.isAnnotated();

            assert fInitStruct || constructor == null;
            ixStep = fInitStruct ? 0 : 3;
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
                            iResult = frameCaller.call1(methodAI, hStruct, Utils.OBJECTS_NONE, Op.A_IGNORE);
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

                        MethodStructure constructAnno = mixin.getStructure().findMethod("construct", aconstArgs.length);
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
                        if (constructor == null)
                            {
                            ixStep++;
                            // fall through
                            }
                        else
                            {
                            Frame frameCtor = frameCaller.createFrame1(constructor, hStruct, ahVar, Op.A_IGNORE);

                            prepareFinalizer(frameCtor, constructor, ahVar);

                            iResult = frameCaller.callInitialized(frameCtor);
                            break;
                            }

                    case 3: // validation
                        iResult = callValidator(frameCaller, hStruct);
                        break;

                    case 4: // check unassigned
                        {
                        List<String> listUnassigned;
                        if ((listUnassigned = hStruct.validateFields()) != null)
                            {
                            return frameCaller.raiseException(xException.unassignedFields(
                                    frameCaller, hStruct.getType().getValueString(), listUnassigned));
                            }
                        ixStep++;
                        // fall through
                        }

                    case 5: // native post-construction validation
                        iResult = postValidate(frameCaller, hStruct);
                        break;

                    case 6:
                        {
                        ObjectHandle hPublic      = hStruct.ensureAccess(Access.PUBLIC);
                        List<Frame>  listFinalize = listFinalizable;
                        if (listFinalize == null)
                            {
                            return frameCaller.assignValue(iReturn, hPublic);
                            }

                        // create a chain (stack) of finalizers
                        int              cFn        = listFinalize.size();
                        FullyBoundHandle hfnFinally = listFinalize.get(cFn - 1).m_hfnFinally;
                        for (int i = cFn - 2; i >= 0; i--)
                            {
                            hfnFinally = listFinalize.get(i).m_hfnFinally.chain(hfnFinally);
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
            if (listFinalizable == null)
                {
                listFinalizable = new ArrayList<>();
                }

            FullyBoundHandle hfn = Utils.makeFinalizer(ctor, ahVar);
            if (hfn == null)
                {
                // in case super constructors have their own finalizers, we need a non-null anchor
                // that may be replaced by Frame.chainFinalizers()
                hfn = FullyBoundHandle.NO_OP;
                }

            frame.m_hfnFinally = hfn;
            listFinalizable.add(frame);
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
            : ((ClassComposition) clz).getInceptionType());
        }

    private ClassTemplate getBaseTemplate(TypeConstant type)
        {
        // first, unwrap access and immutability
        while (!(type instanceof AnnotatedTypeConstant))
            {
            type = type.getUnderlyingType();
            }

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
