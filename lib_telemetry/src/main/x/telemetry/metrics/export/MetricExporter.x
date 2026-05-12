import metrics.model.ResourceMetrics;

/**
 * The interface implemented by all metric exporters.
 *
 * An exporter receives [ResourceMetrics] batches (one per [collect] cycle) and
 * transmits them to a backend — a file, a network endpoint, stdout, etc.
 *
 * Implementations must be thread-safe.
 */
interface MetricExporter {

    static const Result(Status status, Duration retryIn = None);

    static Result Success  = new Result(Status.Success);
    static Result Failure  = new Result(Status.Failure);
    static Result DropData = new Result(Status.DropData);

    enum Status {
        /** The batch was successfully transmitted. */
        Success,
        /** A retryable error occurred; the batch may be retried. */
        Failure,
        /** The exporter is shutting down; the batch was dropped. */
        DropData
    }

    /**
     * Export a batch of [ResourceMetrics].
     *
     * @return [Result.Success] on success, [Result.Failure] on a retryable error, or
     *         [Result.DropData] if the exporter is shut down or a timeout was exceeded
     */
    Result export(ResourceMetrics[] metrics);

    /**
     * Block until all pending export calls have completed, or until the timeout elapses.
     */
    void forceFlush();

    /**
     * Shut down the exporter, releasing held resources. After this call returns,
     * subsequent [export] calls return [Result.DropData].
     */
    void shutdown();
}
