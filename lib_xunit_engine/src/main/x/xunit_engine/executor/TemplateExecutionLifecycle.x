import models.TemplateModel;

import xunit.templates.TestTemplateContext;
import xunit.templates.TestTemplateFactory;

/**
 * An `ExecutionLifecycle` implementation for a `TemplateModel`.
 */
const TemplateExecutionLifecycle
        extends BaseExecutionLifecycle<TemplateModel> {

    /**
     * Create a `TemplateModelExecutionLifecycle`.
     *
     * @param model the `TemplateModel` this lifecycle represents
     */
    construct (TemplateModel model) {
        construct BaseExecutionLifecycle(model);
    }

    // ----- ExecutionLifecycle methods ------------------------------------------------------------

    @Override
    List<Model> getChildren(EngineExecutionContext context) {
        return createChildren(model.template.templateFactories, 0, context, new Array());
    }

    // ----- helper methods ------------------------------------------------------------------------

    private List<Model> createChildren(TestTemplateFactory[] factories, Int index, EngineExecutionContext context,
                                       List<Model> models) {
return [];
//        if (index >= factories.size)
//            {
//            return models;
//            }
//
//        TestTemplateFactory           factory          = factories[index];
//        Iterable<TestTemplateContext> templateContexts = factory.getTemplates(context);
//        Int                           iteration        = 0;
//        Model[]                       children         = new Array();
//
//        for (TestTemplateContext templateContext : templateContexts)
//            {
//            (String name, Extension[] extensions, ResourceRegistry.Resource[] resources)
//                = templateContext.getTemplateInfo(iteration);
//
//            if (models.empty)
//                {
//                children.add(new MethodTemplateModel(templateModel.as(MethodModel), displayName + " " + name, extensions, resources));
//                }
//            else
//                {
//                for (Model model : models)
//                    {
//                    children.add(new MethodTemplateModel(templateModel.as(MethodTemplateModel), model.displayName + " " + name, extensions, resources));
//                    }
//                }
//            iteration++;
//            }
//
//        return createChildren(factories, index + 1, context, children);
    }
}