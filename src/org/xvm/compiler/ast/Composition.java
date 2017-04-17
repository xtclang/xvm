package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import org.xvm.util.ListMap;

import java.util.List;
import java.util.Map;


/**
 * A composition step. This defines one of the "building blocks" for a type composition.
 *
 * @author cp 2017.03.28
 */
public abstract class Composition
        extends AstNode
    {
    // ----- constructors --------------------------------------------------------------------------

    public Composition(Expression condition, Token keyword, TypeExpression type)
        {
        this.condition = condition;
        this.keyword   = keyword;
        this.type      = type;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public Token getKeyword()
        {
        return keyword;
        }

    public TypeExpression getType()
        {
        return type;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return toStartString() + toEndString();
        }

    public String toStartString()
        {
        StringBuilder sb = new StringBuilder();

        if (condition != null)
            {
            sb.append("if (")
                    .append(condition)
                    .append(") { ");
            }

        sb.append(keyword.getId().TEXT)
                .append(' ')
                .append(type);

        return sb.toString();
        }

    protected String toEndString()
        {
        return condition == null ? "" : " }";
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("condition", condition);
        map.put("type", type);
        return map;
        }


    // ----- inner classes -------------------------------------------------------------------------

    public static class Extends
            extends Composition
        {
        public Extends(Expression condition, Token keyword, TypeExpression type, List<Expression> args)
            {
            super(condition, keyword, type);
            this.args = args;
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();

            sb.append(toStartString());

            if (args != null)
                {
                sb.append('(');
                boolean first = true;
                for (Expression arg : args)
                    {
                    if (first)
                        {
                        first = false;
                        }
                    else
                        {
                        sb.append(", ");
                        }
                    sb.append(arg);
                    }
                  sb.append(')');
                }

            sb.append(toEndString());
            return sb.toString();
            }

        @Override
        public Map<String, Object> getDumpChildren()
            {
            Map<String, Object> map = super.getDumpChildren();
            map.put("args", args);
            return map;
            }

        protected List<Expression> args;
        }

    public static class Incorporates
            extends Composition
        {
        public Incorporates(Expression condition, Token keyword, TypeExpression type, List<Expression> args)
            {
            super(condition, keyword, type);
            this.args = args;
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();

            sb.append(toStartString());

            if (args != null)
                {
                sb.append('(');
                boolean first = true;
                for (Expression arg : args)
                    {
                    if (first)
                        {
                        first = false;
                        }
                    else
                        {
                        sb.append(", ");
                        }
                    sb.append(arg);
                    }
                sb.append(')');
                }

            sb.append(toEndString());
            return sb.toString();
            }

        @Override
        public Map<String, Object> getDumpChildren()
            {
            Map<String, Object> map = super.getDumpChildren();
            map.put("args", args);
            return map;
            }

        protected List<Expression> args;
        }

    public static class Implements
            extends Composition
        {
        public Implements(Expression condition, Token keyword, TypeExpression type)
            {
            super(condition, keyword, type);
            }
        }

    public static class Delegates
            extends Composition
        {
        public Delegates(Expression condition, Token keyword, TypeExpression type, Expression delegate)
            {
            super(condition, keyword, type);
            this.delegate = delegate;
            }

        @Override
        public String toString()
            {
            return toStartString() + '(' + delegate + ')' + toEndString();
            }

        @Override
        public Map<String, Object> getDumpChildren()
            {
            Map<String, Object> map = super.getDumpChildren();
            map.put("delegate", delegate);
            return map;
            }

        protected Expression delegate;
        }

    public static class Into
            extends Composition
        {
        public Into(Expression condition, Token keyword, TypeExpression type)
            {
            super(condition, keyword, type);
            }
        }

    public static class Import
            extends Composition
        {
        public Import(Expression condition, Token keyword, NamedTypeExpression type, List<VersionOverride> vers)
            {
            super(condition, keyword, type);
            this.vers = vers;
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();

            sb.append(toStartString());

            if (vers != null)
                {
                boolean first = true;
                for (VersionOverride ver : vers)
                    {
                    if (first)
                        {
                        sb.append(' ');
                        first = false;
                        }
                    else
                        {
                        sb.append("\n        ");
                        }
                    sb.append(ver);
                    }
                }

            sb.append(toEndString());
            return sb.toString();
            }

        @Override
        public Map<String, Object> getDumpChildren()
            {
            Map<String, Object> map = super.getDumpChildren();
            map.put("type", vers);
            return map;
            }

        protected List<VersionOverride> vers;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression     condition;
    protected Token          keyword;
    protected TypeExpression type;
    }
