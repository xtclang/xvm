/**
 * An XUnit ExecutionListener that prints test execution results to the console.
 */
service ConsoleExecutionListener
        implements ExecutionListener {

    /**
     * The console to print results to.
     */
    @Inject Console console;

    /**
     * A map of `UniqueId`s to `Result`s.
     */
    private Map<UniqueId, Result> results = new HashMap();

    /**
     * Whether all tests have succeeded.
     */
    Boolean success = True;

    /**
     * A singleton `Result` representing the initial result for a model.
     */
    static Result InitialResult = new Result(Successful, count=0);

    /**
     * A singleton `Result` representing a model that was skipped without running.
     */
    static Result SkippedResult = new Result(Skipped, skipped=1);

    @Override
    void onStarted(Model model) {
        console.print($"Started:   {model.displayName}");
        results.put(model.uniqueId, InitialResult);
    }

    @Override
    void onCompleted(Model model, Result result) {
        UniqueId   id = model.uniqueId;
        Exception? ex = result.exception;

        this.success &= result.status.success;

        if (model.isContainer) {
            if (Result thisResult := results.get(id)) {
                result = thisResult;
            }
        }
        results.remove(id);
        addToParent(id, result);

        if (result.status == Skipped) {
            if (ex.is(Exception)) {
                printSkipped(model, ex.message);
            } else {
                printSkipped(model, "");
            }
        } else {
            if (model.isContainer) {
                console.print($|Finished:  {model.displayName} {result.status} in {result.duration}\
                               | (passed={result.succeeded} failed={result.failures}\
                               | skipped={result.skipped} errors={result.errors})
                               );
            } else {
                console.print($"Finished:  {model.displayName} {result.status} in {result.duration}");
            }
            if (ex.is(Exception)) {
                console.print(ex);
            }
            Exception[]? suppressed = result.suppressed;
            if (suppressed.is(Exception[])) {
                for (Exception e : suppressed) {
                    console.print($"Suppressed: {e}");
                }
            }
        }
    }

    @Override
    void onSkipped(Model model, String reason) {
        addToParent(model.uniqueId, SkippedResult);
        printSkipped(model, reason);
    }

    @Override
    void onPublished(Model model, ReportEntry entry) {
        console.print($"Published: {model.displayName} {entry.timestamp}");
        for (Map<String,String>.Entry tagEntry : entry.tags.entries) {
            console.print($"    {tagEntry.key}: {tagEntry.value}");
        }
    }

    /**
     * Add a model's result to its parent's, or, for an iteration, to the parent of the test it
     * iterates.
     *
     * @param id      the model's `UniqueId`
     * @param result  the model's `Result`
     */
    private void addToParent(UniqueId id, Result result) {
        if (id.type == Iteration) {
            assert id := id.parent();
        }

        if (UniqueId parentId := id.parent(), Result parentResult := results.get(parentId)) {
            Result r = parentResult.merge(result);
            results.put(parentId, r);
        }
    }

    /**
     * Print that a model was skipped.
     */
    private void printSkipped(Model model, String reason)
            = console.print($"Skipped:   {model.displayName} due to \"{reason}\"");
}
