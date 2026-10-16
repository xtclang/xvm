/**
 * Tests for Timer injection and functionality.
 */
class TimerTests {

    void run() {
        testResolution();
        testElapsed();
    }

    void testResolution() {
        @Inject Timer timer;
        assert timer.resolution == Duration.Nanosec;
    }

    void testElapsed() {
        @Inject Timer timer;
        assert timer.elapsed == Duration.None;

        timer.start();
        Duration elapsed1 = timer.elapsed;
        assert elapsed1 > Duration.None;

        timer.stop();
        Duration elapsed2 = timer.elapsed;
        assert elapsed2 > elapsed1;
        Duration elapsed3 = timer.elapsed;
        assert elapsed3 == elapsed2;

        timer.start();
        Duration elapsed4 = timer.elapsed;
        assert elapsed4 > elapsed3;

        timer.restart();
        Duration elapsed5 = timer.elapsed;
        assert elapsed5 > Duration.None;
        assert elapsed5 < elapsed4;

        timer.stop();
        timer.reset();
        assert timer.elapsed == Duration.None;
    }
}
