import slogging.MemoryHandler;

/**
 * Applies the contract helper to the shipped memory handler. Third-party handlers can
 * copy this pattern for their own tests.
 */
class HandlerContractTest {

    @Test
    void shouldValidateMemoryHandlerDerivations() {
        MemoryHandler handler = new MemoryHandler();

        HandlerContract.assertWithAttrsPrepend(handler, () -> handler.records);

        handler.reset();

        HandlerContract.assertWithGroupNests(handler, () -> handler.records);
    }
}
