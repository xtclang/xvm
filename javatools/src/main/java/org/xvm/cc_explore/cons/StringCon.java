package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;
import java.io.IOException;

/**
  Exploring XEC Constants
 */
public class StringCon extends Const {
  final String _str;
  public StringCon( FilePart X ) throws IOException {
    _str = X.utf8();
  }
  @Override public void resolve( CPool pool ) {}
}
