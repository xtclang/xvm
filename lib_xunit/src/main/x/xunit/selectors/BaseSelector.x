import annotations.*;
import models.ContainerModel;
import models.MethodModel;
import models.TemplateModel;

/**
 * A base class for `Selector` implementations.
 *
 * @param parentId  the parent identifier for models or selectors
 *                  discovered by this selector
 */
@Abstract service BaseSelector<DataType>
        implements Selector {
    /**
     * Produce a `Model` for the specified test container `Class`.
     *
     * @param config  the `DiscoveryConfiguration` to determine what tests to select
     * @param clz     the `Class` to discover tests in
     *
     * @return True if the class contains any test fixtures
     * @return the `Model` representing the test fixtures in the class
     */
    conditional Model processContainer(DiscoveryConfiguration config, Class clz) {
        Class[] childClasses = new Array();
        for (Type type : clz.toType().childTypes.values) {
            if (Class childClass := type.fromClass()) {
                childClasses.add(childClass);
            }
        }
        return processContainer(config, clz, childClasses);
    }

    /**
     * Produce a `Model` for the specified test container `Class`.
     *
     * @param config    the `DiscoveryConfiguration` to determine what tests to select
     * @param clz       the `Class` to discover tests in
     * @param children  the children of the container class to process
     *
     * @return True if the class contains any test fixtures
     * @return the `Model` representing the test fixtures in the class
     */
    conditional Model processContainer(DiscoveryConfiguration config, Class clz, Class[] childClasses) {
        DisplayNameGenerator   generator          = config.displayNameGenerator;
        String                 name               = generator.nameForClass(clz);
        UniqueId               id                 = UniqueId.forClass(clz);
        Type                   type               = clz.toType();
        TestMethodOrFunction?  constructor        = clz.isSingleton() ? Null : findConstructor(type);
        ExtensionProvider[]    extensionProviders = new Array();
        Array<Future<Model[]>> futures            = processNestedClasses(config, clz, childClasses);

        findExtensions(type.functions,  extensionProviders);
        findExtensions(type.methods,    extensionProviders);
        findExtensions(type.properties, extensionProviders);
        findExtensions(type.constants,  extensionProviders);

        Test[] testMethods = new Array();

        for (Function fn : type.functions) {
            if (fn.is(Test) && !fn.omitted()) {
                testMethods.add(fn);
            }
        }

        for (Method method : type.methods) {
            if (method.is(Test) && !method.omitted()) {
                testMethods.add(method);
            }
        }

        Model[] childModels = new Array();

        for (Test test : testMethods.sorted()) {
            if (test.is(TestMethodOrFunction)) {
                UniqueId childId     = id.append(UniqueId.SEGMENT_METHOD, test.name);
                String   displayName = generator.nameForMethod(clz, test);
                Model    methodModel = new MethodModel(childId, clz, test, displayName,
                                                constructor, extensionProviders);
                if (test.is(templates.TestTemplate)) {
                    methodModel = new TemplateModel(methodModel);
                }
                childModels.add(methodModel);
            }
        }

        for (Future<Model[]> f : futures) {
            childModels.addAll(f.get());
        }

        if (childModels.empty) {
            return False;
        }

        Model model = new ContainerModel(id, clz, name, constructor, extensionProviders, childModels);
        if (clz.is(templates.TestTemplate)) {
            model = new TemplateModel(model);
        }

        return True, model;
    }

    private Array<Future<Model[]>> processNestedClasses(DiscoveryConfiguration config, Class clz, Class[] childClasses) {
        Array<Future<Model[]>> futures = new Array();
        for (Class childClass : childClasses) {
            if (childClass.abstract || (childClass.is(Test) && !childClass.omitted())) {
                // skip over abstract and omitted classes
                continue;
            }
            Type childType = childClass.toType();
            if (childType.isA(Package)) {
                if (Object o := childClass.isSingleton()) {
                    Package pkg = o.as(Package);
                    if (pkg.isModuleImport()) {
                        // skip module imports
                        continue;
                    }
                    PackageSelector s = new PackageSelector(pkg);
                    @Future Model[] t = s.select^(config);
                    futures.add(&t);
                }
            } else {
                ClassSelector s = new ClassSelector(childClass);
                @Future Model[] t = s.select^(config);
                futures.add(&t);
            }
        }
        return futures;
    }

    /**
     * Add any `ExtensionProvider`s in the iterable to the `ExtensionProvider` array
     */
    private void findExtensions(Iterable iter, ExtensionProvider[] providers) {
        for (Object o : iter) {
            if (o.is(ExtensionProvider)) {
                providers.add(o);
            }
            if (o.is(AfterAll)) {
                providers.add(o.asAfterAllProvider());
            }
            if (o.is(AfterEach)) {
                providers.add(o.asAfterEachProvider());
            }
            if (o.is(BeforeAll)) {
                providers.add(o.asBeforeAllProvider());
            }
            if (o.is(BeforeEach)) {
                providers.add(o.asBeforeEachProvider());
            }
            if (o.is(RegisterExtension)) {
                providers.add(o.asProvider());
            }
        }
    }

    /**
     * Find the constructor that should be used to create instances of
     * the specified type.
     *
     * If any constructor is annotated with `@Test` that constructor will be used.
     * If multiple constructors are annotated with `@Test`, the first constructor
     * found will be used, which may be non-deterministic.
     *
     * If not constructors are annotated with `@Test` the default constructor will be used,
     * which is either a no-arg constructor, or a constructor where all parameters have
     * default values.
     *
     * @param type  the `Type` to find the constructor for
     *
     * @return the constructor to use, or `Null` if no constructor can be found for the type.
     */
    private Function<<>, <Object>>? findConstructor(Type type) {
        Function<<>, <Object>>? constructor = Null;
        for (Function<Tuple, <>> c : type.constructors) {
            if (c.is(Test)) {
                constructor = c;
                break;
            }
            else if (c.requiredParamCount == 0) {
                constructor = c;
            }
        }
        return constructor;
    }
}
