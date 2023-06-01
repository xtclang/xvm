package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FileComponent;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class ClassCon extends NamedCon {
  public ClassCon( FileComponent X ) throws IOException {
    super(X);
  }
  @Override public void resolve( CPool pool ) { }  
}
