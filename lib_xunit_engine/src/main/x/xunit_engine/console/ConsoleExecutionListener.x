import xunit_engine.Model;
import xunit_engine.UniqueId;

const ConsoleExecutionListener
        implements ExecutionListener {

    @Inject Console console;

    @Override
	void onStarted(Model model) {
	    console.print($"Started:   {model.displayName}");
	}

    @Override
	void onCompleted(Model model, Result result) {
        Exception? ex = result.exception;
	    if (result.status == Skipped) {
	        if (ex.is(Exception)) {
	            onSkipped(model, ex.message);
	        } else {
	            onSkipped(model, "");
	        }
	    } else {
	        console.print($"Finished:  {model.displayName} {result.status} in {result.duration}");
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
	    console.print($"Skipped:   {model.displayName} due to \"{reason}\"");
	}

    @Override
	void onPublished(Model model, ReportEntry entry) {
	    console.print($"Published: {model.displayName} {entry.timestamp}");
	    for (Map<String,String>.Entry tagEntry : entry.tags.entries) {
	    console.print($"    {tagEntry.key}: {tagEntry.value}");
	    }
	}
}
