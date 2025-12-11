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

        if (id.type == Iteration) {
            assert id := id.parent();
        }

        if (UniqueId parentId := id.parent(), Result parentResult := results.get(parentId)) {
            Result r = parentResult.merge(result);
            results.put(parentId, r);
        }

	    if (result.status == Skipped) {
	        if (ex.is(Exception)) {
	            onSkipped(model, ex.message);
	        } else {
	            onSkipped(model, "");
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
	void onSkipped(Model model, String reason)
	        = console.print($"Skipped:   {model.displayName} due to \"{reason}\"");

    @Override
	void onPublished(Model model, ReportEntry entry) {
	    console.print($"Published: {model.displayName} {entry.timestamp}");
	    for (Map<String,String>.Entry tagEntry : entry.tags.entries) {
	        console.print($"    {tagEntry.key}: {tagEntry.value}");
	    }
	}
}
