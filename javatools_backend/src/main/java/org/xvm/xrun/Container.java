package org.xvm.xrun;

import org.xvm.xtc.ModPart;
import org.xvm.xtc.ClzBldSet;
import org.xvm.XEC;
import org.xvm.xec.XClz;
import org.xvm.xec.XRunClz;
import java.lang.reflect.Constructor;


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
  }

  
  public Object invoke(String xrun, String[] args) {
    // Build the java module class.
    if( _mod._jclz==null )
      ClzBldSet.do_compile(_mod,_mod);
    // Make an instanceof the Java version of the XTC module
    XClz jobj;
    try {
      Constructor<XClz> con = _mod._jclz.getConstructor(Container.class);
      jobj = con.newInstance(new NativeContainer());
    } catch( Exception ie ) {
      throw XEC.TODO();
    }
    
    // Find method in the java module class.
    if( !xrun.equals("run") ) throw XEC.TODO();
    XRunClz runner = (XRunClz)jobj;
    // Invoke the method with args, in another thread.
    if( args==null ) runner.run();
    else             runner.main(args);
    // Return a Joinable on the running thread.
    return null;
  }
}
