package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;


// Some kind of base class for a Java class that implements an XTC Module
public class XClzBuilder {
  public final ModPart _mod;
  public final SB _sb;
  public XClz _xclz;            // Java class
  
  public XClzBuilder( ModPart mod ) {
    System.err.println("Making XClz for "+mod);
    _mod = mod;
    _sb = new SB();

    // Let's start by assuming if we're here, we're inside the top-level
    // ecstasy package - otherwise we're nested instead the mirror for the
    // containing package.
    assert mod.child("ecstasy",null) instanceof PackagePart;

    // The Java class will extend XClz.
    // The Java class name will be the mangled module class-name.
    String java_class_name = "J"+mod._name;
    jclass_body(java_class_name);

    throw XEC.TODO();
  }

  // Fill in the body of the matching java class
  private void jclass_body( String java_class_name ) {
    _sb.p("public class ").p(java_class_name).p(" {").nl().ii();
    
    // Look for a module init.  This will become the Java <clinit>
    MMethodPart construct = (MMethodPart)_mod._name2kid.get("construct");
    if( construct != null ) {
      _sb.nl();
      _sb.ip("static {").ii();
      jcode(construct);
      _sb.di().ip("}").nl();
      throw XEC.TODO();      
    }

    for( Part part : _mod._name2kid.values() ) {
      if( part instanceof MMethodPart mm ) {
        if( !mm._name.equals("construct") ) {// Already handled module constructor
          throw XEC.TODO();
        }
      } else if( part instanceof PackagePart pp ) {
        // External reference is ok
      } else {
        throw XEC.TODO();
      }
    }
    
    _sb.di().p("}").nl();
  }
  
  // Generate a Java string code this MM
  private String jcode( MMethodPart mm ) {
    MethodPart meth = (MethodPart)mm._name2kid.get(mm._name);
    assert meth._sibling==null;





    
    throw XEC.TODO();      
  }
  
}
