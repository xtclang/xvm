package org.xvm.xrun;

import org.xvm.ModPart;
import org.xvm.XEC;
import org.xvm.xclz.XRunClz;

/**
   A Container: a self-contained set of types
*/
public abstract class Container {
  final Container _par;         // Parent container
  final ModPart _mod;           // Main module
  public Console console() { return _par.console(); }
  
  Container( Container par, ModPart mod ) {
    _par = par;
    _mod = mod;
    if( _mod==null ) return;    // Native Container has no mod
    // Here I need to build/cache/load a java class for the "_mod",
    XRunClz xclz = _mod.xclz();
    xclz.run();
    System.exit(0);
  }

  // TODO:
  // Top-level design thinking:
  
  // Move an instanceof XClzCompiler here.  Need a private Java ClassLoader per
  // Container; might as well have the XCompiler support here.  The private
  // classLoaders means XTC Container classes are unrelated to each other and
  // can e.g. name-shadow.  All modules in a container should have a "blank"
  // or "simple" top-level name, since they already have a private classLoader
  // and so already have namespace.
  
  // Example: module "FizzBuzz" gets full Java class name "XTC.JFizzBuzz", and
  // contains the module constructor in the Java statics.
  
  // Classes within a module get the module as a file-path element in their name.
  // Example: class "FizzBuzz" has file Java name "XTC.JFizzBuzz.JFizzBuzz".

  
}
