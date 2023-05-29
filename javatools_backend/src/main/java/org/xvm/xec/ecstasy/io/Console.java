package org.xvm.xec.ecstasy.io;

import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.XTC;

public interface Console {

  default public void print( XTC s, boolean no_newline ) {
    System.out.print(s.toString());
    if( !no_newline )
      System.out.println();
  }

  default public void print( String s, boolean no_newline ) {
    System.out.print(s);
    if( !no_newline )
      System.out.println();
  }

  default public void print( int i, boolean no_newline ) {
    System.out.print(i);
    if( !no_newline )
      System.out.println();
  }

  default public void print( boolean i, boolean no_newline ) {
    System.out.print(i);
    if( !no_newline )
      System.out.println();
  }

  default public void print( long i, boolean no_newline ) {
    System.out.print(i);
    if( !no_newline )
      System.out.println();
  }

}
