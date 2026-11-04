import xunit_engine.discovery.Selector;
import xunit_engine.discovery.selectors;

import executor.BaseTestRunner;
import executor.CompositeExecutionListener;

/**
 * An XUnit test runner that executes tests and prints the results to the console; with the
 * injection `xvm.xunit.report` ([ConfigReport]), also as the report it names, for a tool that reads
 * the execution from the output.
 */
service ConsoleRunner
        extends BaseTestRunner {

    /**
     * The injection name of the machine-readable report to print as well, if any: one of
     * [Reports].
     */
    static String ConfigReport = ConfigPrefix + ".report";

    /**
     * The names of the reports: `json`, an event per line, for a tool that reads the execution from
     * the output, such as an IDE ([JsonExecutionListener]).
     */
    static String[] Reports = ["json"];

    @Override
    ExecutionListener? createExecutionListener(Module testModule) {
        @Inject(ConfigReport) String? report;
        ExecutionListener console = new ConsoleExecutionListener();
        return report.is(String)
                ? new CompositeExecutionListener([console, createReport(report)])
                : console;
    }

    /**
     * @return the listener that prints the report `name`
     *
     * @throws IllegalArgument if no report has that name
     */
    ExecutionListener createReport(String name) {
        switch (name) {
        case "json":
            return new JsonExecutionListener();

        default:
            throw new IllegalArgument($"Unknown test report {name}, not one of {Reports}");
        }
    }
}
