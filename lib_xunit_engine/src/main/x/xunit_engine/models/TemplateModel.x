import executor.EngineExecutionContext;
import executor.ExecutionLifecycle;
import executor.MethodExecutionLifecycle;

import xunit.MethodOrFunction;
import xunit.SkipResult;

import xunit.extensions.ExecutionContext;
import xunit.extensions.Extension;
import xunit.extensions.ExtensionProvider;

import xunit.annotations.Disabled;

import xunit.extensions.ResourceRegistrationCallback;

import xunit.templates.TestTemplateContext;
import xunit.templates.TestTemplateFactory;

/**
 * A model that is a template based on another model.
 *
 * A template is a model that may produce one or more child models that are based on modifications
 * applied to a template model.
 */
const TemplateModel
        extends BaseModel {

    /**
     * Create a `TemplateModel`.
     *
     * @param id           a unique id for this test method
     * @param testClass    the test method's parent `Class`
     * @param testMethod   the test `Method` or `Function`
     */
    construct (UniqueId            id,
               Class               testClass,
               MethodOrFunction?   testMethod,
               TestTemplateFactory factory,
               TemplateModel?      templateModel) {

        this.testClass     = testClass;
        this.testMethod    = testMethod;
        this.factory       = factory;
        this.templateModel = templateModel;

        if (testClass.is(Disabled)) {
            this.skipResult = new SkipResult(True, testClass.reason);
        }
        else if (testMethod.is(Disabled)) {
            this.skipResult = new SkipResult(True, testMethod.reason);
        }
        else {
            this.skipResult = SkipResult.NotSkipped;
        }
        construct BaseModel(id, id.value, True);
    }

    /**
     * The test method's parent `Class`.
     */
    Class testClass;

    /**
     * The test method or function this model represents.
     */
    MethodOrFunction? testMethod;

    /**
     * The `TestTemplateFactory` used to create the models for this template.
     */
    TestTemplateFactory factory;

    /**
     * The optional child `TemplateModel`.
     */
    TemplateModel? templateModel;

    /**
     * The `SkipResult` indicating whether the method is skipped.
     *
     * We still produce models for skipped test fixtures so that they will
     * be reported in any execution results.
     */
    SkipResult skipResult;

    @Override
    ExecutionLifecycle createExecutionLifecycle() = throw new Unsupported();

    Iterable<Model> getChildren(EngineExecutionContext context) {
        Iterable<Model> models;

        TemplateModel? templateModel = this.templateModel;
        if (templateModel.is(TemplateModel)) {
            models = templateModel.getChildren(context);
        } else {
            // else this is the bottom of the template chain so execute the template model
            // which should have already bee added to the registry
            assert Model model := context.registry.get(Model, "TemplateModel");
            models = [model];
        }
        Iterable<TestTemplateContext> templateContexts = factory.getContexts(context);
        return new TemplateIterable(uniqueId, templateContexts, models);
    }

	@Override
	conditional Model findByUniqueId(UniqueId uniqueId) {
	    if (this.uniqueId == uniqueId) {
	        return True, this;
        }
	    for (Model child : children) {
	        if (Model found := child.findByUniqueId(uniqueId)) {
	            return True, found;
            }
        }
	    return False;
    }

    // ----- inner service: Builder ----------------------------------------------------------------

    /**
     * A builder to build a `TemplateModel`.
     */
    static const Builder(UniqueId            uniqueId,
                           Class               testClass,
                           MethodOrFunction?   testMethod,
                           TestTemplateFactory factory,
                           Boolean             isContainer)
            implements ModelBuilder {

        @Override
        Model build(DiscoveryConfiguration configuration, Model[] children) {
            // if there are children there should only be a single TemplateModel child
            Int count = children.size;
            assert count == 0 || count == 1 as "expected zero or one TemplateModel child but found {count}";
            TemplateModel? templateModel = Null;
            if (count == 1) {
                Model child = children[0];
                assert child.is(TemplateModel) as "expected a TemplateModel child but was {&child.type}";
                templateModel = child;
            }
            return new TemplateModel(uniqueId, testClass, testMethod, factory, templateModel);
        }

        // ----- Stringable methods ----------------------------------------------------------------

        @Override
        Int estimateStringLength() = uniqueId.estimateStringLength() + 20;

        @Override
        Appender<Char> appendTo(Appender<Char> buf) {
            "TemplateModelBuilder(".appendTo(buf);
            uniqueId.appendTo(buf);
            ")".appendTo(buf);
            return buf;
        }
    }

    // ----- inner service: TemplateIterable -------------------------------------------------------

    /**
     * An Iterable of templated models.
     */
    const TemplateIterable(UniqueId parentId, Iterable<TestTemplateContext> contexts,
    Iterable<Model> models)
            implements Iterable<Model> {

        @Override
        Int size.get() = contexts.size * models.size;

        @Override
        Iterator<Model> iterator() {
            return new NestedModelIterator(parentId, contexts.iterator(), models);
        }
    }

    // ----- inner class: NestedModelIterator ------------------------------------------------------

    class NestedModelIterator
            implements Iterator<Model> {

        construct(UniqueId parentId, Iterator<TestTemplateContext> contexts, Iterable<Model> models) {
            this.parentId  = parentId;
            this.contexts  = contexts;
            this.models    = models;
            this.iteration = 1;
            if (TestTemplateContext context := contexts.next()) {
                this.context  = context;
                this.iterator = models.iterator();
                if (Model model := iterator.next()) {
                    this.model = modelFor(parentId, context, model, this.iteration);
                } else {
                    this.model = Null;
                }
            } else {
                this.context = Null;
                this.model   = Null;
            }
        }

        private UniqueId parentId;

        private Iterator<TestTemplateContext> contexts;

        private Iterable<Model> models;

        private TestTemplateContext? context;

        private Iterator<Model> iterator;

        private Model? model;

        private Int iteration;

        @Override
        conditional Model next() {
            Model? model = this.model;
            if (model.is(Model)) {
                if (Model nextModel := iterator.next()) {
                    this.model = modelFor(parentId, context.as(TestTemplateContext),
                                          nextModel, iteration);
                } else if (TestTemplateContext context := contexts.next()) {
                    this.context  = context;
                    this.iterator = models.iterator();
                    assert Model nextModel := this.iterator.next();
                    this.model = modelFor(parentId, context, nextModel, ++iteration);
                } else {
                    this.model = Null;
                }
                return True, model;
            }
            return False;
        }

        static Model modelFor(UniqueId parentId, TestTemplateContext context,
                              Model model, Int iteration) {

            UniqueId id = parentId.append(Iteration, iteration.toString());
            if (model.is(MethodModel)) {
                return new TemplateIterationMethodModel(id, context, model, iteration);
            }
            return new TemplateIterationModel(id, context, model, iteration);
        }
    }

    // ----- inner const: TemplateIterationModel ---------------------------------------------------

    static const TemplateIterationModel<ModelType extends Model>(UniqueId            uniqueId,
                                                                 TestTemplateContext context,
                                                                 ModelType           delegate,
                                                                 Int                 iteration)
            extends WrapperModel<ModelType>(delegate, uniqueId)
            implements ExtensionProvider
            implements ResourceRegistrationCallback {

        @Override @Lazy String name.calc() = $"{delegate.displayName} ({iteration})";

        @Override @Lazy ExtensionProvider[] extensionProviders.calc() {
            Array<ExtensionProvider> providers = new Array();
            providers.addAll(delegate.extensionProviders);
            providers.add(this);
            return providers.freeze(True);
        }

        @Override @Lazy String displayName.calc() {
            return $"{delegate.displayName} {context.getDisplayName(iteration)}";
        }

        @Override
        Extension[] getExtensions(ExecutionContext context) {
            Extension[] extensions = new Array();
            extensions.addAll(this.context.getExtensions(iteration));
            extensions.add(this);
            return extensions;
        }

        @Override
        void registerResources(ResourceRegistry registry) {
            for (ResourceRegistry.Resource resource : context.getResources(iteration)) {
                assert String unused := registry.register(resource) as $"Failed to register resource {resource}";
            }
        }
    }

    // ----- inner const: TemplateIterationMethodModel ---------------------------------------------

    static const TemplateIterationMethodModel<ModelType extends MethodModel>(
                 UniqueId            uniqueId,
                 TestTemplateContext context,
                 ModelType           delegate,
                 Int                 iteration)
            extends WrapperMethodModel<ModelType>(delegate, uniqueId)
            implements ExtensionProvider
            implements ResourceRegistrationCallback {

        @Override @Lazy String name.calc() = $"{delegate.displayName} ({iteration})";

        @Override @Lazy ExtensionProvider[] extensionProviders.calc() {
            Array<ExtensionProvider> providers = new Array();
            providers.addAll(delegate.extensionProviders);
            providers.add(this);
            return providers.freeze(True);
        }

        @Override @Lazy String displayName.calc() {
            return $"{delegate.displayName} {context.getDisplayName(iteration)}";
        }

        @Override
        ExecutionLifecycle createExecutionLifecycle() = new MethodExecutionLifecycle(this);

        @Override
        Extension[] getExtensions(ExecutionContext context) {
            Extension[] extensions = new Array();
            extensions.addAll(this.context.getExtensions(iteration));
            extensions.add(this);
            return extensions;
        }

        @Override
        void registerResources(ResourceRegistry registry) {
            for (ResourceRegistry.Resource resource : context.getResources(iteration)) {
                assert String unused := registry.registerResource(resource) as $"Failed to register resource {resource}";
            }
        }
    }
}