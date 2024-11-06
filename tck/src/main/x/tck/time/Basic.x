/**
 * Very basic time test
 */
class Basic {

    void run() {
        duration();
        time();
        date();
        timezone();
    }

    // -----------------------------
    @Test void duration() {
        Duration d = new Duration("123");
    }

    @Test
    void time() {
        try {
            Time t = new Time("123");
            assert False;
        } catch( IllegalArgument e ) { }
    }

    @Test
    void date() {
        try {
            Date t = new Date("123");
            assert False;
        } catch( IllegalArgument e ) { }
    }

    @Test
    void timezone() {
        try {
            TimeZone tz = new TimeZone("123");
            assert False;
        } catch( IllegalArgument e ) { }
    }

}
