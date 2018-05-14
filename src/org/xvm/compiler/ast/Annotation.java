package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Register;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.ast.Statement.Context;

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


    // ----- accessors -----------------------------------------------------------------------------

    public NamedTypeExpression getType()
        {
        return type;
        }

    public List<Expression> getArguments()
        {
        return args;
        }

    @Override
    protected boolean canResolveNames()
        {
        return super.canResolveNames() || type.canResolveNames();
        }

    @Override
    public long getStartPosition()
        {
        return type.getStartPosition();
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

    public org.xvm.asm.Annotation buildAnnotation(ConstantPool pool)
        {
        Constant constClass = getType().getIdentityConstant();
        List<Expression> args       = getArguments();
        Constant[]       aconstArgs = null;
        if (args != null && !args.isEmpty())
            {
            int cArgs = args.size();
            aconstArgs = new Constant[cArgs];
            Constant constNoIdea = pool.ensureStringConstant("???");
            for (int iArg = 0; iArg < cArgs; ++iArg)
                {
                aconstArgs[iArg] = constNoIdea;
                }
            }
        m_anno = new org.xvm.asm.Annotation(constClass, aconstArgs);
        return m_anno;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public AstNode resolveNames(List<AstNode> listRevisit, ErrorListener errs)
        {
        return super.resolveNames(listRevisit, errs);

        }

    @Override
    public AstNode validateExpressions(List<AstNode> listRevisit, ErrorListener errs)
        {
        // before we let the children go, we need to fill in the annotation construction parameters
        Constant[] aconstArgs = m_anno == null ? null : m_anno.getParams();
        if (aconstArgs != null && aconstArgs.length > 0)
            {
            ValidatingContext ctx   = new ValidatingContext();
            List<Expression>  args  = getArguments();
            int               cArgs = args.size();
            assert cArgs == aconstArgs.length;

            for (int iArg = 0; iArg < cArgs; ++iArg)
                {
                Expression exprOld = args.get(iArg);
                Expression exprNew = (Expression) exprOld.validateExpressions(listRevisit, errs);
                if (exprNew != null)
                    {
                    exprNew = exprNew.validate(ctx, null, Expression.TuplePref.Rejected, errs);
                    }

                if (exprNew != null && exprNew != exprOld)
                    {
                    args.set(iArg, exprNew);
                    }

                if (exprNew == null || !exprNew.isConstant())
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
            }

        return super.validateExpressions(listRevisit, errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
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

    protected class ValidatingContext
            extends Context
        {
        public ValidatingContext()
            {
            super(null);
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
        public Context fork()
            {
            throw new IllegalStateException();
            }

        @Override
        public void join(Context... contexts)
            {
            throw new IllegalStateException();
            }

        @Override
        public Context enterScope()
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
        public Argument resolveRegularName(Token name, ErrorListener errs)
            {
            return new NameResolver(Annotation.this, name.getValueText()).forceResolve(errs);
            }

        @Override
        public Argument resolveReservedName(Token name, ErrorListener errs)
            {
            return name.getValueText().equals("this:module")
                    ? Annotation.this.getComponent().getIdentityConstant().getModuleConstant()
                    : null;
            }

        @Override
        public Context exitScope()
            {
            throw new IllegalStateException();
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    protected NamedTypeExpression type;
    protected List<Expression>    args;
    protected long                lStartPos;
    protected long                lEndPos;

    private transient org.xvm.asm.Annotation m_anno;

    private static final Field[] CHILD_FIELDS = fieldsForNames(Annotation.class, "type", "args");
    }
