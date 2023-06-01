package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
   Class component
 */
class ClassComponent extends Component {
  ClassComponent( Component par, int nFlags, IdCon id, CondCon cond, FileComponent X ) throws IOException {
    super(par,nFlags,id,cond,X);
    // No other bits to parse
  }
}
