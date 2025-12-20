package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
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
    public <T> T forEachChild(Function<AstNode, T> visitor) {
        T result;
        if (condition != null && (result = visitor.apply(condition)) != null) {
            return result;
        }
        if (type != null && (result = visitor.apply(type)) != null) {
            return result;
        }
        return null;
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
            this.args = new ArrayList<>();
        }

        public Extends(Expression condition, Token keyword, TypeExpression type,
                       List<Expression> args, long lEndPos) {
            super(condition, keyword, type);
            this.args    = args == null ? new ArrayList<>() : new ArrayList<>(args);
            this.lEndPos = lEndPos;
        }

        @Override
        public long getEndPosition() {
            return lEndPos == 0 ? super.getEndPosition() : lEndPos;
        }

        @Override
        public <T> T forEachChild(Function<AstNode, T> visitor) {
            T result = super.forEachChild(visitor);
            if (result != null) {
                return result;
            }
            for (Expression arg : args) {
                if ((result = visitor.apply(arg)) != null) {
                    return result;
                }
            }
            return null;
        }

        @Override
        protected AstNode withChildren(List<AstNode> children) {
            int index = 0;
            Expression condition = this.condition == null ? null : (Expression) children.get(index++);
            TypeExpression type = (TypeExpression) children.get(index++);
            List<Expression> args = new ArrayList<>();
            while (index < children.size()) {
                args.add((Expression) children.get(index++));
            }
            return new Extends(condition, keyword, type, args, lEndPos);
        }

        @Override
        public String toString() {
            return toStartString()
                 + (args.isEmpty() ? "" : "(" + args.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")")
                 + toEndString();
        }

        protected List<Expression> args;
        protected long             lEndPos;
    }


    // ----- inner class: Annotates ----------------------------------------------------------------

    public static class Annotates
            extends CompositionNode {
        public Annotates(AnnotationExpression annotation) {
            super(null, new Token(annotation.getStartPosition(), annotation.getStartPosition(),
                    Token.Id.ANNOTATION), annotation.type);
            this.annotation = annotation;
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
        public <T> T forEachChild(Function<AstNode, T> visitor) {
            return visitor.apply(annotation);
        }

        @Override
        protected AstNode withChildren(List<AstNode> children) {
            return new Annotates((AnnotationExpression) children.get(0));
        }

        protected AnnotationExpression annotation;
    }


    // ----- inner class: Incorporates -------------------------------------------------------------

    public static class Incorporates
            extends CompositionNode {
        public Incorporates(Expression condition, Token keyword, TypeExpression type,
                            @NotNull List<Expression> args, List<Parameter> constraints) {
            super(condition, keyword, type);
            this.args        = args;
            this.constraints = constraints;
        }

        /**
         * @return true iff the incorporates clause is conditional based on the generic parameters
         *         of the specified type
         */
        public boolean isConditional() {
            return constraints != null;
        }

        /**
         * @return list of constraints for conditional incorporates; null otherwise
         */
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
        public <T> T forEachChild(Function<AstNode, T> visitor) {
            T result = super.forEachChild(visitor);
            if (result != null) {
                return result;
            }
            for (Expression arg : args) {
                if ((result = visitor.apply(arg)) != null) {
                    return result;
                }
            }
            if (constraints != null) {
                for (Parameter constraint : constraints) {
                    if ((result = visitor.apply(constraint)) != null) {
                        return result;
                    }
                }
            }
            return null;
        }

        @Override
        protected AstNode withChildren(List<AstNode> children) {
            int index = 0;
            Expression condition = this.condition == null ? null : (Expression) children.get(index++);
            TypeExpression type = (TypeExpression) children.get(index++);
            List<Expression> args = new ArrayList<>();
            int argsCount = this.args.size();
            for (int i = 0; i < argsCount; i++) {
                args.add((Expression) children.get(index++));
            }
            List<Parameter> constraints = null;
            if (this.constraints != null) {
                constraints = new ArrayList<>();
                while (index < children.size()) {
                    constraints.add((Parameter) children.get(index++));
                }
            }
            return new Incorporates(condition, keyword, type, args, constraints);
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();

            if (condition != null) {
                sb.append("if (").append(condition).append(") { ");
            }

            sb.append(keyword.getId().TEXT);

            if (isConditional()) {
                // special handling for "incorporates conditional <T1 extends T2, ..>"
                var sType = type.toString();
                sb.append(" conditional ")
                  .append(sType, 0, sType.indexOf('<'))
                  .append('<')
                  .append(constraints.stream().map(Parameter::toTypeParamString).collect(Collectors.joining(", ")))
                  .append('>');
            } else {
                sb.append(' ').append(type);
            }

            if (args != null && !args.isEmpty()) {
                sb.append('(')
                  .append(args.stream().map(Object::toString).collect(Collectors.joining(", ")))
                  .append(')');
            }

            return sb.append(toEndString()).toString();
        }

        protected List<Expression> args;
        protected List<Parameter>  constraints;
    }


    // ----- inner class: Implements ---------------------------------------------------------------

    public static class Implements
            extends CompositionNode {
        public Implements(Expression condition, Token keyword, TypeExpression type) {
            super(condition, keyword, type);
        }

        @Override
        protected AstNode withChildren(List<AstNode> children) {
            int index = 0;
            Expression condition = this.condition == null ? null : (Expression) children.get(index++);
            TypeExpression type = (TypeExpression) children.get(index++);
            return new Implements(condition, keyword, type);
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
        public <T> T forEachChild(Function<AstNode, T> visitor) {
            T result = super.forEachChild(visitor);
            if (result != null) {
                return result;
            }
            return visitor.apply(delegatee);
        }

        @Override
        protected AstNode withChildren(List<AstNode> children) {
            int index = 0;
            Expression condition = this.condition == null ? null : (Expression) children.get(index++);
            TypeExpression type = (TypeExpression) children.get(index++);
            Expression delegatee = (Expression) children.get(index++);
            return new Delegates(condition, keyword, type, delegatee, lEndPos);
        }

        @Override
        public String toString() {
            return toStartString() + '(' + delegatee + ')' + toEndString();
        }

        protected Expression delegatee;
        protected long       lEndPos;

        @Derived
    protected String name;
    }


    // ----- inner class: Into ---------------------------------------------------------------------

    public static class Into
            extends CompositionNode {
        public Into(Expression condition, Token keyword, TypeExpression type) {
            super(condition, keyword, type);
        }

        @Override
        protected AstNode withChildren(List<AstNode> children) {
            int index = 0;
            Expression condition = this.condition == null ? null : (Expression) children.get(index++);
            TypeExpression type = (TypeExpression) children.get(index++);
            return new Into(condition, keyword, type);
        }
    }


    // ----- inner class: Import -------------------------------------------------------------------

    /**
     * Represents a module import on a package declaration.
     */
    public static class Import
            extends CompositionNode {
        public Import(Expression condition, Token keyword, Token modifier, NamedTypeExpression type,
                      List<VersionOverride> vers, List<Parameter> injects,
                      NamedTypeExpression injector, long lEndPos) {
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
            if (vers != null) {
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
            }
            return vtree;
        }

        /**
         * @return the list of preferred versions
         */
        public List<Version> getPreferVersionList() {
            if (vers == null) {
                return Collections.emptyList();
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
         * @return the name of the singleton injector class
         */
        NamedTypeExpression getInjector() {
            return injector;
        }

        /**
         * @return the types and names of injections that will get handled by the specified injector
         */
        List<Parameter> getSpecificInjections() {
            return injects;
        }

        @Override
        public long getEndPosition() {
            return lEndPos;
        }

        @Override
        public <T> T forEachChild(Function<AstNode, T> visitor) {
            T result = super.forEachChild(visitor);
            if (result != null) {
                return result;
            }
            if (vers != null) {
                for (VersionOverride ver : vers) {
                    if ((result = visitor.apply(ver)) != null) {
                        return result;
                    }
                }
            }
            if (injects != null) {
                for (Parameter inject : injects) {
                    if ((result = visitor.apply(inject)) != null) {
                        return result;
                    }
                }
            }
            if (injector != null && (result = visitor.apply(injector)) != null) {
                return result;
            }
            return null;
        }

        @Override
        protected AstNode withChildren(List<AstNode> children) {
            int index = 0;
            Expression condition = this.condition == null ? null : (Expression) children.get(index++);
            NamedTypeExpression type = (NamedTypeExpression) children.get(index++);
            List<VersionOverride> vers = null;
            if (this.vers != null) {
                vers = new ArrayList<>();
                for (int i = 0; i < this.vers.size(); i++) {
                    vers.add((VersionOverride) children.get(index++));
                }
            }
            List<Parameter> injects = null;
            if (this.injects != null) {
                injects = new ArrayList<>();
                for (int i = 0; i < this.injects.size(); i++) {
                    injects.add((Parameter) children.get(index++));
                }
            }
            NamedTypeExpression injector = this.injector == null ? null : (NamedTypeExpression) children.get(index++);
            return new Import(condition, keyword, modifier, type, vers, injects, injector, lEndPos);
        }

        @Override
        public String toString() {
            var sb = new StringBuilder()
                    .append(keyword.getId().TEXT)
                    .append(' ');

            if (modifier != null) {
                sb.append(modifier.getId().TEXT).append(' ');
            }

            sb.append(type);

            if (vers != null && !vers.isEmpty()) {
                sb.append(' ')
                  .append(vers.stream().map(Object::toString).collect(Collectors.joining("\n        ")));
            }

            if (injects != null && !injects.isEmpty()) {
                sb.append("\n        ")
                  .append(Token.Id.INJECT.TEXT)
                  .append(" (")
                  .append(injects.stream().map(Object::toString).collect(Collectors.joining(", ")))
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
        public <T> T forEachChild(Function<AstNode, T> visitor) {
            T result;
            if (condition != null && (result = visitor.apply(condition)) != null) {
                return result;
            }
            return visitor.apply(expr);
        }

        @Override
        protected AstNode withChildren(List<AstNode> children) {
            int index = 0;
            Expression condition = this.condition == null ? null : (Expression) children.get(index++);
            Expression expr = (Expression) children.get(index++);
            return new Default(condition, keyword, expr, lEndPos);
        }

        @Override
        public String toString() {
            return toStartString() + '(' + expr + ')' + toEndString();
        }

        protected Expression expr;
        protected long       lEndPos;
    }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression     condition;
    protected Token          keyword;
    protected TypeExpression type;

    @Derived
    private Contribution m_contribution;
}