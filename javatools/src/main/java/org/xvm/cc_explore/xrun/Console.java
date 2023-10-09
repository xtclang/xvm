package org.xvm.cc_explore.xrun;

/**
*/
public class Console {

  public void print( String s, boolean no_newline ) {
    System.out.print(s);
    if( !no_newline )
      System.out.println();
  }

  public void print( int i, boolean no_newline ) {
    System.out.print(i);
    if( !no_newline )
      System.out.println();
  }

  public void print( long i, boolean no_newline ) {
    System.out.print(i);
    if( !no_newline )
      System.out.println();
  }
}
