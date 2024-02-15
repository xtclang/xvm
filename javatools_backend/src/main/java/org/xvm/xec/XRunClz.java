package org.xvm.xec;

import org.xvm.xrun.Never;
import org.xvm.xec.ecstasy.collections.AryString;

// Some kind of base class for a Java class that implements an XTC Module
public abstract class XRunClz extends XTC {
  public XRunClz( Never n ) { }
  public void run( AryString args ) { run(); }
  public abstract void run( );
}
