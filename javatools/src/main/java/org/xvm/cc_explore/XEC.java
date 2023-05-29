package org.xvm.cc_explore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
  Exploring XTC bytecodes.  Fakes as a XEC runtime translator to JVM bytecodes.
 */
public class XEC {
  public static void main( String[] args ) throws IOException {
    // Check for sane file
    if( args.length!= 1 ) {
      System.err.println("Usage: xec file.xec");
      System.exit(1);
    }

    if( args[0].endsWith(".xec") ) {
      System.err.println("Error: "+args[0]+" does not end with .xec");
      System.exit(1);
    }

    // Load whole XTC file into buf
    File f = new File(args[0]);    
    byte[] buf = new byte[(int)f.length()];
    try( FileInputStream fis = new FileInputStream(f) ) { fis.read(buf); } finally {}
    
    System.out.println(""+buf.length);
    
  }
}
