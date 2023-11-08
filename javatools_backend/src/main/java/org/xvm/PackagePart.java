package org.xvm;

import org.xvm.cons.*;

/**
   Package part
 */
public class PackagePart extends ClassPart {
  PackagePart( Part par, int nFlags, PackageCon id, CondCon cond, CPool X ) {
    super(par,nFlags,id,cond,X,Part.Format.PACKAGE);
  }  
}
