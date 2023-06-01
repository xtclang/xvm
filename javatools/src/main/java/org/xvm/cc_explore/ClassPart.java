package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

import java.io.IOException;

/**
   Class component
 */
class ClassPart extends Part {
  ClassPart( Part par, int nFlags, IdCon id, CondCon cond, FilePart X ) throws IOException {
    super(par,nFlags,id,cond,X);
    // No other bits to parse
  }
}
