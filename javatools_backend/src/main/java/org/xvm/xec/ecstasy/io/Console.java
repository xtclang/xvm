package org.xvm.xec.ecstasy.io;

import org.xvm.xec.ecstasy.Appenderchar;

public interface Console {

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

  default public void print( long i, boolean no_newline ) {
    System.out.print(i);
    if( !no_newline )
      System.out.println();
  }

}
