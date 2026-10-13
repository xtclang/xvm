package org.xvm.compiler.ast.partial;

import java.util.List;
import java.util.Optional;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.Context;
import org.xvm.compiler.ast.Expression;
import org.xvm.compiler.ast.LabeledExpression;
import org.xvm.compiler.ast.LambdaExpression;
import org.xvm.compiler.ast.NonBindingExpression;
import org.xvm.compiler.ast.Parameter;
import org.xvm.compiler.ast.PartialAst;
import org.xvm.compiler.ast.bindings.CursorBinding;

/**
 * Semantic entry points used by retained incomplete nodes. Contexts and bindings belong to the
 * current compilation attempt; embedding hosts consume its published results instead of calling
 * these operations. The ordinary compiler bridge supplies validation without widening AST member
 * visibility. No incomplete operation becomes an emittable value.
 */
final class PartialQueries {
    private PartialQueries() {}

    /**
     * Inspect intact children in their real context, without selecting the incomplete operation.
     *
     * @param site      the retained incomplete source site
     * @param ctx       the live compiler context that owns the query
     * @param required  the contextual required type, or null when unconstrained
     * @param errs      the diagnostic listener and abort policy for this operation
     */
    static void inspect(IncompleteStatement site, Context ctx, TypeConstant required, ErrorListener errs) {
        var bindings = ctx.getCursorBindings();
        bindings.begin(site);
        if (bindings.isEnabled() && !errs.isAbortDesired()) {
            var scope = CursorScope.capture(ctx)
                    .withEnclosingExpressions(CursorScope.enclosingValues(site, ctx, required, errs));
            bindings.record(site, site.isNameCompletion()
                    ? scope.withTypes(CursorScope.types(site, ctx, errs)) : scope);
        }
        site.getReceiver().ifPresent(receiver -> {
            Expression validated = PartialAst.validate(receiver, ctx, null, errs);
            if (validated != null) {
                AstNode owner = site.isCall() ? site.getTarget() : site;
                owner.replaceChild(receiver, validated);
            }
        });
        boolean partialTarget = site.isCall() && PartialSyntax.contains(site.getTarget());
        if (partialTarget && !errs.isAbortDesired()) {
            // A member prefix before existing arguments owns the original receiver. Inspect it
            // in place; a trial callee clone cannot supply bindings for the retained source site.
            PartialAst.validate(site.getTarget(), ctx, null, errs);
        }
        CursorBinding call = bindings.isEnabled() && site.isCall() && !partialTarget && !errs.isAbortDesired()
                ? PartialCallResolver.inspect(site, ctx, required, errs) : null;
        if (call != null) {
            bindings.record(site, call);
        }
        site.getArguments().stream().takeWhile(argument -> !errs.isAbortDesired()).forEach(argument -> {
            // No single expected type is selected. Candidate fitting inspects trial copies above.
            Expression value = argument instanceof LabeledExpression labeled
                    ? labeled.getUnderlyingExpression() : argument;
            if (!(value instanceof NonBindingExpression || value instanceof LambdaExpression)) {
                Expression validated = PartialAst.validate(argument, ctx, null, errs);
                if (validated != null) {
                    site.replaceChild(argument, validated);
                }
            }
        });
        if (call != null && !errs.isAbortDesired()) {
            PartialSyntax.argument(site).ifPresent(argument -> bindings.record(argument.cursor(), call));
        }
    }

    /**
     * Resolve an unfinished header in its explicit lexical scope, without registering formals.
     *
     * @param site            the retained incomplete source site
     * @param scope           the enclosing lexical scope used to resolve the unfinished header
     * @param writtenFormals  written formal parameters that shadow outer names without registering identities
     * @param errs            the diagnostic listener and abort policy for this operation
     *
     * @return visible type and formal facts, or empty when no enclosing class scope is available
     */
    static Optional<CursorBinding> declarationBinding(IncompleteStatement site, AstNode scope,
            List<Parameter> writtenFormals, ErrorListener errs) {
        return scope.getComponent() instanceof ClassStructure owner
                ? Optional.of(new CursorBinding(List.of(), owner.getFormalType(), false)
                        .withTypes(CursorScope.declarationTypes(site, scope, writtenFormals, errs))
                        .withFormals(CursorScope.declarationFormals(site, scope, writtenFormals, errs)))
                : Optional.empty();
    }
}
