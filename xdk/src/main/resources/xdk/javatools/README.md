This directory contains the Java-based prototype implementations of the
command line tools:

* `xvm.jar` - This contains the portion of the prototype that is written in
  Java. The source code is at
  [https://github.com/xtclang/xvm/tree/master/src](https://github.com/xtclang/xvm/tree/master/src) 
* `xvm.xtc` - This contains the `_native` module that supports the injection
  of functionality from the Java prototype into a running Ecstasy
  application. The source code is located at
  [https://github.com/xtclang/xvm/tree/master/xsrc/_native](https://github.com/xtclang/xvm/tree/master/xsrc/_native)

Note: Requires Java 17 or later.