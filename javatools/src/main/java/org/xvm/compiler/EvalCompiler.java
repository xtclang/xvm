package org.xvm.compiler;

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.Assignment;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ComponentResolver;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;
import org.xvm.asm.Register;

import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ast.Context;
import org.xvm.compiler.ast.MethodDeclarationStatement;
import org.xvm.compiler.ast.StageMgr;
import org.xvm.compiler.ast.StatementBlock;
import org.xvm.compiler.ast.StatementBlock.RootContext;
import org.xvm.compiler.ast.StatementBlock.TargetInfo;
import org.xvm.compiler.ast.TypeExpression;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.Utils;

import org.xvm.util.ListMap;
import org.xvm.util.Severity;

/**
 * The compiler of the "eval" script used by the debugger.
 */
public class EvalCompiler
    {
    public EvalCompiler(Frame frame, String sMethod)
        {
        f_frame  = frame;
        f_source = new Source(sMethod);
        }

    /**
     * The arguments' indexes.
     */
    private int[] m_aiArg;

    /**
     * Create the lambda structure and compute the arguments.
     *
     * If the compilation succeeds, the indexes for arguments could be retrieved via
     * {@link #getArgs()}; otherwise there are errors in the {@link #getErrors() error list}.
     *
     * @return the newly created lambda or null if the compilation failed
     */
    public MethodStructure createLambda(TypeConstant typeReturn)
        {
        ConstantPool pool = f_frame.poolContext();
        ErrorList    errs = m_errs = new ErrorList(1);

        MethodStructure      method = f_frame.f_function;
        ClassStructure       clz    = method.getContainingClass();
        MultiMethodStructure mms    = clz.ensureMultiMethodStructure("->");
        MethodStructure      lambda = mms.createLambda(TypeConstant.NO_TYPES, Utils.NO_NAMES);

        TypeConstant[] atypeRets    = new TypeConstant[] {pool.typeObject()};
        Parameter[]    aparamRets   = new Parameter[] {
                new Parameter(pool, typeReturn, null, null, true, 0, false)};

        lambda.configureLambda(Parameter.NO_PARAMS, 0, aparamRets);
        lambda.setStatic(method.isFunction());
        lambda.createCode();

        try
            {
            StatementBlock astBody   = new Parser(f_source, errs).parseStatementBlock();
            EvalStatement  astMethod = new EvalStatement(f_frame, lambda, astBody);

            StageMgr mgr = new StageMgr(astMethod, Compiler.Stage.Registered, errs);
            if (!mgr.processComplete())
                {
                return null;
                }

            mgr = new StageMgr(astMethod, Compiler.Stage.Loaded, errs);
            if (!mgr.processComplete())
                {
                return null;
                }

            mgr = new StageMgr(astMethod, Compiler.Stage.Resolved, errs);
            if (!mgr.processComplete())
                {
                return null;
                }

            mgr = new StageMgr(astMethod, Compiler.Stage.Validated, errs);
            if (!mgr.processComplete())
                {
                return null;
                }

            mgr = new StageMgr(astMethod, Compiler.Stage.Emitted, errs);
            if (!mgr.processComplete() || errs.hasSeriousErrors())
                {
                return null;
                }

            Map<String, Argument> mapCaptures = astMethod.getCaptures();

            int            cParams      = mapCaptures.size();
            TypeConstant[] atypeParams  = new TypeConstant[cParams];
            Parameter[]    aparamParams = new Parameter[cParams];

            int ix = 0;
            for (Map.Entry<String, Argument> entry : mapCaptures.entrySet())
                {
                String       sName = entry.getKey();
                TypeConstant type  = entry.getValue().getType();

                atypeParams[ix]  = type;
                aparamParams[ix] = new Parameter(pool, type, sName, null, false, ix, false);
                ix++;
                }

            lambda.configureLambda(aparamParams, 0, aparamRets);
            lambda.getIdentityConstant().setSignature(
                    pool.ensureSignatureConstant("->", atypeParams, atypeRets));
            lambda.forceAssembly(pool);

            m_aiArg = astMethod.getArguments();
            return lambda;
            }
        catch (Exception e)
            {
            if (!errs.hasSeriousErrors())
                {
                errs.log(Severity.FATAL, Parser.FATAL_ERROR, null,
                    f_source, f_source.getPosition(), f_source.getPosition());
                }
            return null;
            }
        }

    /**
     * @return a list of errors
     */
    public List<ErrorListener.ErrorInfo> getErrors()
        {
        return m_errs.getErrors();
        }

    /**
     * @return the lambda arguments indexes
     */
    public int[] getArgs()
        {
        return m_aiArg;
        }

    /**
     * A synthetic RootContext used by the {@link EvalStatement}.
     */
    protected static class EvalContext
            extends RootContext
        {
        public EvalContext(Frame frame, StatementBlock stmt, MethodStructure lambda)
            {
            super(stmt, lambda);

            f_frame = frame;
            }

        @Override
        public TypeConstant getThisType()
            {
            ConstantPool pool = pool();

            // for the purpose of the eval compilation we always assume PRIVATE access
            MethodStructure function = f_frame.f_function;
            if (function.isFunction())
                {
                return pool.ensureAccessTypeConstant(super.getThisType(), Access.PRIVATE);
                }

            // The type of "this" is possibly narrower than the level of the contributing class
            // teh current frame belongs to
            ClassStructure clz  = function.getContainingClass();
            TypeConstant   type = clz.getFormalType().resolveGenerics(pool, f_frame.getThis().getType());
            return pool.ensureAccessTypeConstant(type, Access.PRIVATE);
            }

        @Override
        protected Argument resolveRegularName(Context ctxFrom, String sName, Token name, ErrorListener errs)
            {
            Argument arg = super.resolveRegularName(ctxFrom, sName, name, errs);
            if (arg == null)
                {
                try
                    {
                    if (!f_frame.f_function.isFunction() &&
                            f_frame.getArgument(Op.A_PRIVATE) instanceof GenericHandle hThis)
                        {
                        ClassStructure       clz = getThisClass();
                        MultiMethodStructure mms = (MultiMethodStructure) clz.findChildDeep(sName);
                        if (mms != null)
                            {
                            arg = new TargetInfo(sName, mms.getIdentityConstant(),
                                    true, getThisType(), 0);
                            ensureNameMap().put(sName, arg);
                            return arg;
                            }

                        // try to find it above the current level
                        clz = (ClassStructure) hThis.getType().getSingleUnderlyingClass(true).getComponent();
                        mms = (MultiMethodStructure) clz.findChildDeep(sName);
                        if (mms != null)
                            {
                            arg = new TargetInfo(sName, mms.getIdentityConstant(), true,
                                        hThis.getType(), 0);
                            ensureNameMap().put(sName, arg);
                            return arg;
                            }
                        }
                    }
                catch (ExceptionHandle.WrapperException ignore) {}
                }
            return arg;
            }

        /**
         * The list of indexes for captured arguments.
         */
        public final List<Integer> f_listRegisters = new ArrayList<>();

        @Override
        public ConstantPool pool()
            {
            return f_frame.poolContext();
            }

        @Override
        public ClassStructure getEnclosingClass()
            {
            return getMethod().getContainingClass();
            }

        @Override
        public boolean isAnonInnerClass()
            {
            return getEnclosingClass().isAnonInnerClass();
            }

        /**
         * The current frame.
         */
        private final Frame f_frame;

        /**
         * The map of captured arguments.
         */
        public final Map<String, Argument> f_mapCapture  = new ListMap<>();

        @Override
        protected Argument getLocalVar(String sName, Branch branch)
            {
            Argument arg = getNameMap().get(sName);
            if (arg != null)
                {
                return arg;
                }

            Frame.VarInfo[] aInfo = f_frame.f_aInfo;
            int             cVars = f_frame.getCurrentVarCount();

            for (int iVar = 0; iVar < cVars; iVar++)
                {
                Frame.VarInfo info = aInfo[iVar];
                if (info != null && info.getName().equals(sName))
                    {
                    Register reg = new Register(info.getType(), null, f_listRegisters.size());

                    f_mapCapture.put(sName, reg);
                    f_listRegisters.add(iVar);

                    ensureNameMap().put(sName, reg);
                    ensureDefiniteAssignments().put(sName, Assignment.AssignedOnce);
                    return reg;
                    }
                }

            if (!f_frame.f_function.isFunction())
                {
                if (sName.equals("this"))
                    {
                    Register reg = new Register(getThisType(), null, Op.A_THIS);
                    ensureNameMap().put(sName, reg);
                    return reg;
                    }

                if (f_frame.getThis().ensureAccess(Access.PRIVATE) instanceof GenericHandle hThis)
                    {
                    TypeConstant type = getThisType();
                    PropertyInfo prop = type.ensureTypeInfo().findProperty(sName);
                    if (prop != null)
                        {
                        ensureNameMap().put(sName, arg = prop.getIdentity());
                        return arg;
                        }

                    type = hThis.getType();
                    prop = type.ensureTypeInfo().findProperty(sName);
                    if (prop != null)
                        {
                        ensureNameMap().put(sName, arg = prop.getIdentity());
                        return arg;
                        }
                    }
                }
            return null;
            }
        }


    /**
     * The current frame.
     */
    private final Frame f_frame;

    /**
     * The source.
     */
    private final Source f_source;

    /**
     * The errors.
     */
    private ErrorList m_errs;

    /**
     * A synthetic MethodDeclarationStatement that contains the eval body.
     */
    protected class EvalStatement
            extends MethodDeclarationStatement
            implements ComponentResolver
        {
        public EvalStatement(Frame frame, MethodStructure lambda, StatementBlock body)
            {
            super(lambda, body);

            f_frame = frame;
            f_ctx   = new EvalContext(frame, body, lambda);
            }

        /**
         * @return the captured registers
         */
        public Map<String, Argument> getCaptures()
            {
            return f_ctx.f_mapCapture;
            }

        /**
         * @return the list of indexes for lambda arguments
         */
        public int[] getArguments()
            {
            List<Integer> listIndex = f_ctx.f_listRegisters;
            int           cArgs     = listIndex.size();
            int[]         aIndex    = new int[cArgs];
            for (int i = 0; i < cArgs; i++)
                {
                aIndex[i] = listIndex.get(i);
                }
            return aIndex;
            }

        @Override
        public Source getSource()
            {
            return f_source;
            }

        @Override
        public boolean isComponentNode()
            {
            return true;
            }


        @Override
        public ComponentResolver getComponentResolver()
            {
            return this;
            }

        @Override
        public boolean isAutoNarrowingAllowed(TypeExpression type)
            {
            return false;
            }

        @Override
        protected void registerStructures(StageMgr mgr, ErrorListener errs)
            {
            introduceParentage();

            mgr.processChildrenExcept((child) -> child == body);
            }

        @Override
        public void resolveNames(StageMgr mgr, ErrorListener errs)
            {
            mgr.deferChildren();
            }

        @Override
        protected void compileBody(StageMgr mgr, MethodStructure method, ErrorListener errs)
            {
            body.compileMethod(f_ctx, method.ensureCode(), errs);
            }

        @Override
        public ConstantPool pool()
            {
            return f_frame.poolContext();
            }

        @Override
        protected boolean canResolveNames()
            {
            return true;
            }

        @Override
        protected Field[] getChildFields()
            {
            return CHILD_FIELDS;
            }


        // ----- ComponentResolver methods ---------------------------------------------------------

        @Override
        public ResolutionResult resolveName(String sName, Constants.Access access,
                                            ResolutionCollector collector)
            {
            Component component = pool().getImplicitlyImportedComponent(sName);
            return component == null
                    ? ResolutionResult.ERROR
                    : collector.resolvedComponent(component);
            }

        @Override
        public String toString()
            {
            return "EvalStatement";
            }

        private final Frame       f_frame;
        private final EvalContext f_ctx;

        private static final Field[] CHILD_FIELDS = fieldsForNames(MethodDeclarationStatement.class, "body");
        }
    }