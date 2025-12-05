import executor.ExecutionLifecycle;
import executor.MethodExecutionLifecycle;

import xunit.SkipResult;
import xunit.MethodOrFunction;

import xunit.annotations.Disabled;

import xunit.extensions.ExtensionProvider;
import xunit.extensions.ExtensionProviderProvider;

import xunit.templates.TestTemplate;

/**
 * A model that represents a method.
 *
 * This may be a real test method or function or a template test on top of a template model.
 */
const SimpleMethodModel
        extends BaseModel
        implements MethodModel {

    /**
     * Create a `MethodModel`.
     *
     * @param id           a unique id for this test method
     * @param testClass    the test method's parent `Class`
     * @param testMethod   the test `Method` or `Function`
     * @param displayName  the name for this model to use when displaying test information
     * @param constructor  an optional constructor to use to create the test fixture
     * @param providers    the `ExtensionProvider`s this model will use to add additional extensions
     *                     to the test execution
     */
    construct (UniqueId id, Class testClass, MethodOrFunction testMethod, String displayName,
               Constructor? constructor, ExtensionProvider[] providers = []) {
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
        construct BaseModel(id, displayName, False, constructor, providers);
    }

    /**
     * The test method's parent `Class`.
     */
    @Override
    public/private Class testClass;

    /**
     * The test method or function this model represents.
     */
    @Override
    public/private MethodOrFunction testMethod;

    /**
     * The `SkipResult` indicating whether the method is skipped.
     *
     * We still produce models for skipped test fixtures so that they will
     * be reported in any execution results.
     */
    @Override
    public/private SkipResult skipResult;

    /**
     * The lazily calculated string value for this model.
     */
    @Lazy private String stringValue.calc() = $"MethodModel(id={uniqueId})";

    @Override
    ExecutionLifecycle createExecutionLifecycle() = new MethodExecutionLifecycle(this);

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() = stringValue.size;

    @Override
    Appender<Char> appendTo(Appender<Char> buf) = stringValue.appendTo(buf);
}
