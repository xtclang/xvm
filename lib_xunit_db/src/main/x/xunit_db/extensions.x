import xunit.extensions.Extension;

/**
 * The extensions package contains XUnit extensions for database testing.
 */
package extensions {
    /**
     * Create the extensions to be registered with the XUnit test.
     */
    static Extension[] createTestEngineExtensions() {
        return [new DbInjector()];
    }
}
