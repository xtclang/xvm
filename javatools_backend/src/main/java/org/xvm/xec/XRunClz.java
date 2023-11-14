package org.xvm.xec;

import org.xvm.xrun.*;

// Some kind of base class for a Java class that implements an XTC Module
public abstract class XRunClz extends XClz implements Runnable {
  public final Container _container;
  public XRunClz( Container container ) { _container = container; }
  public void main( String[] args ) { run(); }
}
