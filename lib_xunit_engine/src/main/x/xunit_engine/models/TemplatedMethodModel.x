import executor.ExecutionLifecycle;
import executor.TemplateMethodExecutionLifecycle;

/**
 * A model representing a method that is annotated with a template annotation.
 */
const TemplatedMethodModel<ModelType extends MethodModel>
        extends WrapperMethodModel<ModelType>
        implements TemplatedModel<ModelType> {

    construct(ModelType delegate, Model[] children) {
        assert children.size == 1 as "expected a TemplateModel child but children was empty";
        Model model = children[0];
        assert model.is(TemplateModel) as "expected a TemplateModel child but was {&model.type}";
        this.templateModel = model;
        construct WrapperMethodModel(delegate);
    }

    TemplateModel templateModel;

    ModelType templatedModel.get() = delegate;

    @Override
    Iterable<Model> children.get() = [templateModel];

    @Override
    Boolean isContainer.get() = True;

    @Override
    ExecutionLifecycle createExecutionLifecycle() = new TemplateMethodExecutionLifecycle(this);

    @Override
    ModelType template.get() = delegate;
 }