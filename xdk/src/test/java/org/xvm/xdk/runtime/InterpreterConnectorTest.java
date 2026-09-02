package org.xvm.xdk.runtime;


import org.junit.jupiter.api.Test;

import org.xvm.api.InterpreterConnector;

import org.xvm.asm.Constants;

import org.xvm.xdk.BuiltXdk;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A connector can load the system module it was booted from.
 *
 * <p>Deliberately one connector. An earlier version of this test built several side by side to show
 * that they owned independent native containers - a topology that is disputed, and that the test did
 * not need: the defect it actually caught reproduced on the <em>first</em> connector, with no
 * concurrency at all. {@code FileStructure.merge} superseded an existing fingerprint of the module
 * being merged but not an existing real one, so loading ecstasy into a native container that boots
 * with ecstasy already present found itself, reached {@code addChild} with a name that already had a
 * sibling, and asserted inside {@code adoptChildren}.</p>
 *
 * <p>Asserting nothing about how many containers a host may build keeps this a regression test for
 * that fix rather than a position on the container model.</p>
 */
public class InterpreterConnectorTest {
    @Test
    public void aConnectorLoadsTheModuleItBootedFrom() {
        assumeTrue(BuiltXdk.systemModulesAvailable(),
                "the installed XDK is required; run: ./gradlew installDist");

        var connector = new InterpreterConnector(BuiltXdk.systemRepository());
        assertDoesNotThrow(() -> connector.loadModule(Constants.ECSTASY_MODULE),
                "loading a module the native container already holds must merge, not assert");
        assertNotNull(connector.getConstantPool(),
                "the connector must expose the loaded module's pool");
    }
}
