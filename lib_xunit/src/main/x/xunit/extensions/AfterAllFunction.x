import annotations.AfterAll;

/**
 * A `AfterAllCallback` extension implementation that wraps a
 * `AfterAll`. When the callback is invoked, the wrapped
 * `AfterAll` will be invoked.
 *
 * @param after  the `AfterAll` mixin method or function to be invoked
 */
const AfterAllFunction(AfterAll after)
        implements AfterAllCallback {

    @Override Int priority.get() {
        return after.priority;
    }

    @Override
    void afterAll(ExecutionContext context) {
        context.invoke(after);
    }
}
