/**
 * Simple unit tests for the json module.
 *
 * This test verifies that the XTC plugin correctly handles test source sets
 * during the XVM bootstrap build.
 */
module JsonTest {
    package json  import json.xtclang.org;
    package xunit import xunit.xtclang.org;

    /**
     * Basic tests for JSON document types.
     */
    class DocTest {
        @Test
        void shouldCreateBooleanDoc() {
            json.Doc docTrue = True;
            json.Doc docFalse = False;
            assert docTrue == True;
            assert docFalse == False;
        }

        @Test
        void shouldCreateStringDoc() {
            json.Doc doc = "hello";
            assert doc == "hello";
        }

        @Test
        void shouldCreateIntDoc() {
            json.Doc doc = 42;
            assert doc == 42;
        }
    }
}
