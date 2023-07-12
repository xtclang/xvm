import annotations.AfterEach;

/**
 * A `AfterEachCallback` extension implementation that wraps a
 * `AfterEach`. When the callback is invoked, the wrapped 
 * `AfterEach` will be invoked.
 *
 * @param after  the `AfterEach` mixin method or function to be invoked
 */
const AfterEachFunction(AfterEach after)
        implements AfterEachCallback {

    @Override Int priority.get() {
        return after.priority;
    }

    @Override
    void afterEach(ExecutionContext context) {
        context.invoke(after);
    }
}
