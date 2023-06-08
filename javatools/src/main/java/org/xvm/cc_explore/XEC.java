package org.xvm.cc_explore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

/**
  Exploring XTC bytecodes.  Fakes as a XEC runtime translator to JVM bytecodes.
 */
public class XEC {
  
  // Main Launcher.
  // Usage: (-L path)* [-M main] file.xtc args  
  public static void main( String[] args ) throws IOException {

    // Parse options
    // Parse (-L path)* libs
    String[] libs = libs(args);
    // Check for alternate main
    int ndx = libs.length*2;
    if( ndx < args.length && args[ndx].equals("-M") ) throw XEC.TODO();
    // File to run
    String xtc = xtc(ndx++,args);
    // Arguments
    String[] xargs = args(ndx,args);

    
    // Load XDK
    ModRepo repo = new ModRepo(libs);
    
    // Load whole XTC file into buf, then parse
    byte[] buf = Files.readAllBytes(Path.of(xtc));
    FilePart file = new FilePart(buf);
    System.err.println("TODO: Loaded "+xtc+" fine, Execution continues");

    // See that we got a main module
    ModPart mod = file.getMod();
    if( mod==null ) {
      if( explicitModuleFile(file._modName) )  TODO();
      else TODO();
    }
    // Got a module, save in repo
    if( mod==null ) throw new IllegalArgumentException("Unable to load module "+ xtc);
    repo.put(mod.name(),mod);

    
    
    TODO();
  }

  // Parse options: Count and gather libs
  private static String[] libs(String[] args) {
    int nlibs = 0;
    for( int i=0; i<args.length; i++ ) {
      if( !args[i].equals("-L") ) break;
      nlibs++; i++;
    }
    String[] libs = new String[nlibs];
    for( int i=0; i<nlibs; i++ )
      libs[i] = args[i*2+1];
    return libs;
  }

  // Parse options: the main file to run
  private static String xtc(int ndx, String[] args) {
    // Bad args
    if( ndx >= args.length) {
      System.err.println("Usage: xec (-L path)* [-M main] file.xtc args");
      System.exit(1);
    }
    
    // File to parse
    String xtc = args[ndx];
    if( !xtc.endsWith(".xtc") ) {
      System.err.println("Error: "+xtc+" does not end with .xtc");
      System.exit(1);
    }
    return xtc;
  }
  
  // Parse options: args to the main file
  private static String[] args(int ndx, String[] args) {
    return Arrays.copyOfRange(args,ndx,args.length);
  }

  
  // True if the name is an explicit Ecstasy source or compiled name.
  protected static boolean explicitModuleFile(String s) { return s.endsWith(".x") || s.endsWith(".xtc"); }


  // Module Repository: Mapping from Strings to ModParts.
  static class ModRepo extends HashMap<String,ModPart> {
    final String[] _libs;       // Path to libs
    ModRepo(String[] libs) { _libs = libs;  }
  }

  public static RuntimeException TODO() { return new RuntimeException("TODO"); }
}
