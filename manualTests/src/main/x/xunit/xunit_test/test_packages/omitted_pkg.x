/**
 * This package contains a single class with a single omitted method,
 * so during discovery no `Model` should be produced.
 */
package omitted_pkg {

    class SomeTests {
        @Test(Test.Omit)
        void shouldNotBeExecuted() {
            assert:test;
        }

        void noTest() {
        }
    }

}