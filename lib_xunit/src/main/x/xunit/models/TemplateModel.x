import extensions.ExtensionRegistry;
import templates.TestTemplateContext;
import templates.TestTemplateFactory;

const TemplateModel
        extends BaseModel
//        implements ExecutionLifecycle
    {
    construct (Model templateModel)
        {
        this.templateModel = templateModel;
        construct BaseModel(templateModel.uniqueId, templateModel.displayName, templateModel.isContainer,
                templateModel.constructor, templateModel.extensionProviders);
        }

    public/private Model templateModel;

    // ----- ExecutionLifecycle methods ------------------------------------------------------------

//    @Override
//    List<Model> getChildren(DefaultExecutionContext context)
//        {
//        return createChildren(templateModel.templateFactories, 0, context, new Array());
//        }

	// ----- TemplateModel methods -----------------------------------------------------------------

    private List<Model> createChildren(TestTemplateFactory[] factories, Int index, ExecutionContext context, List<Model> models)
        {
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
