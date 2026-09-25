package org.xvm.compiler.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MultiMethodConstant;
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
    static CursorBinding inspect(IncompleteStatement site, Context ctx, ErrorListener errs) {
        var scope = ctx.cursorBinding().withCandidates(List.of());
        if (errs.isAbortDesired()) {
            return scope;
        }
        var probe = ErrorListener.cancellable(silent(PROBE), errs::isAbortDesired);
        Target target = switch (site.getTarget()) {
            case NameExpression callee -> target(callee, ctx, probe);
            case NewExpression creation -> constructor(creation, ctx, probe);
            default -> null;
        };
        if (target == null || errs.isAbortDesired()) {
            return site.getTarget() instanceof NewExpression
                    ? scope : functionScope(site, ctx, scope, probe);
        }
        String methodName = site.getTarget() instanceof NameExpression callee ? callee.getName() : "construct";
        var lookup = ErrorListener.cancellable(ErrorListener.collecting(errs::log), errs::isAbortDesired);
        var info = target.type().ensureTypeInfo(ctx.getThisClassId(), lookup);
        if (lookup.hasSeriousErrors() || lookup.isAbortDesired()) {
            return scope;
        }
        if (target.kind() == MethodKind.Constructor && !info.isNewable(false, probe)) {
            return scope;
        }
        // InvocationExpression gives a property precedence over methods of the same name.
        if (target.kind() != MethodKind.Constructor && info.findProperty(methodName) != null) {
            return functionScope(site, ctx, scope, probe);
        }
        var methods = info.findMethods(methodName, -1, target.kind()).stream()
                .filter(method -> method.isTopLevel() && (target.kind() == MethodKind.Constructor
                        || !info.getMethodById(method).isCtorOrValidator()))
                .filter(method -> info.getType().getAccess() == Access.PRIVATE
                        || info.getMethodById(method).isVisible(ctx.getThisClassId()))
                .toList();
        var candidates = methods.stream()
                .takeWhile(method -> !errs.isAbortDesired())
                .flatMap(method -> site.probeCallCandidate(ctx, target.type(), info, method,
                        site.getArguments(), probe).stream())
                .filter(candidate -> site.getPendingArgumentName().map(name -> {
                    var method = (MethodStructure) candidate.method().getComponent();
                    var parameter = method.getParam(name.getValueText());
                    if (parameter == null || parameter.isTypeParameter()) {
                        return false;
                    }
                    int index = parameter.getIndex() - method.getTypeParamCount();
                    return candidate.arguments().stream().noneMatch(argument -> argument.parameterIndex() == index);
                }).orElse(true))
                .toList();
        var result = scope.withCandidates(candidates);
        return candidates.isEmpty() ? result : result.withArgumentValues(argumentValues(site, scope, probe,
                arguments -> methods.stream().takeWhile(method -> !errs.isAbortDesired())
                        .anyMatch(method -> !site.probeCallCandidate(ctx, target.type(), info,
                                method, arguments, probe).isEmpty())));
    }

    private static CursorBinding functionScope(IncompleteStatement site, Context ctx,
                                              CursorBinding scope, ErrorListener errs) {
        var functions = function(site, ctx, site.getArguments(), errs);
        var result = scope.withFunctions(functions);
        return functions.isEmpty() ? result : result.withArgumentValues(argumentValues(site, scope, errs,
                arguments -> !function(site, ctx, arguments, errs).isEmpty()));
    }

    /**
     * Probe proposed source names in the ordinary argument fitter. Trials have lexical parentage
     * for resolution, but are never installed in the source tree or selected as complete calls.
     * This preserves generic inference and conversions without copying type rules into the host.
     */
    private static List<CursorBinding.Variable> argumentValues(IncompleteStatement site,
            CursorBinding scope, ErrorListener errs, Predicate<List<Expression>> fits) {
        var written = site.getArguments();
        if (site.getPendingArgumentName().isEmpty()
                && (written.size() != site.getSeparators().size()
                    || written.stream().anyMatch(LabeledExpression.class::isInstance))) {
            return List.of();
        }
        String prefix = site.getArgumentPrefix().map(Token::getValueText).orElse("");
        return scope.variables().stream().filter(CursorBinding.Variable::readable)
                .filter(variable -> variable.name().startsWith(prefix))
                .takeWhile(variable -> !errs.isAbortDesired())
                .filter(variable -> {
                    long cursor = site.getEndPosition();
                    Expression value = new NameExpression(new Token(cursor, cursor, Id.IDENTIFIER, variable.name()));
                    Expression argument = site.getPendingArgumentName()
                            .<Expression>map(name -> new LabeledExpression(name, value)).orElse(value);
                    argument.setParent(site);
                    argument.introduceParentage();
                    var arguments = new ArrayList<>(written);
                    arguments.add(argument);
                    return fits.test(arguments);
                }).toList();
    }

    /** Ordinary named construction only; virtual/inner/annotated construction needs its own proof. */
    private static Target constructor(NewExpression creation, Context ctx, ErrorListener errs) {
        if (creation.left != null || creation.type == null || creation.hasSquareBrackets()) {
            return null;
        }
        var validation = ErrorListener.cancellable(ErrorListener.collecting(silent(PROBE)::log), errs::isAbortDesired);
        var trial = ctx.enter();
        var expression = (TypeExpression) creation.type.clone();
        if (expression.validate(trial, creation.pool().typeType(), validation) == null
                || validation.hasSeriousErrors() || validation.isAbortDesired()) {
            return null;
        }
        var type = expression.ensureTypeConstant(trial, validation);
        return type.containsUnresolved() || type.isFormalType() || type.isAnnotated()
                || type.isVirtualChild() || type.isInnerChildClass()
                ? null : new Target(type, MethodKind.Constructor);
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
