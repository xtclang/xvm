import extensions.ExtensionRegistry;

import models.ContainerModel;

import xunit.Extension;
import xunit.ExtensionProvider;
import xunit.SkipResult;
import xunit.TestExecutionPredicate;

import xunit.annotations.TestFixture;

import xunit.extensions.AfterAllCallback;
import xunit.extensions.AfterAllFunction;
import xunit.extensions.BeforeAllCallback;
import xunit.extensions.BeforeAllFunction;


/**
 * An `ExecutionLifecycle` implementation for a `ContainerModel`.
 */
const ContainerExecutionLifecycle
        extends BaseExecutionLifecycle<ContainerModel> {

    /**
     * Create a `ContainerModelExecutionLifecycle`.
     *
     * @param model the `ContainerModel` this lifecycle represents
     */
    construct (ContainerModel model) {
        construct BaseExecutionLifecycle(model);
    }

    // ----- ExecutionLifecycle methods ------------------------------------------------------------

    @Override
	EngineExecutionContext prepare(EngineExecutionContext context, ExtensionRegistry extensions,
            ExtensionRegistry? parentExtensions) {

	    for (ExtensionProvider ep : model.extensionProviders) {
	        for (Extension extension : ep.getExtensions(context)) {
    	        extensions.add(extension, ep);
            }
        }

	    return context.asBuilder(this.model)
                .withTestClass(model.testClass)
                .withTestMethod(Null)
                .build();
	    }

    @Override
	SkipResult shouldBeSkipped(EngineExecutionContext context, ExtensionRegistry extensions)
	    {
	    for (TestExecutionPredicate predicate : context.registry.getResources(TestExecutionPredicate))
	        {
	        if (String reason := predicate.shouldSkip(context))
	            {
	            return new SkipResult(True, reason);
	            }
	        }
		return SkipResult.NotSkipped;
	    }

    @Override
	EngineExecutionContext before(ExceptionCollector collector, EngineExecutionContext context, ExtensionRegistry extensions) {
	    Class testClass = model.testClass;
	    if (testClass.is(TestFixture)) {
	        if (testClass.lifecycle == Singleton) {
	            Object? fixture = ensureFixture(context, extensions, testClass);
	            context = context.asBuilder(model).withTestFixture(fixture).build();
            }
        }

        for (BeforeAllCallback before : extensions.get(BeforeAllCallback)) {
            if (!collector.executeVoid(() -> before.beforeAll(context))) {
                break;
            }
        }
		return context;
    }

    @Override
	void after(ExceptionCollector collector, EngineExecutionContext context, ExtensionRegistry extensions) {
        for (AfterAllCallback after : extensions.get(AfterAllCallback)) {
            if (!collector.executeVoid(() -> after.afterAll(context))) {
            }
        }
    }
}