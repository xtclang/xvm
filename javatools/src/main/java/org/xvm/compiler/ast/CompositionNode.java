package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xvm.asm.Annotation;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Version;
import org.xvm.asm.VersionTree;

import org.xvm.compiler.Source;
import org.xvm.compiler.Token;


/**
 * A composition step. This defines one of the "building blocks" for a type composition.
 */
public abstract class CompositionNode
        extends AstNode
    {
    // ----- constructors --------------------------------------------------------------------------

    public CompositionNode(Expression condition, Token keyword, TypeExpression type)
        {
        this.condition = condition;
        this.keyword   = keyword;
        this.type      = type;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the condition that applies to the composition, or null
     */
    public Expression getCondition()
        {
        return condition;
        }

    /**
     * @return the keyword token used to define the composition
     */
    public Token getKeyword()
        {
        return keyword;
        }

    /**
     * @return the TypeExpression of the Composition
     */
    public TypeExpression getType()
        {
        return type;
        }


    // ----- AstNode methods -----------------------------------------------------------------------

    @Override
    public Source getSource()
        {
        Source source = super.getSource();
        if (source == null)
            {
            source = condition == null ? type.getSource() : condition.getSource();
            }
        return source;
        }

    @Override
    public long getStartPosition()
        {
        return condition == null ? keyword.getStartPosition() : condition.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return condition == null ? type.getEndPosition() : condition.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
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

        sb.append(keyword.getId().TEXT);

        if (type != null)
            {
            sb.append(' ')
              .append(type);
            }

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


    // ----- inner class: Extends ------------------------------------------------------------------

    public static class Extends
            extends CompositionNode
        {
        public Extends(Expression condition, Token keyword, TypeExpression type, List<Expression> args)
            {
            super(condition, keyword, type);
            this.args = args;
            }

        @Override
        public long getEndPosition()
            {
            return condition == null && args != null && !args.isEmpty()
                    ? args.get(args.size()-1).getEndPosition()
                    : super.getEndPosition();
            }

        @Override
        protected Field[] getChildFields()
            {
            return CHILD_FIELDS;
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

        protected List<Expression> args;

        private static final Field[] CHILD_FIELDS = fieldsForNames(Extends.class,
                "condition", "type", "args");
        }


    // ----- inner class: Incorporates -------------------------------------------------------------

    public static class Incorporates
            extends CompositionNode
        {
        public Incorporates(Expression condition, Token keyword, TypeExpression type, List<Expression> args, List<Parameter> constraints)
            {
            super(condition, keyword, type);
            this.args        = args;
            this.constraints = constraints;
            }

        /**
         * Converts an annotation to an "incorporate" clause.
         *
         * @param annotation  the Annotation to create an Incorporates for
         */
        public Incorporates(AnnotationExpression annotation)
            {
            this(null, new Token(annotation.getStartPosition(), annotation.getStartPosition(),
                    Token.Id.INCORPORATES), annotation.type, annotation.args, null);
            this.annotation = annotation;
            }

        /**
         * @return true iff the incorporates clause is conditional based on the generic parameters
         *         of the specified type
         */
        public boolean isConditional()
            {
            return constraints != null;
            }

        /**
         * @return true iff this clause represents a class annotation
         */
        public boolean isAnnotation()
            {
            return annotation != null;
            }

        public Annotation ensureAnnotation(ConstantPool pool)
            {
            assert isAnnotation();
            return annotation.ensureAnnotation(pool);
            }

        /**
         * @return true iff the incorporates clause is conditional based on the generic parameters
         *         of the specified type
         */
        public List<Parameter> getConstraints()
            {
            return constraints;
            }

        @Override
        public long getEndPosition()
            {
            return condition == null && args != null && !args.isEmpty()
                    ? args.get(args.size()-1).getEndPosition()
                    : super.getEndPosition();
            }

        @Override
        protected Field[] getChildFields()
            {
            return CHILD_FIELDS;
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();

            if (condition != null)
                {
                sb.append("if (")
                  .append(condition)
                  .append(") { ");
                }

            sb.append(keyword.getId().TEXT);

            if (isConditional())
                {
                // special handling for "incorporates conditional <T1 extends T2, ..>"
                String sType = type.toString();
                sb.append(" conditional ")
                  .append(sType, 0, sType.indexOf('<'));

                sb.append('<');
                boolean first = true;
                for (Parameter param : constraints)
                    {
                    if (first)
                        {
                        first = false;
                        }
                    else
                        {
                        sb.append(", ");
                        }
                    sb.append(param.toTypeParamString());
                    }
                sb.append('>');
                }
            else
                {
                sb.append(' ')
                  .append(type);
                }

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

        protected List<Expression> args;
        protected List<Parameter>  constraints;

        protected AnnotationExpression annotation;

        private static final Field[] CHILD_FIELDS = fieldsForNames(Incorporates.class,
                "condition", "type", "args", "constraints", "annotation");
        }


    // ----- inner class: Implements ---------------------------------------------------------------

    public static class Implements
            extends CompositionNode
        {
        public Implements(Expression condition, Token keyword, TypeExpression type)
            {
            super(condition, keyword, type);
            }
        }


    // ----- inner class: Delegates ----------------------------------------------------------------

    public static class Delegates
            extends CompositionNode
        {
        public Delegates(Expression condition, Token keyword, TypeExpression type, Token delegate, long lEndPos)
            {
            super(condition, keyword, type);
            this.delegate = delegate;
            this.lEndPos  = lEndPos;
            }

        /**
         * @return the name of the property that holds the reference to delegate to
         */
        public String getPropertyName()
            {
            return (String) delegate.getValue();
            }

        @Override
        public long getEndPosition()
            {
            return lEndPos;
            }

        @Override
        public String toString()
            {
            return toStartString() + '(' + delegate + ')' + toEndString();
            }

        protected Token delegate;
        protected long  lEndPos;
        }


    // ----- inner class: Into ---------------------------------------------------------------------

    public static class Into
            extends CompositionNode
        {
        public Into(Expression condition, Token keyword, TypeExpression type)
            {
            super(condition, keyword, type);
            }
        }


    // ----- inner class: Import -------------------------------------------------------------------

    public static class Import
            extends CompositionNode
        {
        public Import(Expression condition, Token keyword, NamedTypeExpression type, List<VersionOverride> vers, long lEndPos)
            {
            super(condition, keyword, type);
            this.vers    = vers;
            this.lEndPos = lEndPos;
            }

        /**
         * @return a version tree specifying versions allowed/preferred (true) and avoided (false)
         */
        public VersionTree<Boolean> getAllowVersionTree()
            {
            VersionTree<Boolean> vtree = new VersionTree<>();
            if (vers != null)
                {
                for (VersionOverride override : vers)
                    {
                    Version ver = override.getVersion();
                    Boolean BPrevAllow = vtree.get(ver);
                    boolean fAllow = override.isAllowed();
                    if (BPrevAllow != null && fAllow != BPrevAllow.booleanValue())
                        {
                        throw new IllegalStateException(
                                "version " + ver + " is both allowed and disallowed");
                        }
                    else
                        {
                        vtree.put(ver, fAllow);
                        }
                    }
                }
            return vtree;
            }

        /**
         * @return the list of preferred versions
         */
        public List<Version> getPreferVersionList()
            {
            if (vers == null)
                {
                return Collections.EMPTY_LIST;
                }

            List<Version> list = new ArrayList<>();
            for (VersionOverride override : vers)
                {
                if (override.isPreferred())
                    {
                    list.add(override.getVersion());
                    }
                }
            return list;
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

        /**
         * The version overrides; could be null.
         */
        protected List<VersionOverride> vers;
        protected long                  lEndPos;

        private static final Field[] CHILD_FIELDS = fieldsForNames(Import.class, "condition", "type", "vers");
        }


    // ----- inner class: Default ------------------------------------------------------------------

    public static class Default
            extends CompositionNode
        {
        public Default(Expression condition, Token keyword, Expression expr, long lEndPos)
            {
            super(condition, keyword, null);
            this.expr    = expr;
            this.lEndPos = lEndPos;
            }

        /**
         * @return the expression representing the default value for the type composition
         */
        public Expression getValueExpression()
            {
            return expr;
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

        @Override
        public String toString()
            {
            return toStartString() + '(' + expr + ')' + toEndString();
            }

        protected Expression expr;
        protected long       lEndPos;

        private static final Field[] CHILD_FIELDS = fieldsForNames(Default.class, "condition", "expr");
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression     condition;
    protected Token          keyword;
    protected TypeExpression type;

    private static final Field[] CHILD_FIELDS = fieldsForNames(CompositionNode.class, "condition", "type");
    }
