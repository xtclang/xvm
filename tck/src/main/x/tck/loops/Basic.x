/**
 * Basic loop tests.
 */
class Basic {

    void run() {
        testCount();
    }

    @Test
    void testCount() {
        String[] ss = [ "abc", "def", "hgi" ];
        Int sum=0;
        Loop: for( String s : ss ) {
            sum += Loop.count;
        }
        assert sum==3;
    }
}
