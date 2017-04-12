package org.xvm.compiler.ast;


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

    public Composition(TypeExpression type)
        {
        this.type = type;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public abstract String toString();

    @Override
    public String getDumpDesc()
        {
        return toString();
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("type", type);
        return map;
        }


    // ----- inner classes -------------------------------------------------------------------------

    public static class Extends
            extends Composition
        {
        public Extends(TypeExpression type, List<Expression> args)
            {
            super(type);
            this.args = args;
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();

            sb.append("extends ")
              .append(type);

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
        public Incorporates(TypeExpression type, List<Expression> args)
            {
            super(type);
            this.args = args;
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();

            sb.append("incorporates ")
                    .append(type);

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
        public Implements(TypeExpression type)
            {
            super(type);
            }

        @Override
        public String toString()
            {
            return "implements " + type;
            }
        }

    public static class Delegates
            extends Composition
        {
        public Delegates(TypeExpression type, Expression delegate)
            {
            super(type);
            this.delegate = delegate;
            }

        @Override
        public String toString()
            {
            return "delegates " + type + '(' + delegate + ')';
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
        public Into(TypeExpression type)
            {
            super(type);
            }

        @Override
        public String toString()
            {
            return "into " + type;
            }
        }

    public static class Import
            extends Composition
        {
        public Import(NamedTypeExpression type, List<VersionOverride> vers)
            {
            super(type);
            this.vers = vers;
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();

            sb.append("import ")
                    .append(type);

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

            return sb.toString();
            }

        @Override
        public Map<String, Object> getDumpChildren()
            {
            Map<String, Object> map = super.getDumpChildren();
            map.put("vers", vers);
            return map;
            }

        protected List<VersionOverride> vers;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression type;
    }
