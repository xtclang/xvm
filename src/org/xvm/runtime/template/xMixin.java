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
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template._native.reflect.xRTFunction.FullyBoundHandle;


/**
 * Native functionality for annotated classes (except Ref).
 */
public class xMixin
        extends xObject
    {
    public xMixin(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        return ensureClass(typeActual, typeActual);
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
        private ClassComposition       clzNext;
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

            clzNext = (ClassComposition) hStruct.getComposition();
            assert clzNext.getType().getAccess() == Access.STRUCT;

            typeNext = clzNext.getType().getUnderlyingType();
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
                        MethodStructure methodAI = clzNext.ensureAutoInitializer();
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
                        xMixin                mixin      = (xMixin) clzNext.getTemplate();
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
                            clzNext = mixin.f_templates.resolveClass(typeNext);
                            }
                        break;
                        }

                    case 2: // call the base constructor
                        {
                        Frame frameCtor = frameCaller.createFrame1(constructor, hStruct, ahVar, Op.A_IGNORE);

                        prepareFinalizer(frameCtor, constructor, ahVar);

                        iResult = frameCaller.callInitialized(frameCtor);
                        break;
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
        return getBaseTemplate(hTarget).invoke1(frame, chain, hTarget, ahVar, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getBaseTemplate(hTarget).invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        return getBaseTemplate(hTarget).invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    private ClassTemplate getBaseTemplate(ObjectHandle hTarget)
        {
        AnnotatedTypeConstant typeAnno = (AnnotatedTypeConstant) hTarget.getType();
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
