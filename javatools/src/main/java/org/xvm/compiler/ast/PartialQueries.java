package org.xvm.compiler.ast;

import java.util.List;
import java.util.Optional;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.CursorBinding;
import org.xvm.compiler.ast.partial.IncompleteStatement;
import org.xvm.compiler.ast.partial.PartialSyntax;

/**
 * Compiler-internal semantic operations for retained incomplete syntax. Public only to connect
 * the partial syntax package to ordinary validation and inference; embedding hosts query their
 * compilation results instead. Contexts and bindings belong to the current compilation attempt.
 * This service retains no state and never turns an incomplete operation into an emittable value.
 */
public final class PartialQueries {
    private PartialQueries() {}

    /** Inspect intact children in their real context, without selecting the incomplete operation. */
    public static void inspect(IncompleteStatement site, Context ctx, TypeConstant required,
                               ErrorListener errs) {
        var bindings = ctx.getCursorBindings();
        bindings.begin(site);
        if (bindings.isEnabled() && !errs.isAbortDesired()) {
            var scope = CursorScope.capture(ctx);
            bindings.record(site, site.isNameCompletion()
                    ? scope.withTypes(CursorScope.types(site, ctx, errs)) : scope);
        }
        site.getReceiver().ifPresent(receiver -> {
            Expression validated = receiver.validate(ctx, null, errs);
            if (validated != null) {
                AstNode owner = site.isCall() ? site.getTarget() : site;
                owner.replaceChild(receiver, validated);
            }
        });
        CursorBinding call = bindings.isEnabled() && site.isCall() && !errs.isAbortDesired()
                ? PartialCallResolver.inspect(site, ctx, required, errs) : null;
        if (call != null) {
            bindings.record(site, call);
        }
        site.getArguments().stream().takeWhile(argument -> !errs.isAbortDesired()).forEach(argument -> {
            // No single expected type is selected. Candidate fitting inspects trial copies above.
            Expression value = argument instanceof LabeledExpression labeled
                    ? labeled.getUnderlyingExpression() : argument;
            if (!(value instanceof NonBindingExpression || value instanceof LambdaExpression)) {
                Expression validated = argument.validate(ctx, null, errs);
                if (validated != null) {
                    site.replaceChild(argument, validated);
                }
            }
        });
        if (call != null && !errs.isAbortDesired()) {
            PartialSyntax.argument(site).ifPresent(argument -> bindings.record(argument.cursor(), call));
        }
    }

    /** Resolve an unfinished header in its explicit lexical scope, without registering formals. */
    public static Optional<CursorBinding> declarationBinding(IncompleteStatement site, AstNode scope,
            List<Parameter> writtenFormals, ErrorListener errs) {
        return scope.getComponent() instanceof ClassStructure owner
                ? Optional.of(new CursorBinding(List.of(), owner.getFormalType(), false)
                        .withTypes(CursorScope.declarationTypes(site, scope, writtenFormals, errs))
                        .withFormals(CursorScope.declarationFormals(site, scope, writtenFormals, errs)))
                : Optional.empty();
    }
}
