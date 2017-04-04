package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.List;

/**
 * A composition step.
 *
 * @author cp 2017.03.28
 */
public abstract class Composition
    {
    public Composition(TypeExpression type)
        {
        this.type = type;
        }

    @Override
    public abstract String toString();

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

        public final List<Expression> args;
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

        public final List<Expression> args;
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

        public final Expression delegate;
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

        public final List<VersionOverride> vers;
        }

    public final TypeExpression type;
    }
