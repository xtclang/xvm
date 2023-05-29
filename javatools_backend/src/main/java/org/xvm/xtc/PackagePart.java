package org.xvm.xtc;

import org.xvm.xtc.cons.CondCon;
import org.xvm.xtc.cons.PackageCon;

/**
   Package part
 */
public class PackagePart extends ClassPart {
  PackagePart( Part par, int nFlags, PackageCon id, CondCon cond, CPool X ) {
    super(par,nFlags,id,cond,X,Part.Format.PACKAGE);
  }  
}
