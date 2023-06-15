/**
 * A `TestTemplate` indicates that the target defines a test that is executed
 * zero or more times, based on the provided `TestTemplateFactory` instances.
 */
@Abstract mixin TestTemplate(String group = Unit, Int priority = 0)
        extends Test(group, priority)
        into Class | Method | Function {
    /**
     * @return the `TestTemplateFactory` instances to use to control execution of
     * the templated test.
     *
     * Concrete implementations of this method should call `super()` and then append
     * additional `TestTemplateFactory` instances to the returned `TestTemplateFactory`
     * array. This allows nesting of `TestTemplate` mixins. For example a test method
     * could be annotated with both `ParameterizedTest` and `RepeatedTest` to execute
     * the templated test with each set of parameters a repeated number of times.
     */
    TestTemplateFactory[] getTemplates() {
        return [];
    }
}
