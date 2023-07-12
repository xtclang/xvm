import extensions.ExtensionRegistry;

import models.MethodModel;

import xunit.SkipResult;
import xunit.TestExecutionPredicate;

import xunit.extensions.AfterEachCallback;
import xunit.extensions.AfterEachFunction;
import xunit.extensions.AfterTestInvocationCallback;
import xunit.extensions.BeforeEachCallback;
import xunit.extensions.BeforeEachFunction;
import xunit.extensions.BeforeTestInvocationCallback;

/**
 * An `ExecutionLifecycle` implementation for a `MethodModel`.
 */
const MethodExecutionLifecycle
        extends BaseExecutionLifecycle<MethodModel> {

    /**
     * Create a `MethodModelExecutionLifecycle`.
     *
     * @param model the `MethodModel` this lifecycle represents
     */
    construct (MethodModel model) {
        construct BaseExecutionLifecycle(model);
    }

    // ----- ExecutionLifecycle methods ------------------------------------------------------------

    @Override
	EngineExecutionContext prepare(EngineExecutionContext context, ExtensionRegistry extensions,
            ExtensionRegistry? parentExtensions) {

        extensions.parent = parentExtensions;

	    EngineExecutionContext.Builder builder = context.asBuilder(model)
                .withTestClass(model.testClass)
                .withTestMethod(model.testMethod);

        if (context.testFixture == Null) {
            builder.withTestFixture(ensureFixture(context, extensions, model.testClass));
        }

        prepare(builder, extensions);
	    return super(builder.build(), extensions, parentExtensions);
    }

    @Override
	SkipResult shouldBeSkipped(EngineExecutionContext context, ExtensionRegistry extensions) {
	    if (model.skipResult.skipped) {
	        return model.skipResult;
	    }
	    for (TestExecutionPredicate predicate : context.registry.getResources(TestExecutionPredicate)) {
	        if (String reason := predicate.shouldSkip(context)) {
	            return new SkipResult(True, reason);
	        }
	    }
		return SkipResult.NotSkipped;
	}

    @Override
	EngineExecutionContext before(ExceptionCollector collector, EngineExecutionContext context, ExtensionRegistry extensions) {
        return context;
	}

    @Override
	EngineExecutionContext execute(ExceptionCollector collector, EngineExecutionContext context, ExtensionRegistry extensions) {
	    assert context.testFixture != Null;
	    BeforeEachCallback[] beforeEach = extensions.get(BeforeEachCallback);

        // Execute extensions first
        for (BeforeEachCallback before : beforeEach) {
            if (before.is(BeforeEachFunction)) {
                continue;
            }
            if (!collector.executeVoid(() -> before.beforeEach(context))) {
                break;
            }
        }

        // Execute annotated methods second
        for (BeforeEachCallback before : beforeEach) {
            if (before.is(BeforeEachFunction)) {
                if (!collector.executeVoid(() -> before.beforeEach(context))) {
                    break;
                }
            }
        }

        if (collector.empty) {
            // run any before test invocation callbacks
            for (BeforeTestInvocationCallback before : extensions.get(BeforeTestInvocationCallback)) {
                collector.executeVoid(() -> before.beforeTest(context));
            }

            if (collector.empty) {
                // only run the test if there have been no errors
                collector.executeVoid(() -> context.invoke(model.testMethod));
            }
        }

        // Always run all of the afters
        // run any after test in the context regardless of whether there have been errors
        for (AfterTestInvocationCallback after : extensions.get(AfterTestInvocationCallback)) {
            collector.executeVoid(() -> after.afterTest(context));
        }

        // run any after functions in the context regardless of whether there have been errors
        AfterEachCallback[] afterEach = extensions.get(AfterEachCallback);
        // run after each methods first
        for (AfterEachCallback after : afterEach) {
            if (after.is(AfterEachFunction)) {
                collector.executeVoid(() -> after.afterEach(context));
            }
        }

        // run after each extensions second
        for (AfterEachCallback after : afterEach) {
            if (after.is(AfterEachFunction)) {
                continue;
            }
            collector.executeVoid(() -> after.afterEach(context));
        }

		return context;
	}

    /**
     * Can be overridden in sub-classes that want to modify the context builder.
     *
     * @param builder     the `EngineExecutionContext` builder that may be modified
     * @param extensions  the `ExtensionRegistry` to add `Extensions` to
     */
    protected void prepare(EngineExecutionContext.Builder builder, ExtensionRegistry extensions)
        {
        }
}