package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;
import java.io.IOException;

/**
  Exploring XEC Constants
 */
public class PackageCon extends NamedCon {
  public PackageCon( FilePart X ) throws IOException {
    super(X);
  }
  @Override public void resolve( CPool pool ) { }  
}
