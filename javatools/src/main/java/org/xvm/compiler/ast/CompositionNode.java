package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

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
        extends AstNode {
    // ----- constructors --------------------------------------------------------------------------

    public CompositionNode(Expression condition, Token keyword, TypeExpression type) {
        this.condition = condition;
        this.keyword   = keyword;
        this.type      = type;
    }

    /**
     * Copy constructor for CompositionNode.
     * <p>
     * Master clone() semantics:
     * <ul>
     *   <li>CHILD_FIELDS: "condition", "type" - deep copied by AstNode.clone()</li>
     *   <li>All other fields: shallow copied via Object.clone() bitwise copy</li>
     * </ul>
     * <p>
     * Order matches master clone(): all non-child fields FIRST, then children.
     *
     * @param original  the node to copy from
     */
    protected CompositionNode(@NotNull CompositionNode original) {
        super(Objects.requireNonNull(original));

        // Step 1: Copy ALL non-child fields FIRST (matches super.clone() behavior)
        this.keyword        = original.keyword;
        this.m_contribution = original.m_contribution;

        // Step 2: Deep copy children explicitly (CHILD_FIELDS: condition, type)
        this.condition = original.condition == null ? null : original.condition.copy();
        this.type      = original.type == null ? null : original.type.copy();

        // Step 3: Adopt copied children
        if (this.condition != null) {
            this.condition.setParent(this);
        }
        if (this.type != null) {
            this.type.setParent(this);
        }
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the condition that applies to the composition, or null
     */
    public Expression getCondition() {
        return condition;
    }

    /**
     * @return the keyword token used to define the composition
     */
    public Token getKeyword() {
        return keyword;
    }

    /**
     * @return the TypeExpression of the Composition
     */
    public TypeExpression getType() {
        return type;
    }

    /**
     * @return the Contribution associated with (created by) this composition
     */
    public Contribution getContribution() {
        return m_contribution;
    }

    /**
     * Set the Contribution associated with (created by) this composition.
     */
    public void setContribution(Contribution contribution) {
        this.m_contribution = contribution;
    }


    // ----- AstNode methods -----------------------------------------------------------------------

    @Override
    public Source getSource() {
        Source source = super.getSource();
        if (source == null) {
            source = condition == null ? type.getSource() : condition.getSource();
        }
        return source;
    }

    @Override
    public long getStartPosition() {
        return condition == null ? keyword.getStartPosition() : condition.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return condition == null ? type.getEndPosition() : condition.getEndPosition();
    }

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        return toStartString() + toEndString();
    }

    public String toStartString() {
        StringBuilder sb = new StringBuilder();

        if (condition != null) {
            sb.append("if (")
              .append(condition)
              .append(") { ");
        }

        sb.append(keyword.getId().TEXT);

        if (type != null) {
            sb.append(' ')
              .append(type);
        }

        return sb.toString();
    }

    protected String toEndString() {
        return condition == null ? "" : " }";
    }

    @Override
    public String getDumpDesc() {
        return toString();
    }


    // ----- inner class: Extends ------------------------------------------------------------------

    public static class Extends
            extends CompositionNode {
        public Extends(Expression condition, Token keyword, TypeExpression type) {
            super(condition, keyword, type);
            this.args = List.of();
        }

        public Extends(Expression condition, Token keyword, TypeExpression type,
                       @NotNull List<Expression> args, long lEndPos) {
            super(condition, keyword, type);
            this.args    = Objects.requireNonNull(args);
            this.lEndPos = lEndPos;
        }

        /**
         * Copy constructor.
         * <p>
         * Master clone() semantics:
         * <ul>
         *   <li>CHILD_FIELDS: "condition", "type", "args" - deep copied</li>
         *   <li>All other fields: shallow copied</li>
         * </ul>
         * <p>
         * Order matches master clone(): all non-child fields FIRST, then children.
         *
         * @param original  the Extends to copy from
         */
        protected Extends(@NotNull Extends original) {
            super(Objects.requireNonNull(original));

            // Step 1: Copy non-child fields FIRST
            this.lEndPos = original.lEndPos;

            // Step 2: Deep copy children explicitly (args - condition and type handled by super)
            this.args = original.args.stream().map(Expression::copy).toList();

            // Step 3: Adopt copied children
            adopt(this.args);
        }

        @Override
        public Extends copy() {
            return new Extends(this);
        }

        @Override
        public <R> R accept(AstVisitor<R> visitor) {
            return visitor.visit(this);
        }

        /**
         * @return the list of constructor arguments (empty if none)
         */
        @NotNull
        @Unmodifiable
        public List<Expression> getArgs() {
            return args;
        }

        @Override
        public long getEndPosition() {
            return lEndPos == 0 ? super.getEndPosition() : lEndPos;
        }

        @Override
        protected Field[] getChildFields() {
            return CHILD_FIELDS;
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();

            sb.append(toStartString());

            if (!args.isEmpty()) {
                sb.append('(')
                  .append(args.stream()
                          .map(Expression::toString)
                          .collect(Collectors.joining(", ")))
                  .append(')');
            }

            sb.append(toEndString());
            return sb.toString();
        }

        @ChildNode(index = 2, description = "Constructor arguments")
        @NotNull @Unmodifiable protected List<Expression> args;
        protected long             lEndPos;

        private static final Field[] CHILD_FIELDS = fieldsForNames(Extends.class,
                "condition", "type", "args");
    }


    // ----- inner class: Annotates ----------------------------------------------------------------

    public static class Annotates
            extends CompositionNode {
        public Annotates(AnnotationExpression annotation) {
            super(null, new Token(annotation.getStartPosition(), annotation.getStartPosition(),
                    Token.Id.ANNOTATION), annotation.type);
            this.annotation = annotation;
        }

        /**
         * Copy constructor.
         * <p>
         * Master clone() semantics:
         * <ul>
         *   <li>CHILD_FIELDS: "annotation" - deep copied</li>
         *   <li>All other fields: shallow copied (none in this class)</li>
         * </ul>
         *
         * @param original  the Annotates to copy from
         */
        protected Annotates(@NotNull Annotates original) {
            super(Objects.requireNonNull(original));

            // Deep copy child (annotation)
            this.annotation = original.annotation == null ? null : original.annotation.copy();

            // Adopt copied child
            if (this.annotation != null) {
                this.annotation.setParent(this);
            }
        }

        @Override
        public Annotates copy() {
            return new Annotates(this);
        }

        @Override
        public <R> R accept(AstVisitor<R> visitor) {
            return visitor.visit(this);
        }

        /**
         * @return the annotation expression
         */
        @NotNull
        public AnnotationExpression getAnnotation() {
            return annotation;
        }

        public Annotation ensureAnnotation(ConstantPool pool) {
            return annotation.ensureAnnotation(pool);
        }

        @Override
        public long getEndPosition() {
            List<Expression> listArgs = annotation.args;
            return listArgs != null && !listArgs.isEmpty()
                    ? listArgs.getLast().getEndPosition()
                    : super.getEndPosition();
        }

        @Override
        protected Field[] getChildFields() {
            return CHILD_FIELDS;
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();

            if (condition != null) {
                sb.append("if (")
                  .append(condition)
                  .append(") { ");
            }

            sb.append(keyword.getId().TEXT)
              .append(' ')
              .append(annotation.type);

            if (annotation.args != null) {
                sb.append('(')
                  .append(annotation.args.stream()
                          .map(Expression::toString)
                          .collect(Collectors.joining(", ")))
                  .append(')');
            }

            sb.append(toEndString());
            return sb.toString();
        }

        @ChildNode(index = 0, description = "Annotation expression")
        protected AnnotationExpression annotation;

        private static final Field[] CHILD_FIELDS = fieldsForNames(Annotates.class,
                "annotation");
    }


    // ----- inner class: Incorporates -------------------------------------------------------------

    public static class Incorporates
            extends CompositionNode {
        /**
         * Convenience constructor for non-conditional incorporates without arguments.
         */
        public Incorporates(Expression condition, Token keyword, TypeExpression type) {
            this(condition, keyword, type, List.of(), List.of());
        }

        public Incorporates(Expression condition, Token keyword, TypeExpression type,
                            @NotNull List<Expression> args, @NotNull List<Parameter> constraints) {
            super(condition, keyword, type);
            this.args        = Objects.requireNonNull(args);
            this.constraints = Objects.requireNonNull(constraints);
        }

        /**
         * Copy constructor.
         * <p>
         * Master clone() semantics:
         * <ul>
         *   <li>CHILD_FIELDS: "condition", "type", "args", "constraints" - deep copied</li>
         *   <li>All other fields: shallow copied (none in this class)</li>
         * </ul>
         *
         * @param original  the Incorporates to copy from
         */
        protected Incorporates(@NotNull Incorporates original) {
            super(Objects.requireNonNull(original));

            // Deep copy children explicitly
            this.args = original.args.stream().map(Expression::copy).toList();
            this.constraints = original.constraints.stream().map(Parameter::copy).toList();

            // Adopt copied children
            adopt(this.args);
            adopt(this.constraints);
        }

        @Override
        public Incorporates copy() {
            return new Incorporates(this);
        }

        @Override
        public <R> R accept(AstVisitor<R> visitor) {
            return visitor.visit(this);
        }

        /**
         * @return the list of constructor arguments (empty if none)
         */
        @NotNull
        @Unmodifiable
        public List<Expression> getArgs() {
            return args;
        }

        /**
         * @return true iff the incorporates clause is conditional based on the generic parameters
         *         of the specified type
         */
        public boolean isConditional() {
            return !constraints.isEmpty();
        }

        /**
         * @return list of constraints for conditional incorporates (empty if not conditional)
         */
        @NotNull
        @Unmodifiable
        public List<Parameter> getConstraints() {
            return constraints;
        }

        @Override
        public long getEndPosition() {
            return condition == null && !args.isEmpty()
                    ? args.getLast().getEndPosition()
                    : super.getEndPosition();
        }

        @Override
        protected Field[] getChildFields() {
            return CHILD_FIELDS;
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();

            if (condition != null) {
                sb.append("if (")
                  .append(condition)
                  .append(") { ");
            }

            sb.append(keyword.getId().TEXT);

            if (isConditional()) {
                // special handling for "incorporates conditional <T1 extends T2, ..>"
                var sType = type.toString();
                sb.append(" conditional ")
                  .append(sType, 0, sType.indexOf('<'));

                sb.append('<')
                  .append(constraints.stream()
                          .map(Parameter::toTypeParamString)
                          .collect(Collectors.joining(", ")))
                  .append('>');
            } else {
                sb.append(' ')
                  .append(type);
            }

            if (!args.isEmpty()) {
                sb.append('(')
                  .append(args.stream()
                          .map(Expression::toString)
                          .collect(Collectors.joining(", ")))
                  .append(')');
            }

            sb.append(toEndString());
            return sb.toString();
        }

        @ChildNode(index = 2, description = "Constructor arguments")
        @NotNull @Unmodifiable protected List<Expression> args;
        @ChildNode(index = 3, description = "Conditional constraints")
        @NotNull @Unmodifiable protected List<Parameter>  constraints;

        private static final Field[] CHILD_FIELDS = fieldsForNames(Incorporates.class,
                "condition", "type", "args", "constraints");
    }


    // ----- inner class: Implements ---------------------------------------------------------------

    public static class Implements
            extends CompositionNode {
        public Implements(Expression condition, Token keyword, TypeExpression type) {
            super(condition, keyword, type);
        }

        /**
         * Copy constructor.
         * <p>
         * Master clone() semantics:
         * <ul>
         *   <li>CHILD_FIELDS: "condition", "type" - deep copied via base class</li>
         *   <li>All other fields: shallow copied</li>
         * </ul>
         *
         * @param original  the Implements to copy from
         */
        protected Implements(@NotNull Implements original) {
            super(Objects.requireNonNull(original));
        }

        @Override
        public Implements copy() {
            return new Implements(this);
        }

        @Override
        public <R> R accept(AstVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }


    // ----- inner class: Delegates ----------------------------------------------------------------

    public static class Delegates
            extends CompositionNode {
        public Delegates(Expression condition, Token keyword, TypeExpression type,
                         Expression delegatee, long lEndPos) {
            super(condition, keyword, type);
            this.delegatee = delegatee;
            this.lEndPos   = lEndPos;
        }

        /**
         * Copy constructor.
         * <p>
         * Master clone() semantics:
         * <ul>
         *   <li>CHILD_FIELDS: "condition", "type", "delegatee" - deep copied</li>
         *   <li>All other fields: shallow copied</li>
         * </ul>
         * <p>
         * Order matches master clone(): all non-child fields FIRST, then children.
         *
         * @param original  the Delegates to copy from
         */
        protected Delegates(@NotNull Delegates original) {
            super(Objects.requireNonNull(original));

            // Step 1: Copy non-child fields FIRST
            this.lEndPos = original.lEndPos;
            this.name    = original.name;

            // Step 2: Deep copy child explicitly
            this.delegatee = original.delegatee == null ? null : original.delegatee.copy();

            // Step 3: Adopt copied child
            if (this.delegatee != null) {
                this.delegatee.setParent(this);
            }
        }

        @Override
        public Delegates copy() {
            return new Delegates(this);
        }

        @Override
        public <R> R accept(AstVisitor<R> visitor) {
            return visitor.visit(this);
        }

        /**
         * @return the expression denoting the target of the delegation, such as the name of the
         *         property that holds the reference to delegate to
         */
        public Expression getDelegatee() {
            return delegatee;
        }

        public void setPropertyName(String sName) {
            assert name == null;
            name = sName;
        }

        public String getPropertyName() {
            assert name != null;
            return name;
        }

        @Override
        public long getEndPosition() {
            return lEndPos;
        }

        @Override
        protected Field[] getChildFields() {
            return CHILD_FIELDS;
        }

        @Override
        public String toString() {
            return toStartString() + '(' + delegatee + ')' + toEndString();
        }

        @ChildNode(index = 2, description = "Delegatee expression")
        protected Expression delegatee;
        protected long       lEndPos;

        @ComputedState
        protected String name;

        private static final Field[] CHILD_FIELDS =
                fieldsForNames(Delegates.class, "condition", "type", "delegatee");
    }


    // ----- inner class: Into ---------------------------------------------------------------------

    public static class Into
            extends CompositionNode {
        public Into(Expression condition, Token keyword, TypeExpression type) {
            super(condition, keyword, type);
        }

        /**
         * Copy constructor.
         * <p>
         * Master clone() semantics:
         * <ul>
         *   <li>CHILD_FIELDS: "condition", "type" - deep copied via base class</li>
         *   <li>All other fields: shallow copied</li>
         * </ul>
         *
         * @param original  the Into to copy from
         */
        protected Into(@NotNull Into original) {
            super(Objects.requireNonNull(original));
        }

        @Override
        public Into copy() {
            return new Into(this);
        }

        @Override
        public <R> R accept(AstVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }


    // ----- inner class: Import -------------------------------------------------------------------

    /**
     * Represents a module import on a package declaration.
     */
    public static class Import
            extends CompositionNode {
        /**
         * Convenience constructor for imports without version overrides or injections.
         */
        public Import(Expression condition, Token keyword, Token modifier, NamedTypeExpression type,
                      long lEndPos) {
            this(condition, keyword, modifier, type, List.of(), List.of(), null, lEndPos);
        }

        public Import(Expression condition, Token keyword, Token modifier, NamedTypeExpression type,
                      @NotNull List<VersionOverride> vers, @NotNull List<Parameter> injects,
                      NamedTypeExpression injector, long lEndPos) {
            super(condition, keyword, type);
            this.modifier = modifier;
            this.vers     = Objects.requireNonNull(vers);
            this.injects  = Objects.requireNonNull(injects);
            this.injector = injector;
            this.lEndPos  = lEndPos;
        }

        /**
         * Copy constructor.
         * <p>
         * Master clone() semantics:
         * <ul>
         *   <li>CHILD_FIELDS: "condition", "type", "vers", "injects", "injector" - deep copied</li>
         *   <li>All other fields: shallow copied</li>
         * </ul>
         * <p>
         * Order matches master clone(): all non-child fields FIRST, then children.
         *
         * @param original  the Import to copy from
         */
        protected Import(@NotNull Import original) {
            super(Objects.requireNonNull(original));

            // Step 1: Copy non-child fields FIRST
            this.modifier = original.modifier;
            this.lEndPos  = original.lEndPos;

            // Step 2: Deep copy children explicitly
            this.vers = original.vers.stream().map(VersionOverride::copy).toList();
            this.injects = original.injects.stream().map(Parameter::copy).toList();
            this.injector = original.injector == null ? null : original.injector.copy();

            // Step 3: Adopt copied children
            adopt(this.vers);
            adopt(this.injects);
            if (this.injector != null) {
                this.injector.setParent(this);
            }
        }

        @Override
        public Import copy() {
            return new Import(this);
        }

        @Override
        public <R> R accept(AstVisitor<R> visitor) {
            return visitor.visit(this);
        }

        /**
         * @return the modifier keyword, or null
         */
        public Token getModifier() {
            return modifier;
        }

        /**
         * @return the implied modifier keyword as a token ID
         */
        public Token.Id getImplicitModifier() {
            return modifier == null
                    ? Token.Id.REQUIRED
                    : modifier.getId();
        }

        /**
         * @return a version tree specifying versions allowed/preferred (true) and avoided (false)
         */
        public VersionTree<Boolean> getAllowVersionTree() {
            VersionTree<Boolean> vtree = new VersionTree<>();
            for (VersionOverride override : vers) {
                Version ver = override.getVersion();
                Boolean BPrevAllow = vtree.get(ver);
                boolean fAllow = override.isAllowed();
                if (BPrevAllow != null && fAllow != BPrevAllow.booleanValue()) {
                    throw new IllegalStateException(
                            "version " + ver + " is both allowed and disallowed");
                } else {
                    vtree.put(ver, fAllow);
                }
            }
            return vtree;
        }

        /**
         * @return the list of preferred versions
         */
        public List<Version> getPreferVersionList() {
            if (vers.isEmpty()) {
                return List.of();
            }

            List<Version> list = new ArrayList<>();
            for (VersionOverride override : vers) {
                if (override.isPreferred()) {
                    list.add(override.getVersion());
                }
            }
            return list;
        }

        /**
         * @return the version overrides for this import (empty if none)
         */
        @NotNull
        @Unmodifiable
        public List<VersionOverride> getVersionOverrides() {
            return vers;
        }

        /**
         * @return the name of the singleton injector class, if specified
         */
        public Optional<NamedTypeExpression> getInjector() {
            return Optional.ofNullable(injector);
        }

        /**
         * @return the types and names of injections that will get handled by the specified injector
         *         (empty if none)
         */
        @NotNull
        @Unmodifiable
        public List<Parameter> getSpecificInjections() {
            return injects;
        }

        @Override
        public long getEndPosition() {
            return lEndPos;
        }

        @Override
        protected Field[] getChildFields() {
            return CHILD_FIELDS;
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();

            sb.append(keyword.getId().TEXT)
              .append(' ');

            if (modifier != null) {
                sb.append(modifier.getId().TEXT)
                  .append(' ');
            }

            sb.append(type);

            if (!vers.isEmpty()) {
                boolean first = true;
                for (VersionOverride ver : vers) {
                    if (first) {
                        sb.append(' ');
                        first = false;
                    } else {
                        sb.append("\n        ");
                    }
                    sb.append(ver);
                }
            }

            if (!injects.isEmpty()) {
                sb.append("\n        ")
                  .append(Token.Id.INJECT.TEXT)
                  .append(' ')
                  .append('(')
                  .append(injects.stream()
                          .map(Parameter::toString)
                          .collect(Collectors.joining(", ")))
                  .append(')');
            }

            if (injector != null) {
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
         * The version overrides (empty if none).
         */
        @ChildNode(index = 2, description = "Version overrides")
        @NotNull @Unmodifiable protected List<VersionOverride> vers;

        /**
         * The injection list (empty if none).
         */
        @ChildNode(index = 3, description = "Injection list")
        @NotNull @Unmodifiable protected List<Parameter> injects;

        /**
         * The injector specifier; could be null.
         */
        @ChildNode(index = 4, description = "Injector specifier")
        protected NamedTypeExpression injector;

        protected long lEndPos;

        private static final Field[] CHILD_FIELDS =
                fieldsForNames(Import.class, "condition", "type", "vers", "injects", "injector");
    }


    // ----- inner class: Default ------------------------------------------------------------------

    public static class Default
            extends CompositionNode {
        public Default(Expression condition, Token keyword, Expression expr, long lEndPos) {
            super(condition, keyword, null);
            this.expr    = expr;
            this.lEndPos = lEndPos;
        }

        /**
         * Copy constructor.
         * <p>
         * Master clone() semantics:
         * <ul>
         *   <li>CHILD_FIELDS: "condition", "expr" - deep copied</li>
         *   <li>All other fields: shallow copied</li>
         * </ul>
         * <p>
         * Order matches master clone(): all non-child fields FIRST, then children.
         *
         * @param original  the Default to copy from
         */
        protected Default(@NotNull Default original) {
            super(Objects.requireNonNull(original));

            // Step 1: Copy non-child fields FIRST
            this.lEndPos = original.lEndPos;

            // Step 2: Deep copy child explicitly
            this.expr = original.expr == null ? null : original.expr.copy();

            // Step 3: Adopt copied child
            if (this.expr != null) {
                this.expr.setParent(this);
            }
        }

        @Override
        public Default copy() {
            return new Default(this);
        }

        @Override
        public <R> R accept(AstVisitor<R> visitor) {
            return visitor.visit(this);
        }

        /**
         * @return the expression representing the default value for the type composition
         */
        public Expression getValueExpression() {
            return expr;
        }

        @Override
        public long getEndPosition() {
            return lEndPos;
        }

        @Override
        protected Field[] getChildFields() {
            return CHILD_FIELDS;
        }

        @Override
        public String toString() {
            return toStartString() + '(' + expr + ')' + toEndString();
        }

        @ChildNode(index = 1, description = "Default value expression")
        protected Expression expr;
        protected long       lEndPos;

        private static final Field[] CHILD_FIELDS = fieldsForNames(Default.class, "condition", "expr");
    }


    // ----- fields --------------------------------------------------------------------------------

    @ChildNode(index = 0, description = "Conditional expression")
    protected Expression     condition;
    protected Token          keyword;
    @ChildNode(index = 1, description = "Type being composed")
    protected TypeExpression type;

    @ComputedState
    private Contribution m_contribution;

    private static final Field[] CHILD_FIELDS =
            fieldsForNames(CompositionNode.class, "condition", "type");
}