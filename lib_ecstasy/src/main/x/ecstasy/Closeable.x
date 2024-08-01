/**
 * This interface is implemented by classes that desire a notification of an end-of-lifecycle
 * event. The two common patterns associated with this interface are:
 *
 * * Objects that need to be automatically closed at the conclusion of a `using` or
 *   `try`-with-resources block, e.g. [Timeout];
 * * Objects that represent resources that need to be released or otherwise cleaned up when the
 *   holding object will no longer be used, e.g. a `ServerSocket`.
 *
 * Generally, all implementations of [close()] should be idempotent.
 */
interface Closeable {
    /**
     * This method is invoked to mark the end of the use of an object. The object should release
     * its resources at this point.
     *
     * After the completion of the `close()` method, the object should no longer be used.
     * Subsequent attempts to use the object may result in undefined behavior.
     *
     * It is recommended that an implementation of this method be idempotent, such that
     * calls to `close()` after the first one will not have any effect.
     *
     * @param cause  (optional) the exception within a `using` or `try`-with-resources block
     *               that triggered the call to `close()`, or `Null` if  the call represents a
     *               normal lifecycle completion
     */
    void close(Exception? cause = Null);
}