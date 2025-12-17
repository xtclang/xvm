/**
 * An extension that allows registering resources.
 */
interface ResourceRegistrationCallback
        extends Extension {

    /**
     * Called repeatedly before each test is invoked to allow registration of resources for the
     * specific test. This method will be called ahead of any other "before" test processing.
     *
     * If this method throws an exception, no further "before" test processing will be executed, the
     * test will not execute, but all "after" test processing will be executed.
     *
     * @param context  the `ResourceRegistry` to register resources with
     */
    void registerResources(ResourceRegistry registry);
}
