package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.Assignment;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Constants;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.Severity;


/**
 * An annotation is a type annotation and an optional argument list.
 */
public class Annotation
        extends AstNode
    {
    // ----- constructors --------------------------------------------------------------------------

    public Annotation(NamedTypeExpression type, List<Expression> args, long lStartPos, long lEndPos)
        {
        this.type      = type;
        this.args      = args;
        this.lStartPos = lStartPos;
        this.lEndPos   = lEndPos;
        }

    public Annotation(org.xvm.asm.Annotation anno, AstNode node)
        {
        this.m_anno    = anno;
        this.m_node    = node;
        this.lStartPos = node.getStartPosition();
        this.lEndPos   = node.getEndPosition();
        }


    // ----- accessors -----------------------------------------------------------------------------

    public NamedTypeExpression getType()
        {
        NamedTypeExpression expr = type;
        if (expr == null)
            {
            assert m_node != null && m_anno != null;
            List<Token> names = Collections.singletonList(new Token(lStartPos, lEndPos,
                    Id.IDENTIFIER, ((IdentityConstant) m_anno.getAnnotationClass()).getName()));
            type = expr = new NamedTypeExpression(null, names, null, null, null, lEndPos);
            }
        return expr;
        }

    public List<Expression> getArguments()
        {
        return args;
        }

    @Override
    protected boolean canResolveNames()
        {
        return m_anno != null || super.canResolveNames() || type.canResolveNames();
        }

    @Override
    public long getStartPosition()
        {
        return lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }

    /**
     * Build an XVM Annotation that corresponds to the information that this AST Annotation has
     * available.
     *
     * @param pool  the ConstantPool to use to create the XVM structures
     *
     * @return an XVM Annotation
     */
    public org.xvm.asm.Annotation ensureAnnotation(ConstantPool pool)
        {
        if (m_anno != null)
            {
            return m_anno;
            }

        Constant         constClass  = getType().getIdentityConstant();
        List<Expression> args        = getArguments();
        Constant[]       aconstArgs  = Constant.NO_CONSTS;
        int              cArgs       = args == null ? 0 : args.size();
        Constant         constNoIdea = null;
        if (cArgs > 0)
            {
            aconstArgs = new Constant[cArgs];
            for (int iArg = 0; iArg < cArgs; ++iArg)
                {
                Expression exprArg = args.get(iArg);
                if (exprArg.alreadyReached(Stage.Validated))
                    {
                    aconstArgs[iArg] = exprArg.toConstant();
                    }
                else if (exprArg instanceof LiteralExpression
                        && ((LiteralExpression) exprArg).getLiteral().getId() == Id.LIT_STRING)
                    {
                    // only String literals have a predictable runtime type (no @Auto conversions)
                    aconstArgs[iArg] = pool.ensureStringConstant(((LiteralExpression) exprArg)
                            .getLiteral().getValue().toString());
                    }
                else
                    {
                    if (constNoIdea == null)
                        {
                        constNoIdea = pool.ensureStringConstant("???");
                        }
                    aconstArgs[iArg] = constNoIdea;
                    }
                }
            }

        org.xvm.asm.Annotation anno = new org.xvm.asm.Annotation(pool, constClass, aconstArgs);
        if (constNoIdea != null)
            {
            anno.markUnresolved();
            }
        m_anno = anno;
        return anno;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public void validateContent(StageMgr mgr, ErrorListener errs)
        {
        org.xvm.asm.Annotation anno = m_anno;
        assert anno != null;

        Constant constAnno = anno.getAnnotationClass();
        if (constAnno.containsUnresolved())
            {
            // the type name resolution failed and an error must have been reported
            return;
            }

        boolean       fValid   = true;
        ClassConstant idAnno   = (ClassConstant) constAnno;
        // TODO: this should "steal" any matching type parameters from the underlying type
        TypeInfo      infoAnno = idAnno.ensureTypeInfo(errs);
        if (infoAnno.getFormat() != Format.MIXIN)
            {
            log(errs, Severity.ERROR, Constants.VE_ANNOTATION_NOT_MIXIN, idAnno.getName());
            fValid = false;
            }

        if (!mgr.processChildren())
            {
            mgr.requestRevisit();
            return;
            }

        // find a matching constructor on the annotation class
        // before we let the children go, we need to fill in the annotation construction parameters
        List<Expression>  args       = getArguments();
        int               cArgs      = args == null ? 0 : args.size();
        String[]          asArgNames = null;
        boolean           fNameErr   = false;
        TypeConstant[]    atypeArgs  = TypeConstant.NO_TYPES;
        ValidatingContext ctx        = null;
        if (cArgs > 0)
            {
            // build a list of argument types and names (used later to try to find an appropriate
            // annotation constructor)
            atypeArgs = new TypeConstant[cArgs];
            ctx       = new ValidatingContext();

            for (int iArg = 0; iArg < cArgs; ++iArg)
                {
                Expression exprArg = args.get(iArg);
                if (exprArg instanceof LabeledExpression)
                    {
                    if (asArgNames == null)
                        {
                        asArgNames = new String[cArgs];
                        }
                    asArgNames[iArg] = ((LabeledExpression) exprArg).getName();
                    }
                else if (asArgNames != null && !fNameErr)
                    {
                    // there was already at least one arg with a name, so all trailing args MUST
                    // have a name
                    exprArg.log(errs, Severity.ERROR, Compiler.ARG_NAME_REQUIRED, iArg);
                    fNameErr = true;
                    fValid   = false;
                    }

                atypeArgs[iArg] = exprArg.getImplicitType(ctx);
                }
            }

        if (fValid)
            {
            MethodConstant idConstruct = infoAnno.findConstructor(atypeArgs, asArgNames);
            if (idConstruct == null)
                {
                // TODO uncomment this after we bother to create constructors :D
                // log(errs, Severity.ERROR, Compiler.ANNOTATION_DECL_UNRESOLVABLE, idAnno.getName());
                }
            else if (cArgs > 0)
                {
                // validate the argument expressions and fix up all of the constants used as
                // arguments to construct the annotation
                Constant[] aconstArgs = anno.getParams();
                assert cArgs == aconstArgs.length;

                TypeConstant[] atypeParams = idConstruct.getRawParams();
                for (int iArg = 0; iArg < cArgs; ++iArg)
                    {
                    Expression exprOld = args.get(iArg);
                    Expression exprNew = exprOld.validate(ctx, atypeParams[iArg], errs);
                    if (exprNew != null && exprNew != exprOld)
                        {
                        args.set(iArg, exprNew);
                        }

                    if (exprNew == null || !exprNew.isRuntimeConstant())
                        {
                        exprOld.log(errs, Severity.ERROR, Compiler.CONSTANT_REQUIRED);
                        }
                    else
                        {
                        // update the Annotation directly
                        // Note: this is quite unusual, in that normally things like an annotation are
                        //       treated as a constant once instantiated, but in this case, it was
                        //       impossible to validate the arguments of the annotation when it was
                        //       constructed, because we were too early in the compile cycle to resolve
                        //       any constant expressions that refer to anything _by name_
                        aconstArgs[iArg] = exprNew.toConstant();
                        }
                    }

                anno.resolveParams(aconstArgs);
                }
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        if (m_anno != null)
            {
            return m_anno.toString();
            }

        StringBuilder sb = new StringBuilder();

        sb.append('@')
          .append(type);

        if (args != null)
            {
            sb.append('(');

            boolean first = true;
            for (Expression expr : args)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(expr);
                }

            sb.append(')');
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- inner class: ValidatingContext --------------------------------------------------------

    /**
     * A context implementation that allows us to validate the constant parameters of an annotation
     * without having to be inside a StatementBlock of a method that is being compiled.
     */
    protected class ValidatingContext
            extends Context
        {
        public ValidatingContext()
            {
            super(null, false);
            }

        @Override
        public MethodStructure getMethod()
            {
            throw new IllegalStateException();
            }

        @Override
        public Source getSource()
            {
            return Annotation.this.getSource();
            }

        @Override
        public ConstantPool pool()
            {
            return Annotation.this.pool();
            }

        @Override
        public Context enterFork(boolean fWhenTrue)
            {
            throw new IllegalStateException();
            }

        @Override
        public Context enter()
            {
            throw new IllegalStateException();
            }

        @Override
        public void registerVar(Token tokName, Register reg, ErrorListener errs)
            {
            throw new IllegalStateException();
            }

        @Override
        public boolean isVarDeclaredInThisScope(String sName)
            {
            return false;
            }

        @Override
        public boolean isVarWritable(String sName)
            {
            return false;
            }

        @Override
        protected Argument resolveRegularName(String sName, Token name, ErrorListener errs)
            {
            return new NameResolver(Annotation.this, sName).forceResolve(errs);
            }

        @Override
        protected Argument resolveReservedName(String sName, Token name, ErrorListener errs)
            {
            return sName.equals("this:module")
                    ? Annotation.this.getComponent().getIdentityConstant().getModuleConstant()
                    : null;
            }

        @Override
        public Context exit()
            {
            throw new IllegalStateException();
            }

        @Override
        public Map<String, Assignment> prepareJump(Context ctxDest)
            {
            throw new IllegalStateException();
            }

        @Override
        protected Assignment promote(String sName, Assignment asnInner, Assignment asnOuter)
            {
            throw new IllegalStateException();
            }

        @Override
        protected Assignment promoteNonCompleting(String sName, Assignment asnInner,
                Assignment asnOuter)
            {
            throw new IllegalStateException();
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    protected NamedTypeExpression type;
    protected List<Expression>    args;
    protected long                lStartPos;
    protected long                lEndPos;

    // these two fields allow us to pretend to be an Annotation by generating a type on the fly, if
    // necessary
    private transient AstNode                m_node;
    private transient org.xvm.asm.Annotation m_anno;

    private static final Field[] CHILD_FIELDS = fieldsForNames(Annotation.class, "type", "args");
    }
