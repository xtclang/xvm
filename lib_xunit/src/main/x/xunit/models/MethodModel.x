import annotations.Disabled;
import executor.SkipResult;

/**
 * A `Model` representing a test method.
 */
const MethodModel
        extends BaseModel {
    /**
     * Create a `MethodModel`.
     *
     * @param id                  a unique id for this test method
     * @param testClass           the test method's parent `Class`
     * @param testMethod          the test `Method` or `Function`
     * @param displayName         the name for this model to use when displaying test information
     * @param constructor         an optional constructor to use to create the test fixture
     * @param extensionProviders  the `ExtensionProvider`s this model will use to add additional extensions
     *                            to the test execution
     */
    construct (UniqueId id, Class testClass, TestMethodOrFunction testMethod, String displayName,
               TestMethodOrFunction? constructor, ExtensionProvider[] extensionProviders) {
        this.testClass  = testClass;
        this.testMethod = testMethod;

        if (testClass.is(Disabled)) {
            this.skipResult = new SkipResult(True, testClass.reason);
        }
        else if (testMethod.is(Disabled)) {
            this.skipResult = new SkipResult(True, testMethod.reason);
        }
        else {
            this.skipResult = SkipResult.NotSkipped;
        }
        construct BaseModel(id, displayName, False, constructor, extensionProviders);
    }

    /**
     * The test method's parent `Class`.
     */
    public/private Class testClass;

    /**
     * The test method or function this model represents.
     */
    public/private TestMethodOrFunction testMethod;

    /**
     * The `SkipResult` indicating whether the method is skipped.
     *
     * We still produce models for skipped test fixtures so that they will
     * be reported in any execution results.
     */
    public/private SkipResult skipResult;

    /**
     * The lazily calculated string value for this model.
     */
    @Lazy private String stringValue.calc() {
        return $"MethodModel(id={uniqueId})";
    }

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        return stringValue.size;
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        return stringValue.appendTo(buf);
    }
}
