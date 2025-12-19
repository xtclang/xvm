/**
 * The extensions package provides classes for extending the behavior of xUnit.
 */
package extensions {

    /**
     * An class that determines whether a test should be skipped.
     */
    interface TestExecutionPredicate
            extends Const {
        /**
         * Evaluate whether a test should be skipped.
         *
         * @return `True` if the test should be skipped, otherwise `False`
         * @return the reason the test should be skipped
         */
        conditional String shouldSkip(ExecutionContext context);
    }

    /**
     * A provider of `ExtensionProvider` instances.
     */
    interface ExtensionProviderProvider {
        /**
         * @return the `ExtensionProvider` instances this provider provides.
         */
        ExtensionProvider[] getExtensionProviders() = [];
    }

    /**
     * Returns the directory to for any files specific for the current test.
     *
     * @param root  the root directory to place the test directory under
     *
     * @return the directory to for any files specific for the current test
     */
    static Directory testDirectoryFor(Directory         root,
                                      Class?            testClass,
                                      MethodOrFunction? test = Null) {
        Directory dir = root;
        if (testClass.is(Class)) {
            String path = testClass.path;
            Int    size = path.size;
            if (path[size - 1] == ':') {
                // this is a Module
                dir = dir.dirFor(testClass.name);
            } else {
                assert Int colon := path.indexOf(':');
                String packages   = path[colon >..< path.size];
                for (String name : packages.split('.')) {
                    dir = dir.dirFor(name);
                }
                if (test.is(Test)) {
                    dir = dir.dirFor(test.name);
                }
            }
        }
        dir.ensure();
        return dir;
    }

}
