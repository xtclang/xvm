/**
 * The `assertions` package contains functions and utilities to provide more feature rich assertions
 * in tests.
 */
package assertions {
    /**
     * Assert that a specific function throws an expected exception.
     *
     * @param fn  the function to execute
     *
     * @throws an `Assertion` exception if the expected exception is not thrown
     */
    static <E extends Exception> void assertThrows(Type<E> type, Function<Tuple,Tuple<Object>> fn) {
        assertThrows(type, () -> {
            fn();
        });
    }

    /**
     * Assert that a specific function throws an expected exception.
     *
     * @param fn  the function to execute
     *
     * @throws an `Assertion` exception if the expected exception is not thrown
     */
    static <E extends Exception> void assertThrows(Type<E> type, function void () fn) {
        type.DataType e = assertThrows(fn);
    }

    /**
     * Assert that a specific function throws an expected exception.
     *
     * This version of `assertThrows` returns the expected exception so that tests can do further
     * assertions on that exception, for example check it message content, etc.
     *
     * @param fn  the function to execute
     *
     * @return the expected exception
     *
     * @throws an `Assertion` exception if the expected exception is not thrown
     */
    static <E extends Exception> E assertThrows(Function<Tuple,Tuple<Object>> fn) {
        return assertThrows(() -> {
            fn();
        });
    }

    /**
     * Assert that a specific function throws an expected exception.
     *
     * This version of `assertThrows` returns the expected exception so that tests can do further
     * assertions on that exception, for example check it message content, etc.
     *
     * @param fn  the function to execute
     *
     * @return the expected exception
     *
     * @throws an `Assertion` exception if the expected exception is not thrown
     */
    static <E extends Exception> E assertThrows(function void () fn) {
        Exception? thrown = Null;
        try {
            fn();
        } catch (Exception e) {
            thrown = e;
        }

        if (thrown == Null) {
            throw new Assertion($"expected {E} to be thrown");
        }

        if (thrown.is(E)) {
            return thrown;
        }
        throw new Assertion($"expected {E} to be thrown but was {thrown}");
    }
}
