package org.xvm.xrun;

import org.xvm.xtc.MethodPart;
import org.xvm.xtc.ModPart;
import org.xvm.XEC;
import org.xvm.xec.XClz;

// The initial container
public class MainContainer extends Container {
  public MainContainer( Container par, ModPart mod ) {
    super(par,mod);
  }

  // TODO: Returns something to join on, or some kind of error
  public void invoke(String xrun, String[] args) {
    MethodPart meth = _mod.method(xrun);
    if( meth == null ) return; // TODO: Existing XEC prints to sys.err and returns
    // Then invoke a Java executor Runnable task (F/J lite task?) for the method+args.
    System.err.println("Launching "+xrun);

    throw XEC.TODO();
  }

}
