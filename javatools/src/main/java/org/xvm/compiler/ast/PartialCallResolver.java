package org.xvm.compiler.ast;

import java.util.List;

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
import org.xvm.compiler.ast.StatementBlock.TargetInfo;

import static org.xvm.asm.ErrorListener.Silence.PROBE;
import static org.xvm.asm.ErrorListener.silent;

/** Explicit incomplete-call inspection on the compiler worker, while the lexical context exists. */
final class PartialCallResolver {
    static List<CursorBinding.Candidate> inspect(IncompleteStatement site, Context ctx, ErrorListener errs) {
        if (!(site.getTarget() instanceof NameExpression callee) || errs.isAbortDesired()) {
            return List.of();
        }
        var probe = ErrorListener.cancellable(silent(PROBE), errs::isAbortDesired);
        Target target = target(callee, ctx, probe);
        if (target == null || errs.isAbortDesired()) {
            return List.of();
        }
        var lookup = ErrorListener.cancellable(ErrorListener.collecting(errs::log), errs::isAbortDesired);
        var info = target.type().ensureTypeInfo(ctx.getThisClassId(), lookup);
        if (lookup.hasSeriousErrors() || lookup.isAbortDesired()) {
            return List.of();
        }
        return info.findMethods(callee.getName(), -1, target.kind()).stream()
                .filter(method -> method.isTopLevel() && !info.getMethodById(method).isCtorOrValidator())
                .filter(method -> info.getType().getAccess() == Access.PRIVATE
                        || info.getMethodById(method).isVisible(ctx.getThisClassId()))
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
