package org.xvm.cc_explore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/**
  Exploring XTC bytecodes.  Fakes as a XEC runtime translator to JVM bytecodes.
 */
public class XEC {
  
  // Main Launcher
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

    // Load whole XTC file into buf, then parse
    byte[] buf = Files.readAllBytes(Path.of(args[0]));
    FilePart file = new FilePart(buf);
    
    TODO();
  }

  public static RuntimeException TODO() { return new RuntimeException("TODO"); }

}
