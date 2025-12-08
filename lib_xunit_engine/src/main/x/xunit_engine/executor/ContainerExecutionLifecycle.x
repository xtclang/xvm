import extensions.ExtensionRegistry;

import models.ContainerModel;

import xunit.SkipResult;

import xunit.annotations.TestFixture;

import xunit.extensions.AfterAllCallback;
import xunit.extensions.BeforeAllCallback;
import xunit.extensions.Extension;
import xunit.extensions.ExtensionProvider;
import xunit.extensions.TestExecutionPredicate;


/**
 * An `ExecutionLifecycle` implementation for a `ContainerModel`.
 *
 * @param model the `ContainerModel` this lifecycle represents
 */
const ContainerExecutionLifecycle<ModelType extends ContainerModel>(ModelType model)
        extends BaseExecutionLifecycle<ModelType>(model) {

    // ----- ExecutionLifecycle methods ------------------------------------------------------------

    @Override
	EngineExecutionContext prepare(EngineExecutionContext context, ExtensionRegistry extensions) {
	    for (ExtensionProvider ep : model.extensionProviders) {
	        for (Extension extension : ep.getExtensions(context)) {
    	        extensions.add(extension, ep);
            }
        }
	    return super(context.asBuilder(this.model)
                .withTestClass(model.testClass)
                .withTestMethod(Null)
                .build(), extensions);
	    }

    @Override
	SkipResult shouldBeSkipped(EngineExecutionContext context, ExtensionRegistry extensions)
	    {
	    for (TestExecutionPredicate predicate : context.registry.getAll(TestExecutionPredicate))
	        {
	        if (String reason := predicate.shouldSkip(context))
	            {
	            return new SkipResult(True, reason);
	            }
	        }
		return SkipResult.NotSkipped;
	    }

    @Override
	EngineExecutionContext before(ExceptionCollector     collector,
	                              EngineExecutionContext context,
	                              ExtensionRegistry      extensions) {

	    if (Class testClass := model.testClass.is(TestFixture)) {
	        if (testClass.lifecycle == Singleton) {
	            Object fixture = ensureFixture(context, extensions, testClass);
	            context = context.asBuilder(model).withTestFixture(fixture).build();
            }
        }

        for (BeforeAllCallback before : extensions.get(BeforeAllCallback, False)) {
            if (!collector.executeVoid(() -> before.beforeAll(context))) {
                break;
            }
        }
		return context;
    }

    @Override
	void after(ExceptionCollector     collector,
               EngineExecutionContext context,
               ExtensionRegistry      extensions) {

        for (AfterAllCallback after : extensions.get(AfterAllCallback,
                                                     fromParent=False,
                                                     parentFirst=False)) {
            collector.executeVoid(() -> after.afterAll(context));
        }
    }
}
