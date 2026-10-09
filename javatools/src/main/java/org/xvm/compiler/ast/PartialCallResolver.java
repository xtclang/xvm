package org.xvm.compiler.ast;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.MethodKind;
import org.xvm.asm.constants.TypedefConstant;

import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.CursorBinding;
import org.xvm.compiler.InvocationBinding;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.StatementBlock.TargetInfo;
import org.xvm.compiler.ast.partial.IncompleteStatement;
import org.xvm.compiler.ast.partial.PartialSyntax;
import org.xvm.compiler.ast.partial.PartialSyntax.ArgumentCursor;
import org.xvm.compiler.ast.partial.ProposedLiteralToken;

import org.xvm.util.PackedInteger;

import static org.xvm.asm.ErrorListener.Silence.PROBE;
import static org.xvm.asm.ErrorListener.silent;

/** Explicit incomplete-call inspection on the compiler worker, while the lexical context exists. */
final class PartialCallResolver {
    static CursorBinding inspect(IncompleteStatement site, Context ctx, TypeConstant required, ErrorListener errs) {
        if (site.getTarget() instanceof NewExpression creation) {
            return PartialConstructionResolver.inspect(site, creation, ctx, required, errs);
        }
        var scope = CursorScope.capture(ctx).withCandidates(List.of());
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
        var direct = methodScope(site, ctx, scope, target, errs);
        if (!direct.candidates().isEmpty() || !direct.functions().isEmpty() || target.kind() != MethodKind.Method) {
            return direct;
        }
        // Match InvocationExpression's fallback order: a Type<T>'s functions, then
        // receiver-rewritten functions X.f(receiver, args) for ordinary instances.
        var alternate = target.type().isTypeOfType()
                ? new Target(target.type().getParamType(0).resolveConstraints(), MethodKind.Function, false)
                : new Target(target.type(), MethodKind.Function, true);
        return methodScope(site, ctx, scope, alternate, errs);
    }

    private static CursorBinding methodScope(IncompleteStatement site, Context ctx, CursorBinding scope,
                                              Target target, ErrorListener errs) {
        String methodName = ((NameExpression) site.getTarget()).getName();
        var lookup = ErrorListener.cancellable(ErrorListener.collecting(silent(PROBE)::log), errs::isAbortDesired);
        var info = target.type().ensureTypeInfo(ctx.getThisClassId(), lookup);
        if (lookup.hasSeriousErrors() || lookup.isAbortDesired()) {
            return scope;
        }
        // InvocationExpression gives a property precedence over methods of the same name.
        if (!target.receiverArgument() && info.findProperty(methodName) != null) {
            return functionScope(site, ctx, scope, errs);
        }
        var methods = info.findMethods(methodName, -1, target.kind()).stream()
                .filter(method -> method.isTopLevel() && !info.getMethodById(method).isCtorOrValidator())
                .filter(method -> info.getType().getAccess() == Access.PRIVATE
                        || info.getMethodById(method).isVisible(ctx.getThisClassId()))
                .toList();
        var probe = ErrorListener.cancellable(silent(PROBE), errs::isAbortDesired);
        Function<List<Expression>, List<CursorBinding.Candidate>> fit = arguments -> {
            var supplied = target.receiverArgument()
                    ? Stream.concat(Stream.of(site.getReceiver().orElseThrow()), arguments.stream()).toList() : arguments;
            return methods.stream().takeWhile(method -> !errs.isAbortDesired())
                    .flatMap(method -> probeCallCandidate(site, ctx, target.type(), info, method,
                            supplied, probe).stream())
                    .map(candidate -> target.receiverArgument() ? candidate.withReceiverArgument() : candidate)
                    .toList();
        };
        var candidates = fit.apply(writtenArguments(site)).stream()
                .filter(candidate -> acceptsLabel(site, candidate)).toList();
        var result = scope.withCandidates(candidates);
        return candidates.isEmpty() ? result : argumentValues(site, ctx, result, errs,
                arguments -> fit.apply(arguments).stream().anyMatch(candidate ->
                        validateArguments(ctx, candidate, target.receiverArgument()
                                ? Stream.concat(Stream.of(site.getReceiver().orElseThrow()), arguments.stream()).toList()
                                : arguments, errs)));
    }

    /**
     * Test one incomplete-call candidate with the compiler's usual named-argument, conversion and
     * generic inference rules. Cloned arguments and a child context isolate speculative changes.
     * Missing parameters are permitted; no best-overload selection or invocation validation occurs.
     */
    static List<CursorBinding.Candidate> probeCallCandidate(AstNode site, Context ctx, TypeConstant target, TypeInfo info,
            MethodConstant method, List<Expression> arguments, ErrorListener errs) {
        var written = arguments.stream().map(argument -> (Expression) argument.clone()).toList();
        var named = site.collectNamedArgs(written, errs);
        if (named == null || errs.isAbortDesired()) {
            return List.of();
        }
        Set<MethodConstant> direct = new HashSet<>();
        Set<MethodConstant> converting = new HashSet<>();
        List<CursorBinding.Candidate> result = new ArrayList<>();
        site.collectMatchingMethods(ctx.enter(), target, info, Set.of(method), written, false, named,
                null, direct, converting, new HashMap<>(), errs, (signature, ordered) -> {
                    var resolved = signature.resolveGenericTypes(site.pool(), target);
                    var original = info.getMethodById(method).getSignature();
                    // A pending method formal is not a concrete expected type. Retain its written
                    // formal instead of leaking PendingTypeConstant or substituting Object.
                    var params = IntStream.range(0, resolved.getParamCount())
                            .mapToObj(i -> resolved.getRawParams()[i].containsUnresolved()
                                    ? original.getRawParams()[i] : resolved.getRawParams()[i])
                            .toArray(TypeConstant[]::new);
                    var returns = IntStream.range(0, resolved.getReturnCount())
                            .mapToObj(i -> resolved.getRawReturns()[i].containsUnresolved()
                                    ? original.getRawReturns()[i] : resolved.getRawReturns()[i])
                            .toArray(TypeConstant[]::new);
                    var copied = site.pool().ensureSignatureConstant(resolved.getName(), params, returns);
                    var declaration = info.getMethodById(method).getTopmostMethodStructure(info).getIdentityConstant();
                    InvocationBinding.arguments(written, ordered).ifPresent(mapping ->
                            result.add(new CursorBinding.Candidate(declaration, copied, mapping, converting.contains(method))));
                });
        return List.copyOf(result);
    }

    /** testFit can use an operator's optimistic implicit type; validate each actual insertion too. */
    static boolean validateArguments(Context ctx, CursorBinding.Candidate candidate,
                                     List<Expression> arguments, ErrorListener errs) {
        var trial = ctx.enter();
        var probe = ErrorListener.cancellable(ErrorListener.collecting(silent(PROBE)::log), errs::isAbortDesired);
        int formals = ((MethodStructure) candidate.method().getComponent()).getTypeParamCount();
        return arguments.stream().allMatch(written -> {
            var mapping = candidate.arguments().stream().filter(argument ->
                    argument.startPosition() == written.getStartPosition()
                            && argument.endPosition() == written.getEndPosition()).findFirst();
            if (mapping.isEmpty() || probe.isAbortDesired()) {
                return false;
            }
            var expected = candidate.signature().getRawParams()[formals + mapping.orElseThrow().parameterIndex()];
            Expression value = written instanceof LabeledExpression label ? label.getUnderlyingExpression() : written;
            Expression validated = ((Expression) value.clone()).validate(trial, expected, probe);
            return validated != null && validated.getTypeFit().isFit()
                    && !probe.hasSeriousErrors() && !probe.isAbortDesired();
        });
    }

    private static CursorBinding functionScope(IncompleteStatement site, Context ctx,
                                              CursorBinding scope, ErrorListener errs) {
        var functions = function(site, ctx, writtenArguments(site), errs);
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
        var nested = PartialSyntax.argument(site);
        var cursor = nested.map(ArgumentCursor::cursor).orElse(site);
        boolean qualified = !cursor.isCall() && cursor.getReceiver().isPresent();
        if (nested.isEmpty() && site.getPendingArgumentName().isEmpty()
                && (written.size() != site.getSeparators().size()
                    || written.stream().anyMatch(LabeledExpression.class::isInstance))) {
            return scope;
        }
        String prefix = cursor.getCompletionPrefix();
        Predicate<Expression> fitsValue = value -> {
            if (nested.isPresent()) {
                return fits.test(PartialArgument.proposed(site, nested.orElseThrow(), value));
            }
            Expression argument = site.getPendingArgumentName()
                    .<Expression>map(label -> new LabeledExpression(label, value)).orElse(value);
            argument.setParent(site);
            argument.introduceParentage();
            var arguments = new ArrayList<>(written);
            arguments.add(argument);
            return fits.test(arguments);
        };
        Predicate<String> fitsName = name -> fitsValue.test(proposedName(cursor, name));
        var variables = scope.variables().stream().filter(variable -> !qualified && variable.readable())
                .filter(variable -> variable.name().startsWith(prefix))
                .takeWhile(variable -> !errs.isAbortDesired())
                .filter(variable -> fitsName.test(variable.name())).toList();
        var literals = qualified ? List.<String>of()
                : List.of("False", "True", "Null", "0", "0.0", "\"\"", "' '", "#00", "[]", "Map:[]", "Tuple:()").stream()
                .filter(text -> text.startsWith(prefix))
                .takeWhile(text -> !errs.isAbortDesired())
                .filter(text -> {
                    Expression proposal = proposedLiteral(cursor, text, errs);
                    return proposal != null && fitsValue.test(proposal);
                }).toList();
        var expressions = instanceSpellings(cursor).takeWhile(text -> !errs.isAbortDesired())
                .filter(text -> fitsValue.test(proposedInstance(cursor, text))).toList();
        var templates = qualified || !prefix.isEmpty() ? List.<String>of()
                : Stream.concat(scope.candidates().stream().flatMap(candidate -> Arrays.stream(candidate.signature().getRawParams())),
                        scope.functions().stream().flatMap(function -> Arrays.stream(site.pool().extractFunctionParams(function.type()))))
                    .map(TypeConstant::resolveTypedefs).map(TypeConstant::removeNullable)
                    .filter(TypeConstant::isFunction)
                    .map(site.pool()::extractFunctionParams).filter(Objects::nonNull)
                    .map(parameters -> parameters.length).distinct().sorted()
                    .takeWhile(arity -> !errs.isAbortDesired())
                    .filter(arity -> fitsValue.test(proposedLambda(cursor, arity)))
                    .map(arity -> "(" + IntStream.range(0, arity).mapToObj(index -> "arg" + (index + 1))
                            .collect(Collectors.joining(", ")) + ") -> TODO()")
                    .toList();
        var result = scope.withArgumentValues(variables).withArgumentLiterals(literals)
                .withArgumentExpressions(expressions).withArgumentTemplates(templates);
        if (errs.isAbortDesired()) {
            return result;
        }
        var lookup = ErrorListener.cancellable(ErrorListener.collecting(errs::log), errs::isAbortDesired);
        var receiver = qualified ? ((Expression) cursor.getReceiver().orElseThrow().clone())
                .validate(ctx.enter(), null, lookup) : null;
        if (qualified && (receiver == null || !receiver.getTypeFit().isFit())) {
            return result;
        }
        var info = (qualified ? receiver.getType() : scope.thisType()).ensureTypeInfo(ctx.getThisClassId(), lookup);
        if (lookup.hasSeriousErrors() || lookup.isAbortDesired()) {
            return result;
        }
        var propertyNames = info.ensurePropertiesByName().values().stream()
                .filter(property -> property.getName().startsWith(prefix))
                .filter(property -> qualified || scope.instance() || property.isConstant())
                .filter(property -> qualified || scope.variables().stream().noneMatch(variable -> variable.name().equals(property.getName())))
                .filter(property -> info.getType().getAccess() == Access.PRIVATE || property.isVisible(ctx.getThisClassId()))
                .map(property -> property.getName());
        var properties = Stream.concat(propertyNames, qualified ? Stream.empty() : CursorScope.valueNames(cursor).stream())
                .filter(name -> name.startsWith(prefix))
                .filter(name -> qualified || scope.variables().stream().noneMatch(variable -> variable.name().equals(name)))
                .distinct().sorted().takeWhile(name -> !errs.isAbortDesired())
                .map(name -> propertyValue(cursor, ctx, name, errs))
                .filter(Objects::nonNull)
                .filter(property -> fitsName.test(property.name()))
                .toList();
        return result.withArgumentProperties(properties);
    }

    /** A real inferred-parameter lambda on disposable syntax; the whole-call fitter proves its arity. */
    private static Expression proposedLambda(IncompleteStatement site, int arity) {
        long cursor = site.getEndPosition();
        var names = IntStream.range(0, arity).mapToObj(index ->
                new NameExpression(new Token(cursor, cursor, Id.IDENTIFIER, "arg" + (index + 1))))
                .collect(Collectors.toCollection(ArrayList::new));
        var body = new ThrowExpression(new Token(cursor, cursor, Id.TODO), null, null,
                new Token(cursor, cursor, Id.R_PAREN));
        var returns = new ReturnStatement(new Token(cursor, cursor, Id.RETURN), body);
        var lambda = new LambdaExpression(names, new Token(cursor, cursor, Id.LAMBDA),
                new StatementBlock(new ArrayList<>(List.of(returns)), cursor, cursor), cursor);
        lambda.setParent(site);
        lambda.introduceParentage();
        return lambda;
    }

    private static Expression proposedLiteral(IncompleteStatement site, String text, ErrorListener errs) {
        long cursor = site.getEndPosition();
        return switch (text) {
            case "0" -> new LiteralExpression(new ProposedLiteralToken(cursor, Id.LIT_INT, PackedInteger.ZERO, text));
            case "0.0" -> new LiteralExpression(new ProposedLiteralToken(cursor, Id.LIT_DEC, BigDecimal.ZERO, text));
            case "\"\"" -> new LiteralExpression(new ProposedLiteralToken(cursor, Id.LIT_STRING, "", text));
            case "' '" -> new LiteralExpression(new ProposedLiteralToken(cursor, Id.LIT_CHAR, ' ', text));
            case "#00" -> new LiteralExpression(new ProposedLiteralToken(cursor, Id.LIT_BINSTR, new byte[]{0}, text));
            case "[]" -> new ListExpression(null, new ArrayList<>(), cursor, cursor);
            case "Map:[]" -> {
                var map = new MapExpression(new NamedTypeExpression(null,
                        List.of(new Token(cursor, cursor, Id.IDENTIFIER, "Map")), null, null, null, cursor),
                        new ArrayList<>(), new ArrayList<>(), cursor);
                map.setParent(site);
                map.introduceParentage();
                var probe = ErrorListener.cancellable(ErrorListener.collecting(silent(PROBE)::log), errs::isAbortDesired);
                yield new StageMgr(map, Stage.Resolved, probe).fastForward(20)
                        && !probe.hasSeriousErrors() && !probe.isAbortDesired() ? map : null;
            }
            case "Tuple:()" -> new TupleExpression(null, new ArrayList<>(), cursor, cursor);
            default -> proposedName(site, text);
        };
    }

    /** Lexical candidates only; each caller must prove these through ordinary validation. */
    static Stream<String> instanceSpellings(IncompleteStatement site) {
        boolean qualified = !site.isCall() && site.getReceiver().isPresent();
        boolean explicitThis = qualified && site.getReceiver().orElseThrow() instanceof NameExpression receiver
                && receiver.getLeftExpression() == null && receiver.getName().equals("this");
        return qualified && !explicitThis ? Stream.empty()
                : Stream.concat(qualified ? Stream.empty() : Stream.of("this"),
                        CursorScope.enclosingInstances(site).stream().map(name -> qualified ? name : "this." + name))
                    .filter(text -> text.startsWith(site.getCompletionPrefix()));
    }

    static Expression proposedInstance(IncompleteStatement site, String text) {
        if (!text.startsWith("this.")) {
            return proposedName(site, text);
        }
        long cursor = site.getEndPosition();
        var expression = new NameExpression(new NameExpression(new Token(cursor, cursor, Id.IDENTIFIER, "this")),
                null, new Token(cursor, cursor, Id.IDENTIFIER, text.substring("this.".length())), null, cursor);
        expression.setParent(site);
        expression.introduceParentage();
        return expression;
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
        var token = new Token(cursor, cursor, Id.IDENTIFIER, name);
        var expression = !site.isCall() && site.getReceiver().isPresent()
                ? new NameExpression((Expression) site.getReceiver().orElseThrow().clone(), null, token, null, cursor)
                : new NameExpression(token);
        expression.setParent(site);
        expression.introduceParentage();
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
                        || argument instanceof NonBindingExpression && PartialSyntax.argument(site).isEmpty())) {
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
        var arguments = written.stream().map(argument -> (Expression) argument.clone())
                .collect(Collectors.toCollection(ArrayList::new));
        boolean fit = IntStream.range(0, arguments.size()).allMatch(index -> {
            Expression argument = arguments.get(index);
            if (argument instanceof NonBindingExpression && PartialSyntax.argument(site).isPresent()) {
                return true;
            }
            var value = argument.validate(trial, parameters[index], validation);
            return value != null && value.isSingle() && value.getTypeFit().isFit()
                    && !validation.hasSeriousErrors() && !validation.isAbortDesired();
        });
        if (!fit) {
            return List.of();
        }
        var mapping = IntStream.range(0, written.size()).mapToObj(index -> {
            var argument = written.get(index);
            return new InvocationBinding.Argument(argument.getStartPosition(), argument.getEndPosition(), index);
        }).toList();
        return List.of(new CursorBinding.FunctionCandidate(type, mapping));
    }

    /** Preserve later arguments while letting an unknown slot remain unbound during enumeration. */
    static List<Expression> writtenArguments(IncompleteStatement site) {
        return PartialSyntax.argument(site).map(argument -> PartialArgument.unbound(site, argument)).orElseGet(site::getArguments);
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

    private record Target(TypeConstant type, MethodKind kind, boolean receiverArgument) {
        private Target(TypeConstant type, MethodKind kind) {
            this(type, kind, false);
        }
    }
}
