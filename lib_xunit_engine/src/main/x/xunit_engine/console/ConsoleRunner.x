import xunit_engine.discovery.Selector;
import xunit_engine.discovery.selectors;

import executor.BaseTestRunner;
import executor.CompositeExecutionListener;

/**
 * An XUnit test runner that executes tests and prints results to the console. When the
 * `xvm.xunit.report` injection ([ConfigReport]) is `json`, it also prints machine-readable events
 * for tools such as IDEs.
 */
service ConsoleRunner
        extends BaseTestRunner {

    /**
     * Injection key for the optional machine-readable report; its value must name one of [Reports].
     */
    static String ConfigReport = ConfigPrefix + ".report";

    /**
     * Supported report formats. `json` emits one event per line ([JsonExecutionListener]).
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
     * @return a listener for the named report
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
