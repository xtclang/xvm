/**
 * The `assertions` package contains functions and utilities to
 * provide more feature rich assertions in tests.
 */
package assertions {
    /**
     * Assert that a specific function throws an `Exception`.
     *
     * @param fn  the function to execute
     *
     * @return the expected exception
     *
     * @throws an `Assertion` exception if the expected exception is not thrown
     */
    <E extends Exception> E assertThrows(function void () fn) {
        Exception? thrown = Null;
        try {
            fn();
        } catch (Exception e) {
            thrown = e;
        }

        if (thrown.is(E)) {
            return thrown;
        }
        if (thrown == Null) {
            throw new Assertion($"expected {E} to be thrown");
        }
        throw new Assertion($"expected {E} to be thrown but was {thrown}");
    }
}