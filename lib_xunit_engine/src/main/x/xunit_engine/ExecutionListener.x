/**
 * A listener that receives events related to the execution of test fixtures.
 */
interface ExecutionListener {
	/**
	 * Called when the execution of a `Model` in the test hierarchy has been started.
	 *
	 * @param model  the `Model` representing the test or container
	 */
	void onStarted(Model model) {
	}

	/**
	 * Called when the execution of a `Model` in the test hierarchy has completed.
	 *
	 * @param model   the `Model` representing the test or container
	 * @param result  the `Result` of the test execution
	 */
	void onCompleted(Model model, Result result) {
	}

	/**
	 * Called when the execution of a `Model` in the test hierarchy has been skipped.
	 *
	 * @param model   the `Model` representing the test or container
	 * @param reason  a message describing why the test was skipped
	 */
	void onSkipped(Model model, String reason) {
	}

	/**
	 * Called for a `Model` to publish additional information.
	 *
	 * @param model  the `Model` representing the test or container
	 * @param entry       a `ReportEntry` instance to be published
	 */
	void onPublished(Model model, ReportEntry entry) {
	}

    // ---- inner const: ReportEntry ---------------------------------------------------------------

    /**
     * An entry in a test report.
     */
    static const ReportEntry {
        /**
         * Create a `ReportEntry`.
         *
         * @param timestamp  the timestamp of the report entry
         * @param tags       optional test information tags
         */
        construct(Time? timestamp, Map<String, String> tags = Map:[]) {
            if (timestamp.is(Time)) {
                this.timestamp = timestamp.as(Time);
            }
            else {
                @Inject Clock clock;
                this.timestamp = clock.now;
            }
            this.tags = tags;
        }

        /**
         * The timestamp of this report entry.
         */
        Time timestamp;

        /**
         * Additional report information tags.
         */
        Map<String, String> tags;

        // ---- Orderable --------------------------------------------------------------------------

        /**
         * Compare two ReportEntry values for the purposes of ordering.
         */
        static <CompileType extends ReportEntry> Ordered compare(CompileType value1, CompileType value2) {
            return value1.timestamp <=> value2.timestamp;
        }

        /**
         * Compare two ReportEntry values for equality.
         */
        static <CompileType extends ReportEntry> Boolean equals(CompileType value1, CompileType value2) {
            return value1.timestamp == value2.timestamp;
        }
    }

    // ---- inner const: NoOpListener --------------------------------------------------------------

    /**
     * The singleton `NoOpListener` instance.
     */
    static ExecutionListener NoOp = new NoOpListener();

    /**
     * An `ExecutionListener` that does nothing.
     */
    static const NoOpListener
            implements ExecutionListener {
    }
}