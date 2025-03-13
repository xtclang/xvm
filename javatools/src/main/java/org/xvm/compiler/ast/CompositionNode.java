package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xvm.asm.Annotation;
import org.xvm.asm.Component.Contribution;
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

    /**
     * @return the Contribution associated with (created by) this composition
     */
    public Contribution getContribution()
        {
        return m_contribution;
        }

    /**
     * Set the Contribution associated with (created by) this composition.
     */
    public void setContribution(Contribution contribution)
        {
        this.m_contribution = contribution;
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
        public Extends(Expression condition, Token keyword, TypeExpression type)
            {
            super(condition, keyword, type);
            }

        public Extends(Expression condition, Token keyword, TypeExpression type,
                       List<Expression> args, long lEndPos)
            {
            super(condition, keyword, type);
            this.args    = args;
            this.lEndPos = lEndPos;
            }

        @Override
        public long getEndPosition()
            {
            return lEndPos == 0 ? super.getEndPosition() : lEndPos;
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
        protected long             lEndPos;

        private static final Field[] CHILD_FIELDS = fieldsForNames(Extends.class,
                "condition", "type", "args");
        }


    // ----- inner class: Annotates ----------------------------------------------------------------

    public static class Annotates
            extends CompositionNode
        {
        public Annotates(AnnotationExpression annotation)
            {
            super(null, new Token(annotation.getStartPosition(), annotation.getStartPosition(),
                    Token.Id.ANNOTATION), annotation.type);
            this.annotation = annotation;
            }

        public Annotation ensureAnnotation(ConstantPool pool)
            {
            return annotation.ensureAnnotation(pool);
            }

        @Override
        public long getEndPosition()
            {
            List<Expression> listArgs = annotation.args;
            return listArgs != null && !listArgs.isEmpty()
                    ? listArgs.getLast().getEndPosition()
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

            sb.append(keyword.getId().TEXT)
              .append(' ')
              .append(annotation.type);

            if (annotation.args != null)
                {
                sb.append('(');
                boolean first = true;
                for (Expression arg : annotation.args)
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

        protected AnnotationExpression annotation;

        private static final Field[] CHILD_FIELDS = fieldsForNames(Annotates.class,
                "annotation");
        }


    // ----- inner class: Incorporates -------------------------------------------------------------

    public static class Incorporates
            extends CompositionNode
        {
        public Incorporates(Expression condition, Token keyword, TypeExpression type,
                            List<Expression> args, List<Parameter> constraints)
            {
            super(condition, keyword, type);
            this.args        = args;
            this.constraints = constraints;
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
         * @return list of constraints for conditional incorporates; null otherwise
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

        private static final Field[] CHILD_FIELDS = fieldsForNames(Incorporates.class,
                "condition", "type", "args", "constraints");
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
        public Delegates(Expression condition, Token keyword, TypeExpression type,
                         Expression delegatee, long lEndPos)
            {
            super(condition, keyword, type);
            this.delegatee = delegatee;
            this.lEndPos   = lEndPos;
            }

        /**
         * @return the expression denoting the target of the delegation, such as the name of the
         *         property that holds the reference to delegate to
         */
        public Expression getDelegatee()
            {
            return delegatee;
            }

        public void setPropertyName(String sName)
            {
            assert name == null;
            name = sName;
            }

        public String getPropertyName()
            {
            assert name != null;
            return name;
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
            return toStartString() + '(' + delegatee + ')' + toEndString();
            }

        protected Expression delegatee;
        protected long       lEndPos;

        protected transient String name;

        private static final Field[] CHILD_FIELDS =
                fieldsForNames(Delegates.class, "condition", "type", "delegatee");
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

    /**
     * Represents a module import on a package declaration.
     */
    public static class Import
            extends CompositionNode
        {
        public Import(Expression condition, Token keyword, Token modifier, NamedTypeExpression type,
                      List<VersionOverride> vers, List<Parameter> injects,
                      NamedTypeExpression injector, long lEndPos)
            {
            super(condition, keyword, type);
            this.modifier = modifier;
            this.vers     = vers;
            this.injects  = injects;
            this.injector = injector;
            this.lEndPos  = lEndPos;
            }

        /**
         * @return the modifier keyword, or null
         */
        public Token getModifier()
            {
            return modifier;
            }

        /**
         * @return the implied modifier keyword as a token ID
         */
        public Token.Id getImplicitModifier()
            {
            return modifier == null
                    ? Token.Id.REQUIRED
                    : modifier.getId();
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
                return Collections.emptyList();
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

        /**
         * @return the name of the singleton injector class
         */
        NamedTypeExpression getInjector()
            {
            return injector;
            }

        /**
         * @return the types and names of injections that will get handled by the specified injector
         */
        List<Parameter> getSpecificInjections()
            {
            return injects;
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

            sb.append(keyword.getId().TEXT)
              .append(' ');

            if (modifier != null)
                {
                sb.append(modifier.getId().TEXT)
                  .append(' ');
                }

            sb.append(type);

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

            if (injects != null)
                {
                sb.append("\n        ")
                  .append(Token.Id.INJECT.TEXT)
                  .append(' ')
                  .append('(');

                boolean first = true;
                for (Parameter injection : injects)
                    {
                    if (first)
                        {
                        first = false;
                        }
                    else
                        {
                        sb.append(", ");
                        }
                    sb.append(injection);
                    }

                sb.append(')');
                }


            if (injector != null)
                {
                sb.append("\n        ")
                    .append(Token.Id.USING.TEXT)
                    .append(' ')
                    .append(injector);
                }

            return sb.toString();
            }

        /**
         * The keyword modifier; could be null.
         */
        protected Token modifier;

        /**
         * The version overrides; could be null.
         */
        protected List<VersionOverride> vers;

        /**
         * The injection list; could be null.
         */
        protected List<Parameter> injects;

        /**
         * The injector specifier; could be null.
         */
        protected NamedTypeExpression injector;

        protected long lEndPos;

        private static final Field[] CHILD_FIELDS =
                fieldsForNames(Import.class, "condition", "type", "vers", "injects", "injector");
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

    private transient Contribution m_contribution;

    private static final Field[] CHILD_FIELDS =
            fieldsForNames(CompositionNode.class, "condition", "type");
    }