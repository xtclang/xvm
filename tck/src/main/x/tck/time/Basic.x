/**
 * Very basic time test
 */
class Basic {

    void run() {
        //duration();
        //time();
        date();
        //timezone();
    }

    // -----------------------------
    //@Test void duration() {
    //    Duration d = new Duration("1:23");
    //}
    //
    //@Test void time() {
    //    Time t = new Time("1:23");
    //}
    @Test void date() {
        Date t = new Date("123");
    }
    //@Test void timezone() {
    //    try {
    //        TimeZone tz = new TimeZone("123");
    //        assert False;
    //    } catch( IllegalArgument e ) {
    //    }
    //}

}
