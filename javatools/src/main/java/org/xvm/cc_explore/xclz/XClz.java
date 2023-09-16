package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.xrun.*;


// Some kind of base class for a Java class that implements an XTC Module
public abstract class XClz implements Runnable {
  public final Container _container;
  public XClz( Container container ) { _container = container; }
}
