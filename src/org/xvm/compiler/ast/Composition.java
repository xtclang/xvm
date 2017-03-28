package org.xvm.compiler.ast;


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
        public Extends(TypeExpression type, Expression constructor)
            {
            super(type);
            this.constructor = constructor;
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();

            sb.append("extends ")
              .append(type);

            if (constructor != null)
                {
                sb.append('(')
                  .append(constructor)      // TODO not sure how this will work
                  .append(')');
                }

            return sb.toString();
            }

        public final Expression constructor;
        }

    public static class Incorporates
            extends Composition
        {
        public Incorporates(TypeExpression type, Expression constructor)
            {
            super(type);
            this.constructor = constructor;
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();

            sb.append("incorporates ")
                    .append(type);

            if (constructor != null)
                {
                sb.append('(')
                        .append(constructor)      // TODO not sure how this will work
                        .append(')');
                }

            return sb.toString();
            }

        public final Expression constructor;
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

    public final TypeExpression type;
    }
