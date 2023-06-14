package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

class MainContainer extends Container {
  final ModPart _mod;           // Starting module
  MainContainer( Container par, XEC.ModRepo repo, ModPart mod ) {
    super(par,repo);
    _mod = mod;
  }

  // TODO: Returns something to join on, or some kind of error
  void invoke(String xrun, String[] args) {
    MethodPart meth = _mod.method(xrun);
    if( meth == null ) return; // TODO: Existing XEC prints to sys.err and returns
    // Here I need to build/cache/load a java class for the "_mod",
    // Then invoke a Java executor Runnable task (F/J lite task?) for the method+args.
    
    throw XEC.TODO();
  }
  
}
