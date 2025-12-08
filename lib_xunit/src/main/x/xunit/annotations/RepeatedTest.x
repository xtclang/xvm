import ecstasy.reflect.Annotation;

import extensions.ExecutionContext;
import extensions.Extension;
import extensions.ParameterResolver;
import extensions.ResourceRegistry;
import extensions.ResourceRegistry.Resource;

import templates.TestTemplate;
import templates.TestTemplateContext;
import templates.TestTemplateFactory;

/**
 * A `RepeatedTest` is a `TestTemplate` annotation that indicates the annotated test method or
 * function should be executed a number of times.
 *
 * Each iteration of the repeated tests behaves in the same way as a regular test execution, with
 * the same lifecycle callbacks and extensions applied.In addition, the current repetition and total
 * number of repetitions can be accessed by having the {@link RepetitionInfo} injected.
 *
 * @param iterations  the number of repetitions; must be greater than zero
 * @param group       this assigns the test to a named group of tests, which allows specific groups
 *                    of tests to be selected for execution (see `Test.group`).
 * @param order       this assigns an ordering to execution of the annotated resource
 *                    (see `Test.order`).
 */
annotation RepeatedTest(Int iterations, String group = Test.Unit, Int priority = 0)
        extends TestTemplate(group, priority)
        into MethodOrFunction {

    /**
     * The name of the `@Injected` key that provides access to the current iteration of a repeated
     * test.
     */
    static String CurrentIteration = "xunit.annotations.RepeatedTest.currentIteration";

    /**
     * The name of the `@Injected` key that provides access to the total iterations of a repeated
     * test.
     */
    static String IterationCount = "xunit.annotations.RepeatedTest.iterationCount";

    @Override
    TestTemplateFactory[] getTemplateFactories()
            = super() + new RepeatedTestTemplateFactory(iterations);

    /**
     * A value that can be used by tests that need to know details of a repeated test execution.
     */
    static const Info(Int count, Int iteration);

    /**
     * A `RepeatedTestTemplateFactory` is a `TestTemplateFactory` that will cause a templated test
     * to executed a specified number of times.
     *
     * @param iterations  the total number of times a repeated test will be executed
     */
    static const RepeatedTestTemplateFactory(Int iterations)
            implements TestTemplateFactory<RepeatedTest> {

        @Override
        Iterable<TestTemplateContext> getContexts(ExecutionContext context)
                = new RepeatedTestTemplateContextIterable(new RepeatedTestTemplateContext(iterations));
    }

    // ---- inner const: RepeatedTestTemplateContext -----------------------------------------------

    /**
     * A `TestTemplateContext` representing a repeated test.
     *
     * @param iterations  the number of times the test template is repeated
     */
    static const RepeatedTestTemplateContext(Int iterations)
            implements TestTemplateContext {

        @Override
        String getDisplayName(Int iteration) = $"repetition {iteration} of {iterations}";

        @Override
        Extension[] getExtensions(Int iteration) = [];

        @Override
        ResourceRegistry.Resource[] getResources(Int iteration) {
            RepeatedTestInfoResolver resolver = new RepeatedTestInfoResolver(iterations, iteration);
            return [ResourceRegistry.resource(resolver, Replace),
                    ResourceRegistry.resource(iteration, CurrentIteration, Replace),
                    ResourceRegistry.resource(iterations, IterationCount, Replace)];
        }
    }

    // ---- inner const: RepeatedTestTemplateContextIterable ---------------------------------------

    /**
     * An `Iterable` representing a number of `TestTemplateContext` instances. The
     * `TestTemplateContext` instances are created lazily as they are iterated over.
     *
     * @param iterations  the number of `TestTemplateContext` instances to iterate over
     */
    static const RepeatedTestTemplateContextIterable(RepeatedTestTemplateContext context)
            implements Iterable<TestTemplateContext> {

        @Override
        Int size.get() = context.iterations;

        @Override
        Iterator<TestTemplateContext> iterator() = new RepeatedTestTemplateIterator(context);
    }

    // ---- inner class: RepeatedTestTemplateIterator ----------------------------------------------

    /**
     * An `Iterator` that iterates over a number of `TestTemplateContext` instances.
     *
     * @param iterations  the number of `TestTemplateContext` instances to iterate over
     */
    static class RepeatedTestTemplateIterator(RepeatedTestTemplateContext context)
            implements Iterator<TestTemplateContext> {
        /**
         * The index of the next `TestTemplateContext` that will be returned.
         */
        private Int iteration = 0;

        @Override
        conditional TestTemplateContext next() {
            if (iteration < context.iterations) {
                iteration++;
                return True, context;
            }
            return False;
        }
    }

    // ---- inner const: RepeatedTestInfoResolver --------------------------------------------------

    /**
     * A `ParameterResolver` that resolves `Parameter`s of type `RepeatedTestInfo`.
     *
     * @param iterations  the total number of times a repeated test will be executed
     * @param iteration   the current iteration of the repeated test (the first iteration is zero)
     */
    static const RepeatedTestInfoResolver(Int iterations, Int iteration)
            implements ParameterResolver {

        @Override
        <ParamType> conditional ParamType resolve(ExecutionContext     context,
                                                  Parameter<ParamType> param) {
            if (ParamType == RepeatedTest.Info) {
                return True, new RepeatedTest.Info(iterations, iteration);
            }
            return False;
        }
    }
}