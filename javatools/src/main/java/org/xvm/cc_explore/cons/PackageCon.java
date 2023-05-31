package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.XEC;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class PackageCon extends NamedCon {
  public PackageCon( XEC.XParser X ) throws IOException {
    super(X);
  }
  @Override public void resolve( CPool pool ) { }  
}
