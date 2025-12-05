import extensions.ExtensionRegistry;

import models.TemplatedMethodModel;

/**
 * An `ExecutionLifecycle` implementation for a test template.
 */
const TemplateMethodExecutionLifecycle<ModelType extends TemplatedMethodModel>
        extends BaseExecutionLifecycle<ModelType> {
    /**
     * Create a `TemplateExecutionLifecycle`.
     *
     * @param model the `TemplateModel` this lifecycle represents
     */
    construct (ModelType model) {
        construct BaseExecutionLifecycle(model);
    }

	@Override
    Iterable<Model> getChildren(EngineExecutionContext context) {
        assert context.registry.register(Model, model.templatedModel, "TemplateModel", Replace);
        // we should always have a single TemplateModel as a child
        return model.templateModel.getChildren(context);
    }

}