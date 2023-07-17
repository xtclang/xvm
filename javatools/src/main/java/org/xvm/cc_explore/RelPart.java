package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import java.util.Arrays;
  
/**
   Union or Interface composed interface Part
 */
public class RelPart extends ClassPart {
  Part _p1, _p2;
  public RelPart( Part p1, Part p2 ) {
    super(null,make_name(p1._name,p2._name),Part.Format.INTERFACE);
    _p1 = p1;
    _p2 = p2;
  }
  private static String make_name( String n1, String n2 ) {
    if( n1.compareTo(n2) > 0 ) { String tmp = n1; n1 = n2; n2 = tmp; }
    return "["+n1+","+n2+"]";
  }
  
  // Tok, kid-specific internal linking.
  @Override void link_innards( XEC.ModRepo repo ) {
    throw XEC.TODO();
  }
}
