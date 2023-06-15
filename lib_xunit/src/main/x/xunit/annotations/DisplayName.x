/**
 * A mixin that can be applied to a `TestTarget` to provide
 * a human readable name for the test fixture.
 */
mixin DisplayName(String name)
    into Method | Function;
