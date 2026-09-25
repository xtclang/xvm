package org.xvm.compiler.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo.MethodKind;

import org.xvm.compiler.CursorBinding;

import static org.xvm.asm.ErrorListener.Silence.PROBE;
import static org.xvm.asm.ErrorListener.silent;

/** Constructor cursor queries reuse normal type preparation and argument fitting on trial syntax. */
final class PartialConstructionResolver {
    static CursorBinding inspect(IncompleteStatement site, NewExpression creation, Context ctx,
                                 TypeConstant required, ErrorListener errs) {
        var scope = ctx.cursorBinding().withCandidates(List.of());
        // Anonymous class preparation creates compiler components and needs its own ownership proof.
        if (creation.body != null || errs.isAbortDesired()) {
            return scope;
        }
        var validation = ErrorListener.cancellable(ErrorListener.collecting(silent(PROBE)::log), errs::isAbortDesired);
        var trial = (NewExpression) creation.clone();
        var construction = trial.prepareConstruction(ctx.enter(), required, validation, true);
        if (construction == null || validation.hasSeriousErrors() || validation.isAbortDesired()) {
            return scope;
        }
        var lookup = ErrorListener.cancellable(ErrorListener.collecting(errs::log), errs::isAbortDesired);
        var info = trial.getTypeInfo(ctx, construction.target(), lookup);
        if (lookup.hasSeriousErrors() || lookup.isAbortDesired()
                || construction.requiresNewable() && !info.isNewable(false, validation)) {
            return scope;
        }
        var methods = info.findMethods("construct", -1, MethodKind.Constructor).stream()
                .filter(method -> info.getType().getAccess() == Access.PRIVATE
                        || info.getMethodById(method).isVisible(ctx.getThisClassId()))
                .toList();
        var written = arguments(site, site.getArguments());
        var candidates = methods.stream()
                .takeWhile(method -> !errs.isAbortDesired())
                .flatMap(method -> site.probeCallCandidate(ctx, construction.target(), info, method, written, validation).stream()
                        .map(candidate -> infer(trial, ctx, construction.result(), candidate, written, errs)))
                .filter(Objects::nonNull)
                .filter(candidate -> site.getLeadingArguments().isEmpty()
                        || candidate.signature().getParamCount() > site.getLeadingArguments().size())
                .filter(candidate -> PartialCallResolver.acceptsLabel(site, candidate))
                .toList();
        var result = scope.withCandidates(candidates);
        return candidates.isEmpty() ? result : PartialCallResolver.argumentValues(site, ctx, result, errs,
                values -> methods.stream().takeWhile(method -> !errs.isAbortDesired())
                        .anyMatch(method -> !site.probeCallCandidate(ctx, construction.target(), info, method,
                                arguments(site, values), validation).isEmpty()));
    }

    private static List<Expression> arguments(IncompleteStatement site, List<Expression> values) {
        return Stream.concat(site.getLeadingArguments().stream(), values.stream()).toList();
    }

    /** Apply the ordinary constructor's post-argument class inference without selecting an overload. */
    private static CursorBinding.Candidate infer(NewExpression creation, Context ctx, TypeConstant result,
            CursorBinding.Candidate candidate, List<Expression> written, ErrorListener errs) {
        var method = (MethodStructure) candidate.method().getComponent();
        var owner = (ClassStructure) method.getParent().getParent();
        if (result.isParamsSpecified() || !owner.isParameterized()) {
            return candidate;
        }
        var validation = ErrorListener.cancellable(ErrorListener.collecting(silent(PROBE)::log), errs::isAbortDesired);
        var trial = ctx.enter();
        List<Expression> ordered = new ArrayList<>(written.stream().map(value -> (Expression) value.clone()).toList());
        if (creation.containsNamedArgs(ordered)) {
            ordered = creation.rearrangeNamedArgs(method, ordered, validation);
            if (ordered == null) {
                return null;
            }
        }
        for (int index = 0; index < ordered.size() && !validation.isAbortDesired(); ++index) {
            var value = ordered.get(index);
            if (!(value instanceof NonBindingExpression)) {
                var validated = value.validate(trial, candidate.signature().getRawParams()[index], validation);
                if (validated == null || !validated.getTypeFit().isFit()) {
                    return null;
                }
                ordered.set(index, validated);
            }
        }
        if (validation.hasSeriousErrors() || validation.isAbortDesired()) {
            return null;
        }
        var inferred = creation.inferTypeFromConstructor(trial, owner, method, ordered);
        return inferred == null ? candidate : new CursorBinding.Candidate(candidate.method(),
                method.getIdentityConstant().getSignature().resolveGenericTypes(creation.pool(), inferred),
                candidate.arguments(), candidate.converting());
    }
}
