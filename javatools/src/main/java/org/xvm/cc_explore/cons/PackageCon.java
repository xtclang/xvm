package org.xvm.cc_explore;

import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class PackageConst extends NamedConst {
  PackageConst( XEC.XParser X ) throws IOException {
    super(X);
  }
  @Override void resolve( CPool pool ) { }  
}
