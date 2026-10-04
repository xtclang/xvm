/**
 * Negative compiler regression for issue #667: util.Taken is declared inline in App/util.x
 * and again in the companion file App/util/Taken.x.
 *
 * Automated from the repository root (also included in the manual suites and check):
 *
 *     ./gradlew :manualTests:runDuplicateTypes \
 *         -PincludeBuildManualTests=true -PincludeBuildAttachManualTests=true
 *
 * From this directory, using the XDK to test:
 *
 *     xcc -o . App.x
 *
 * Expected: compilation fails with COMPILER-148 (duplicate class or package name "Taken") at
 * the companion declaration, without a ClassCastException or an internal compiler error. Before the fix,
 * NamedTypeExpression.calculateDefaultType() casts a CompositeComponent to ClassStructure.
 *
 * Positive controls: remove either class declaration and compile again; compilation succeeds.
 * This deliberately invalid cluster lives under archive so normal manualTests builds exclude it.
 */
module App {
    util.Taken make() = new util.Taken();
}
