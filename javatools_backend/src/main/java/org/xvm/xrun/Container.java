package org.xvm.xrun;

import org.xvm.XEC;
import org.xvm.xec.XRunClz;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.collections.AryString;
import org.xvm.xtc.ClzBldSet;
import org.xvm.xtc.JavaC;
import org.xvm.xtc.ModPart;

import java.lang.reflect.Constructor;


/**
   A Container: a self-contained set of types
*/
public abstract class Container {
  final Container _par;         // Parent container
  final ModPart _mod;           // Main module
  public NativeConsole console() { return _par.console(); }
  public NativeTimer timer() { return _par.timer(); }

  Container( Container par, ModPart mod ) {
    _par = par;
    _mod = mod;
  }


  public Object invoke( String xrun, String[] args) {
    // Build the java module class.
    if( JavaC.XFM.klass(_mod)==null )
      ClzBldSet.do_compile(_mod);
    // Make an instanceof the Java version of the XTC module
    XTC jobj;
    try {
      Constructor<XTC> con = JavaC.XFM.klass(_mod).getConstructor();
      jobj = con.newInstance();
    } catch( NoSuchMethodException nsme ) {
      throw XEC.TODO(); // No top-level run method?
    } catch( Exception ie ) {
      throw XEC.TODO();
    }

    // Find method in the java module class.
    if( !xrun.equals("run") ) throw XEC.TODO();
    XRunClz runner = (XRunClz)jobj;
    // Invoke the method with args, in another thread.
    if( args==null ) runner.run();
    else             runner.run(new AryString(1.,args));
    // Return a Joinable on the running thread.
    return null;
  }
}
