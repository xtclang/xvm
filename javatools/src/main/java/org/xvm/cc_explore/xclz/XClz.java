package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.ModPart;
import org.xvm.cc_explore.XEC;


// Some kind of base class for a Java class that implements an XTC class
public abstract class XClz {

  public static XClz make( ModPart mod ) {
    System.err.println("Making XClz for "+mod);
    throw XEC.TODO();
  }
}
