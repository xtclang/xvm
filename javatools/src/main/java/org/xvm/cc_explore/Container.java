package org.xvm.cc_explore;

import org.xvm.cc_explore.ModPart;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.xclz.XClz;

/**
   A Container: a self-contained set of types
*/
public abstract class Container {
  final Container _par;         // Parent container
  final ModPart _mod;           // Main module
  
  Container( Container par, ModPart mod ) {
    _par = par;
    _mod = mod;
    if( _mod==null ) return;    // Native Container has no mod
    // Here I need to build/cache/load a java class for the "_mod",
    XClz xclz = _mod.xclz();
    throw XEC.TODO();
  }
  
}
