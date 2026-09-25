package org.xvm.compiler.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo.MethodKind;
import org.xvm.asm.constants.TypedefConstant;

import org.xvm.compiler.CursorBinding;
import org.xvm.compiler.InvocationBinding;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.StatementBlock.TargetInfo;

import static org.xvm.asm.ErrorListener.Silence.PROBE;
import static org.xvm.asm.ErrorListener.silent;

/** Explicit incomplete-call inspection on the compiler worker, while the lexical context exists. */
final class PartialCallResolver {
    static CursorBinding inspect(IncompleteStatement site, Context ctx, TypeConstant required, ErrorListener errs) {
        if (site.getTarget() instanceof NewExpression creation) {
            return PartialConstructionResolver.inspect(site, creation, ctx, required, errs);
        }
        var scope = ctx.cursorBinding().withCandidates(List.of());
        if (errs.isAbortDesired()) {
            return scope;
        }
        var probe = ErrorListener.cancellable(silent(PROBE), errs::isAbortDesired);
        Target target = switch (site.getTarget()) {
            case NameExpression callee -> target(callee, ctx, probe);
            default -> null;
        };
        if (target == null || errs.isAbortDesired()) {
            return functionScope(site, ctx, scope, errs);
        }
        String methodName = ((NameExpression) site.getTarget()).getName();
        var lookup = ErrorListener.cancellable(ErrorListener.collecting(errs::log), errs::isAbortDesired);
        var info = target.type().ensureTypeInfo(ctx.getThisClassId(), lookup);
        if (lookup.hasSeriousErrors() || lookup.isAbortDesired()) {
            return scope;
        }
        // InvocationExpression gives a property precedence over methods of the same name.
        if (info.findProperty(methodName) != null) {
            return functionScope(site, ctx, scope, errs);
        }
        var methods = info.findMethods(methodName, -1, target.kind()).stream()
                .filter(method -> method.isTopLevel() && !info.getMethodById(method).isCtorOrValidator())
                .filter(method -> info.getType().getAccess() == Access.PRIVATE
                        || info.getMethodById(method).isVisible(ctx.getThisClassId()))
                .toList();
        var candidates = methods.stream()
                .takeWhile(method -> !errs.isAbortDesired())
                .flatMap(method -> site.probeCallCandidate(ctx, target.type(), info, method,
                        site.getArguments(), probe).stream())
                .filter(candidate -> acceptsLabel(site, candidate))
                .toList();
        var result = scope.withCandidates(candidates);
        return candidates.isEmpty() ? result : argumentValues(site, ctx, result, errs,
                arguments -> methods.stream().takeWhile(method -> !errs.isAbortDesired())
                        .anyMatch(method -> !site.probeCallCandidate(ctx, target.type(), info,
                                method, arguments, probe).isEmpty()));
    }

    private static CursorBinding functionScope(IncompleteStatement site, Context ctx,
                                              CursorBinding scope, ErrorListener errs) {
        var functions = function(site, ctx, site.getArguments(), errs);
        var result = scope.withFunctions(functions);
        return functions.isEmpty() ? result : argumentValues(site, ctx, result, errs,
                arguments -> !function(site, ctx, arguments, errs).isEmpty());
    }

    /**
     * Probe proposed source names in the ordinary argument fitter. Trials have lexical parentage
     * for resolution, but are never installed in the source tree or selected as complete calls.
     * This preserves generic inference and conversions without copying type rules into the host.
     */
    static CursorBinding argumentValues(IncompleteStatement site, Context ctx,
            CursorBinding scope, ErrorListener errs, Predicate<List<Expression>> fits) {
        var written = site.getArguments();
        if (site.getPendingArgumentName().isEmpty()
                && (written.size() != site.getSeparators().size()
                    || written.stream().anyMatch(LabeledExpression.class::isInstance))) {
            return scope;
        }
        String prefix = site.getArgumentPrefix().map(Token::getValueText).orElse("");
        Predicate<String> fitsName = name -> {
            Expression value = proposedName(site, name);
            Expression argument = site.getPendingArgumentName()
                    .<Expression>map(label -> new LabeledExpression(label, value)).orElse(value);
            argument.setParent(site);
            argument.introduceParentage();
            var arguments = new ArrayList<>(written);
            arguments.add(argument);
            return fits.test(arguments);
        };
        var variables = scope.variables().stream().filter(CursorBinding.Variable::readable)
                .filter(variable -> variable.name().startsWith(prefix))
                .takeWhile(variable -> !errs.isAbortDesired())
                .filter(variable -> fitsName.test(variable.name())).toList();
        var result = scope.withArgumentValues(variables);
        if (errs.isAbortDesired()) {
            return result;
        }
        var lookup = ErrorListener.cancellable(ErrorListener.collecting(errs::log), errs::isAbortDesired);
        var info = scope.thisType().ensureTypeInfo(ctx.getThisClassId(), lookup);
        if (lookup.hasSeriousErrors() || lookup.isAbortDesired()) {
            return result;
        }
        var properties = info.ensurePropertiesByName().values().stream()
                .filter(property -> property.getName().startsWith(prefix))
                .filter(property -> scope.instance() || property.isConstant())
                .filter(property -> scope.variables().stream().noneMatch(variable -> variable.name().equals(property.getName())))
                .filter(property -> info.getType().getAccess() == Access.PRIVATE || property.isVisible(ctx.getThisClassId()))
                .map(property -> property.getName()).sorted()
                .takeWhile(name -> !errs.isAbortDesired())
                .map(name -> propertyValue(site, ctx, name, errs))
                .filter(Objects::nonNull)
                .filter(property -> fitsName.test(property.name()))
                .toList();
        return result.withArgumentProperties(properties);
    }

    /** Normal read validation supplies identity, narrowing and receiver-specific type substitution. */
    private static CursorBinding.Property propertyValue(IncompleteStatement site, Context ctx,
                                                        String name, ErrorListener errs) {
        var validation = ErrorListener.cancellable(ErrorListener.collecting(silent(PROBE)::log), errs::isAbortDesired);
        var expression = proposedName(site, name);
        var value = expression.validate(ctx.enter(), null, validation);
        return value != null && value.getTypeFit().isFit()
                && !validation.hasSeriousErrors() && !validation.isAbortDesired()
                && expression.getResolvedTarget() instanceof PropertyConstant identity
                ? new CursorBinding.Property(name, identity, value.getType()) : null;
    }

    private static NameExpression proposedName(IncompleteStatement site, String name) {
        long cursor = site.getEndPosition();
        var expression = new NameExpression(new Token(cursor, cursor, Id.IDENTIFIER, name));
        expression.setParent(site);
        return expression;
    }

    static boolean acceptsLabel(IncompleteStatement site, CursorBinding.Candidate candidate) {
        return site.getPendingArgumentName().map(name -> {
            var method = (MethodStructure) candidate.method().getComponent();
            var parameter = method.getParam(name.getValueText());
            if (parameter == null || parameter.isTypeParameter()) {
                return false;
            }
            int index = parameter.getIndex() - method.getTypeParamCount();
            return candidate.arguments().stream().noneMatch(argument -> argument.parameterIndex() == index);
        }).orElse(true);
    }

    /** Validate trial copies of the callee and arguments without fabricating a complete call. */
    private static List<CursorBinding.FunctionCandidate> function(
            IncompleteStatement site, Context ctx, List<Expression> written, ErrorListener errs) {
        if (errs.isAbortDesired() || site.getPendingArgumentName().isPresent()
                || written.stream().anyMatch(argument -> argument instanceof LabeledExpression
                        || argument instanceof NonBindingExpression)) {
            return List.of();
        }
        var validation = ErrorListener.cancellable(ErrorListener.collecting(silent(PROBE)::log), errs::isAbortDesired);
        var trial = ctx.enter();
        var callee = ((Expression) site.getTarget().clone()).validate(trial, null, validation);
        if (callee == null || !callee.getTypeFit().isFit() || validation.hasSeriousErrors() || validation.isAbortDesired()) {
            return List.of();
        }
        var type = callee.getType().resolveTypedefs();
        if (!type.isFunction()) {
            return List.of();
        }
        var parameters = site.pool().extractFunctionParams(type);
        if (parameters == null || parameters.length < written.size()) {
            return List.of();
        }
        var arguments = new ArrayList<>(written.stream()
                .map(argument -> (Expression) argument.clone()).toList());
        if (site.validateExpressions(trial, arguments, parameters, validation) == null
                || validation.hasSeriousErrors() || validation.isAbortDesired()
                || arguments.stream().anyMatch(argument -> !argument.isSingle() || !argument.getTypeFit().isFit())) {
            return List.of();
        }
        var mapping = IntStream.range(0, written.size()).mapToObj(index -> {
            var argument = written.get(index);
            return new InvocationBinding.Argument(argument.getStartPosition(), argument.getEndPosition(), index);
        }).toList();
        return List.of(new CursorBinding.FunctionCandidate(type, mapping));
    }

    private static Target target(NameExpression callee, Context ctx, ErrorListener errs) {
        Expression receiver = callee.getLeftExpression();
        if (receiver == null) {
            var resolved = ctx.resolveName(callee.getNameToken(), errs);
            return switch (resolved) {
                case TargetInfo target when target.getId() instanceof MultiMethodConstant ->
                        new Target(target.getTargetType(), target.hasThis() ? MethodKind.Any : MethodKind.Function);
                case MultiMethodConstant methods -> new Target(methods.getParentConstant().getType(), MethodKind.Function);
                case null, default -> null;
            };
        }
        if (!receiver.isValidated() || !receiver.getTypeFit().isFit()) {
            return null;
        }
        if (receiver instanceof NameExpression name) {
            switch (name.getResolvedTarget()) {
            case ClassConstant identity:
                return new Target(identity.getType(), MethodKind.Function);
            case TypedefConstant alias:
                return new Target(alias.getReferredToType(), MethodKind.Function);
            case SingletonConstant singleton:
                return new Target(singleton.getClassConstant().getType(), MethodKind.Any);
            case IdentityConstant identity when identity.getComponent() instanceof ClassStructure structure:
                return new Target(identity.getType(), structure.isSingleton() ? MethodKind.Any : MethodKind.Function);
            case null, default:
                break;
            }
        }
        return new Target(receiver.getType(), MethodKind.Method);
    }

    private record Target(TypeConstant type, MethodKind kind) {}
}
