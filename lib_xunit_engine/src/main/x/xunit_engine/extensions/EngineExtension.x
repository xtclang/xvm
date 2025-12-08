/**
 * An extension that can be applied to the test engine.
 */
interface EngineExtension
    extends ExecutionListener {

    /**
     * Called after the engine has been initialized and before test discovery or test execution
     * begins.
     */
    void init() {
    }
}