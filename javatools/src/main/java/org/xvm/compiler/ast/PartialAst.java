package org.xvm.compiler.ast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.compiler.Token;

/**
 * Internal bridge from {@code ast.partial} to ordinary compiler operations. Java subpackages do
 * not share package access, so this class keeps the existing AST members protected or package
 * private while partial-analysis policy lives in its own package.
 *
 * <p>All nodes, contexts and results belong to the current compiler worker and attempt. This is
 * not an embedding API: hosts consume the results exposed by {@code EmbeddingSupport}. Methods
 * delegate to ordinary validation and fitting; they neither select incomplete calls for emission
 * nor install proposed expressions into the source tree.
 */
public final class PartialAst {
    private PartialAst() {}

    /**
     * Obtain the constant pool visible from a source node.
     *
     * @param node  the node in the current compilation attempt
     * @return its compilation's pool
     */
    public static ConstantPool pool(AstNode node) {
        return node.pool();
    }

    /**
     * Give disposable syntax a lexical parent without changing its internal child parentage.
     *
     * @param <T>    the concrete node type, preserved for the caller
     * @param node   the disposable node; never an attached child being moved between live trees
     * @param scope  the lexical parent used for resolution
     * @return the same node
     */
    public static <T extends AstNode> T inScope(T node, AstNode scope) {
        node.setParent(scope);
        return node;
    }

    /**
     * Parent a newly built trial tree, including its descendants, without attaching it to a source child field.
     *
     * @param <T>    the concrete node type, preserved for the caller
     * @param node   the trial tree
     * @param scope  its lexical parent, or null for a trial root
     * @return the same tree with descendant parent links installed
     */
    public static <T extends AstNode> T parentTree(T node, AstNode scope) {
        inScope(node, scope).introduceParentage();
        return node;
    }

    /**
     * Validate an intact expression through the ordinary compiler rules. The caller controls
     * whether it is retained source or a disposable proposal and owns any replacement it returns.
     *
     * @param expression  the expression to validate
     * @param ctx         its live compiler context
     * @param required    the required type, or null when unconstrained
     * @param errs        the listener and abort policy
     * @return the validated replacement, or null on failure
     */
    public static Expression validate(Expression expression, Context ctx, TypeConstant required, ErrorListener errs) {
        return expression.validate(ctx, required, errs);
    }

    /**
     * Query type information with the ordinary expression's access rules.
     *
     * @param expression  the expression supplying lexical ownership
     * @param ctx         its live compiler context
     * @param type        the receiver type to inspect
     * @param errs        the listener and abort policy
     * @return the type information used by ordinary validation
     */
    public static TypeInfo typeInfo(Expression expression, Context ctx, TypeConstant type, ErrorListener errs) {
        return expression.getTypeInfo(ctx, type, errs);
    }

    /**
     * Initialize lazy parameter registers before enumerating visible variable names.
     *
     * @param ctx  the cursor's live validation context
     * @return the names, without making any claim about readability or narrowed types
     */
    public static Set<String> variableNames(Context ctx) {
        Stream.iterate(ctx, Objects::nonNull, Context::getOuterContext).forEach(Context::getNameMap);
        Set<String> names = new HashSet<>();
        ctx.collectVariables(names);
        return names;
    }

    /**
     * Read already established explicit and wildcard imports without retaining the block's maps.
     *
     * @param block  the enclosing source block
     * @return candidate import names; later ordinary lookup still checks visibility
     */
    public static Set<String> importNames(StatementBlock block) {
        Set<String> names = new HashSet<>();
        if (block.imports != null) {
            names.addAll(block.imports.keySet());
        }
        if (block.importsWild != null) {
            block.importsWild.forEach(statement -> {
                if (statement.getNameResolver().getConstant() instanceof IdentityConstant identity
                        && identity.getComponent() != null) {
                    names.addAll(identity.getComponent().getChildByNameMap().keySet());
                }
            });
        }
        return names;
    }

    /**
     * Inspect a written type qualifier without adopting it into another tree.
     *
     * @param type  the written named type
     * @return its borrowed qualifier, or null when absent
     */
    public static TypeExpression qualifier(NamedTypeExpression type) {
        return type.left;
    }

    /**
     * Retain the positions of all written name segments, not just their string values.
     *
     * @param type  the written named type
     * @return a read-only snapshot containing the borrowed source tokens
     */
    public static List<Token> nameTokens(NamedTypeExpression type) {
        return List.copyOf(type.names);
    }

    /**
     * Build a trial reference to the same name and access spelling without type arguments.
     *
     * @param type  the unqualified written type whose name is being resolved
     * @return a fresh, unparented type reference
     */
    public static NamedTypeExpression typeReference(NamedTypeExpression type) {
        return new NamedTypeExpression(null, type.names, type.access, null, null, type.getEndPosition());
    }

    /**
     * Inspect the source expression retained by a failed type parse.
     *
     * @param type  the failed type wrapper
     * @return its borrowed written expression
     */
    public static Expression writtenType(BadTypeExpression type) {
        return type.nonType;
    }

    /**
     * Test for an anonymous source body, including after preparation registers its class shell.
     *
     * @param creation  the source construction
     * @return whether it retains an anonymous body
     */
    public static boolean hasAnonymousBody(NewExpression creation) {
        return creation.body != null;
    }

    /**
     * Prepare the ordinary construction receiver and type without synthesizing a forwarding
     * constructor or emitting the unfinished operation.
     *
     * @param creation  the owned source construction or its disposable trial copy
     * @param ctx       the trial validation context
     * @param required  the contextual type, or null when unconstrained
     * @param errs      the listener and abort policy
     * @return prepared types, or null if preparation failed
     */
    public static Construction prepareConstruction(NewExpression creation, Context ctx, TypeConstant required, ErrorListener errs) {
        var prepared = creation.prepareConstruction(ctx, required, errs, true);
        return prepared == null ? null : new Construction(prepared.result(), prepared.target(),
                prepared.superType(), prepared.requiresNewable());
    }

    /**
     * Prepared types for this construction query; retains no private construction plan or context.
     *
     * @param result           the inferred result type
     * @param target           the type whose constructors are inspected
     * @param superType        the prepared superclass type for anonymous construction, otherwise null
     * @param requiresNewable  whether ordinary construction requires a newable target
     */
    public record Construction(TypeConstant result, TypeConstant target, TypeConstant superType, boolean requiresNewable) {}

    /**
     * Identify the real fixed-size constructor selected by array-dimension syntax.
     *
     * @param creation  the prepared construction
     * @return the supply constructor, or null for other type syntax
     */
    public static MethodConstant arrayConstructor(NewExpression creation) {
        return creation.type instanceof ArrayTypeExpression array ? array.getSupplyConstructor() : null;
    }

    /**
     * Arrange named arguments using the ordinary compiler's parameter-order rules.
     *
     * @param site       the source operation supplying diagnostic positions
     * @param method     the candidate declaration
     * @param arguments  the owned trial arguments, already in parameter order when unnamed
     * @param errs       the listener and abort policy
     * @return the parameter-ordered arguments, or null if labels cannot be arranged
     */
    public static List<Expression> orderArguments(AstNode site, MethodStructure method, List<Expression> arguments, ErrorListener errs) {
        return site.containsNamedArgs(arguments) ? site.rearrangeNamedArgs(method, arguments, errs) : arguments;
    }

    /**
     * Apply ordinary post-argument class inference to a prepared construction.
     *
     * @param creation   the prepared source construction or trial copy
     * @param ctx        the trial validation context
     * @param owner      the class declaring the candidate constructor
     * @param method     the candidate constructor
     * @param arguments  its validated arguments in parameter order
     * @return the inferred class type, or null when no refinement was obtained
     */
    public static TypeConstant inferConstruction(NewExpression creation, Context ctx, ClassStructure owner,
                                                MethodStructure method, List<Expression> arguments) {
        return creation.inferTypeFromConstructor(ctx, owner, method, arguments);
    }

    /**
     * Fit one method using ordinary named-argument, conversion and inference rules. The callback
     * runs during fitting and must copy any facts it retains; it does not select a final overload.
     *
     * @param site       the source operation supplying pool and diagnostic positions
     * @param ctx        the enclosing context; fitting uses a child context
     * @param target     the receiver type
     * @param info       its type information
     * @param method     the single candidate to fit
     * @param arguments  disposable written arguments
     * @param errs       the listener and abort policy
     * @param matched    receives each fitted signature and ordered argument list synchronously
     */
    public static void matchMethod(AstNode site, Context ctx, TypeConstant target, TypeInfo info, MethodConstant method,
                                   List<Expression> arguments, ErrorListener errs, Consumer<MethodMatch> matched) {
        var named = site.collectNamedArgs(arguments, errs);
        if (named == null || errs.isAbortDesired()) {
            return;
        }
        Set<MethodConstant> direct = new HashSet<>();
        Set<MethodConstant> converting = new HashSet<>();
        site.collectMatchingMethods(ctx.enter(), target, info, Set.of(method), arguments, false, named,
                null, direct, converting, new HashMap<>(), errs, (signature, ordered) ->
                        matched.accept(new MethodMatch(signature, ordered, converting.contains(method))));
    }

    /**
     * Borrowed values delivered synchronously by the ordinary argument fitter.
     *
     * @param signature  the fitted signature before partial-query presentation adjustments
     * @param arguments  the argument expressions in parameter order; valid during the callback
     * @param converting whether this candidate requires conversion
     */
    public record MethodMatch(SignatureConstant signature, List<Expression> arguments, boolean converting) {}
}
