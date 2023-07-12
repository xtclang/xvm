import xunit.annotations.*;

/**
 * A package to test before and after extensions.
 */
package before_and_after {

    @BeforeAll
    void packageLevelBeforeAll() {
    }

    @AfterAll
    void packageLevelAfterAll() {
    }

    @BeforeAll
    @AfterAll
    void packageLevelBeforeAndAfterAll() {
    }

    @BeforeEach
    void packageLevelBeforeEach() {
    }

    @AfterEach
    void packageLevelAfterEach() {
    }

    @BeforeEach
    @AfterEach
    void packageLevelBeforeAndAfterEach() {
    }

    /**
     * A simple test class.
     */
    class BeforeAndAfterTests {
        @BeforeAll
        static void shouldRunBeforeAll() {
        }

        @AfterAll
        static void shouldRunAfterAll() {
        }

        @BeforeAll
        @AfterAll
        static void shouldRunBeforeAndAfterAll() {
        }

        @BeforeEach
        void shouldRunBeforeEach() {
        }

        @AfterEach
        void shouldRunAfterEach() {
        }

        @BeforeEach
        @AfterEach
        void shouldRunBeforeAndAfterEach() {
        }

        @Test
        void testOne() {
        }
    }
}