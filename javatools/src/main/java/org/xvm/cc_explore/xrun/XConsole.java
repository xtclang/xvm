package org.xvm.cc_explore.xrun;

/**
*/
public class XConsole {

  public void print( String s, boolean no_newline ) {
    System.out.print(s);
    if( !no_newline )
      System.out.println();
  }
}
