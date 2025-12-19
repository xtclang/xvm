import xunit_engine.discovery.Selector;
import xunit_engine.discovery.selectors;

import executor.BaseTestRunner;

/**
 * An XUnit test runner that executes tests and prints the results to the console.
 */
service ConsoleRunner
        extends BaseTestRunner {

    @Override
    ExecutionListener? createExecutionListener(Module testModule) {
        return new ConsoleExecutionListener();
    }
}
