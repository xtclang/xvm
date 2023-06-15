import annotations.BeforeAll;

/**
 * A `BeforeAllCallback` extension implementation that wraps a
 * `BeforeAll`. When the callback is invoked, the wrapped 
 * `BeforeAll` will be invoked.
 *
 * @param before  the `BeforeAll` mixin method or function to be invoked
 */
const BeforeAllFunction(BeforeAll before)
        implements BeforeAllCallback {

    @Override Int priority.get() {
        return before.priority;
    }

    @Override
    void beforeAll(ExecutionContext context) {
        //context.invoke(before);
    }
}
