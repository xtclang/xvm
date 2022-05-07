/**
 * This is the "native" Ecstasy module for the Java prototype compiler and runtime.  The prototype
 * runtime loads his code in "container -1" (the "embryonic" container that gives birth to the
 * initial "container 0"), and uses it to provide implementations for various system services.
 *
 * It is an error for any type or class from this module to be visible to user code.
 */
module _native.xtclang.org
    {
    package libnet import net.xtclang.org;
    }
