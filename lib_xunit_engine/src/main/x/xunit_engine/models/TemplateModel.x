import executor.ExecutionLifecycle;
import executor.TemplateExecutionLifecycle;

import executor.EngineExecutionContext;

import extensions.ExtensionRegistry;

const TemplateModel
        extends BaseModel {

    /**
     * Create a `TemplateModel`.
     *
     * @param template  the `Model` that this model uses for a template
     */
    construct (Model template) {
        this.template = template;
        construct BaseModel(template.uniqueId, template.displayName, template.isContainer,
                template.constructor, template.extensionProviders);
        }

    /**
     * The `Model` that this model uses for a template.
     */
    public/private Model template;

    @Override
    ExecutionLifecycle createExecutionLifecycle() {
        return new TemplateExecutionLifecycle(this);
    }
}
