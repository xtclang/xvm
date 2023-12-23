package org.xvm.xec;

import org.xvm.xrun.*;
import org.xvm.xec.ecstasy.collections.AryString;

// Some kind of base class for a Java class that implements an XTC Module
public abstract class XRunClz extends XTC {
  public final Container _container;
  public XRunClz( Container container ) { _container = container; }
  public XRunClz( Never n ) { _container = null; }
  public void run( AryString args ) { run(); }
  public abstract void run( );
}
