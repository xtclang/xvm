/**
 * TODO GG:
 * 1. remove this file
 * 2. change lib_ecstasy (ecstasy.xtclang.org) module build to use javatools_mack (mack.xtclang.org)
 *    module instead of javatools_bridge (_native.xtclang.org)
 * 3. move the javatools_bridge (_native.xtclang.org) compilation to **after** the other various XDK
 *    libs are built (it has dependencies on e.g. net.xtclang.org)
 * 4. change native container to drag in mack.xtclang.org to use as its bottom turtle (in lieu of
 *    this class)
 * 5. change native container to drag in any new dependencies, such as net.xtclang.org, etc.
 */
class NakedRef<Referent>
    {
    Referent get();
    }
