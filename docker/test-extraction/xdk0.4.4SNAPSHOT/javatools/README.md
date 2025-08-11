This directory contains the Java-based prototype implementations of the
command line tools:

* `javatools.jar` - This contains the portion of the prototype that is written in
  Java. The source code is at
  [https://github.com/xtclang/xvm/tree/master/javatools](https://github.com/xtclang/xvm/tree/master/javatools) 
* `javatools_turtle.xtc` - This contains the `mack.xtclang.org` module that provides the bottom
  turtle type in the infinitely recursive type system. The source code is located at
  [https://github.com/xtclang/xvm/tree/master/javatools_turtle](https://github.com/xtclang/xvm/tree/master/javatools_turtle)
* `javatools_bridge.xtc` - This contains the `_native.xtclang.org` module that supports the
  injection of functionality from the Java prototype into a running Ecstasy application.
  The source code is located at
  [https://github.com/xtclang/xvm/tree/master/javatools_bridge](https://github.com/xtclang/xvm/tree/master/javatools_bridge)

Note: Requires Java 17 or later.