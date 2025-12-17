import ecstasy.reflect.Annotation;

import discovery.selectors.TemplateSelector;

import models.TemplateModel;

import xunit.MethodOrFunction;

import xunit.templates.TestTemplate;
import xunit.templates.TestTemplateFactory;

/**
 * A `SelectorResolver` that resolves test templates.
 *
 * This resolvers specifically handles `TemplateSelector` instances.
 */
const TemplateResolver
        implements SelectorResolver {
    @Override
    conditional (ModelBuilder[], Selector[]) resolve(DiscoveryConfiguration config, Selector selector) {
        if (selector.is(TemplateSelector)) {
            Class             testClass  = selector.testClass;
            MethodOrFunction? testMethod = selector.testMethod;
            Type              type       = selector.templateAnnotation.annoClass.PublicType;
            TestTemplate      template;
            UniqueId          id;

            if (testMethod.is(TestTemplate)) {
                template = testMethod;
                id       = UniqueId.forObject(testMethod);
            } else {
                assert testClass.is(TestTemplate);
                template = testClass;
                id       = UniqueId.forObject(testClass);
            }

            TestTemplateFactory[] factories     = template.getTemplateFactories();
            TestTemplateFactory?  actualFactory = Null;
            Int                   count         = factories.size;
            Boolean               isContainer   = False;

            for (Int i : count >.. 0) {
                TestTemplateFactory factory = factories[i];
                if (factory.TemplateType == type) {
                    actualFactory = factory;
                    break;
                }
                isContainer = True;
            }

            ModelBuilder[] builders;
            if (actualFactory.is(TestTemplateFactory)) {
                for (Annotation an : selector.parents) {
                    id = UniqueId.forTemplate(id, an.annoClass.name);
                }
                id = UniqueId.forTemplate(id, selector.templateAnnotation.annoClass.name);
                builders = [new TemplateModel.Builder(id, testClass, testMethod, actualFactory, isContainer)];
            } else {
                builders = [];
            }
            return True, builders, [];
        }
        return False;
    }
}
