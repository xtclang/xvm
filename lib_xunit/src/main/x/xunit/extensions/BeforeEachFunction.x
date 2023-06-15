import annotations.BeforeEach;

/**
 * A `BeforeEachCallback` extension implementation that wraps a
 * `BeforeEach`. When the callback is invoked, the wrapped 
 * `BeforeEach` will be invoked.
 *
 * @param before  the `BeforeEach` mixin method or function to be invoked
 */
const BeforeEachFunction(BeforeEach before)
        implements BeforeEachCallback {

    @Override
    Int priority.get() {
        return before.priority;
    }

    @Override
    void beforeEach(ExecutionContext context) {
        //context.invoke(before);
    }
}
