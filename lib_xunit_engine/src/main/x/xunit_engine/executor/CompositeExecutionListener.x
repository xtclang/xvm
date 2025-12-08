/**
 * An `ExecutionListener` that forwards all events to a list of other listeners.
 */
service CompositeExecutionListener(ExecutionListener[] listeners)
        implements ExecutionListener {

    @Override
	void onStarted(Model model) {
	    for (ExecutionListener listener : listeners) {
	        listener.onStarted(model);
	    }
	}

    @Override
	void onCompleted(Model model, Result result) {
	    for (ExecutionListener listener : listeners) {
	        listener.onCompleted(model, result);
	    }
	}

    @Override
	void onSkipped(Model model, String reason) {
	    for (ExecutionListener listener : listeners) {
	        listener.onSkipped(model, reason);
	    }
	}

    @Override
	void onPublished(Model model, ReportEntry entry) {
	    for (ExecutionListener listener : listeners) {
	        listener.onPublished(model, entry);
	    }
	}
}