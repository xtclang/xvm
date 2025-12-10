import extensions.ExtensionRegistry;

import models.MethodModel;

import xunit.SkipResult;

import xunit.annotations.AfterEach.AfterEachFunction;
import xunit.annotations.BeforeEach.BeforeEachFunction;

import xunit.extensions.AfterEachCallback;
import xunit.extensions.AfterTestInvocationCallback;
import xunit.extensions.AroundTestCallback;
import xunit.extensions.BeforeEachCallback;
import xunit.extensions.BeforeTestInvocationCallback;
import xunit.extensions.Extension;
import xunit.extensions.ExtensionProvider;
import xunit.extensions.ResourceRegistrationCallback;
import xunit.extensions.TestExecutionPredicate;

/**
 * An `ExecutionLifecycle` implementation for a `MethodModel`.
 *
 * @param model the `MethodModel` this lifecycle represents
 */
const MethodExecutionLifecycle<ModelType extends MethodModel>(ModelType model)
        extends BaseExecutionLifecycle<ModelType>(model) {

    // ----- ExecutionLifecycle methods ------------------------------------------------------------

    @Override
	EngineExecutionContext prepare(EngineExecutionContext context, ExtensionRegistry extensions) {
	    for (ExtensionProvider ep : model.extensionProviders) {
	        for (Extension extension : ep.getExtensions(context)) {
    	        extensions.add(extension, ep);
            }
        }

	    EngineExecutionContext.Builder builder = context.asBuilder(model)
                .withTestClass(model.testClass)
                .withTestMethod(model.testMethod);

        if (context.testFixture == Null) {
            Object fixture = ensureFixture(context, extensions, model.testClass);
            builder.withTestFixture(fixture);
        }
	    return super(builder.build(), extensions);
    }

    @Override
	SkipResult shouldBeSkipped(EngineExecutionContext context, ExtensionRegistry extensions) {
	    if (model.skipResult.skipped) {
	        return model.skipResult;
	    }
	    for (TestExecutionPredicate predicate : context.registry.getAll(TestExecutionPredicate)) {
	        if (String reason := predicate.shouldSkip(context)) {
	            return new SkipResult(True, reason);
	        }
	    }
		return SkipResult.NotSkipped;
	}

    @Override
	EngineExecutionContext before(ExceptionCollector     collector,
                                  EngineExecutionContext context,
                                  ExtensionRegistry      extensions) {
        return context;
	}

    @Override
	EngineExecutionContext execute(ExceptionCollector     collector,
                                   EngineExecutionContext context,
                                   ExtensionRegistry      extensions) {

	    assert context.testFixture != Null;

        // Execute any ResourceRegistrationCallback extensions before any other "before" extensions
	    ResourceRegistrationCallback[] registrars = extensions.get(ResourceRegistrationCallback, True);
        for (ResourceRegistrationCallback registrar : registrars) {
            if (!collector.executeVoid(() -> registrar.registerResources(context.registry))) {
                break;
            }
        }

        // Execute any AroundTestCallback extensions before any other "before" extensions
	    AroundTestCallback[] aroundTest = extensions.get(AroundTestCallback, False);
        for (AroundTestCallback around : aroundTest) {
            if (!collector.executeVoid(() -> around.beforeTest(context))) {
                break;
            }
        }

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
        AfterEachCallback[] afterEach = extensions.get(AfterEachCallback, parentFirst=False);
        // run after each methods first
        for (AfterEachCallback after : afterEach) {
            if (after.is(AfterEachFunction)) {
                collector.executeVoid(() -> after.afterEach(context));
            }
        }

        // run after each extensions second
        for (AfterEachCallback after : afterEach) {
            if (!after.is(AfterEachFunction)) {
                collector.executeVoid(() -> after.afterEach(context));
            }
        }

        // Execute any AroundTestCallback extensions after any other "after" extensions
        for (AroundTestCallback around : aroundTest) {
            collector.executeVoid(() -> around.afterTest(context));
        }
		return context;
	}
}