package org.xvm.xtc;

import org.xvm.util.SB;
import org.xvm.xec.XClz;

// XTC Module is roughly above-equals a Java Package.
// The Java code is in the top-level XEC directory.
// Single-class modules are the same as normal modules from Java's POV.
// 
public class XModBuilder {  
  public final ModPart _mod;
  public XModBuilder( ModPart mod ) { _mod = mod; }

  // Use the normal Clz builder, with the ModPart as the XTC class
  Class<XClz> jmod( ) {
    return new XClzBuilder(_mod,_mod,new SB(),true).jclz();
  }  
}
