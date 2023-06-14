package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.Const;

import java.io.FileFilter;
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

    
    ModRepo repo = new ModRepo();
    // Load XTC file into repo
    ModPart mod = repo.load(xtc);
    // Load XDK
    for( String lib : libs ) repo.load(lib);
    // Link the repo
    repo.link();
    
    // See that we got a main module
    if( mod==null ) {
      if( explicitModuleFile(xtc) )  TODO();
      else TODO();
    }
    if( mod==null ) throw new IllegalArgumentException("Unable to load module "+ xtc);

    // Start the thread pool up
    XRuntime.start();

    // Start the initial container.
    Container C = new Container(repo,mod);

    System.err.println("TODO: Loaded "+xtc+" fine, Execution continues");
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


  // Module Repository: Mapping from module name Strings to ModParts.
  static class ModRepo extends HashMap<String,ModPart> {
    // Load a single file or directory of files.  Return a single module or null.
    ModPart load( String s ) throws IOException { return load(new File(s)); }
    ModPart load( File f ) throws IOException {
      if( f.isDirectory() ) {
        for( File file : f.listFiles(ModulesOnly) )
          load(file);
        return null;            // Null for directories
      } else {
        byte[] buf = Files.readAllBytes(f.toPath()); // The only IO, might throw here
        FilePart file = new FilePart(buf); // Parse the entire file
        ModPart mod = file.getMod();       // Extract main module
        put(mod.name(),mod);
        return mod;             // Return single module for single file
      }
    }
    // Filter to readable XTC named files only
    static final FileFilter ModulesOnly = file ->
      file.getName().length() > 4 && file.getName().endsWith(".xtc") &&
      file.exists() && file.isFile() && file.canRead() && file.length() > 0;

    // Link.
    // Each module has a parent FilePart; the parent FilePart has a set of
    // child modules (including the original module).  Some of the child
    // modules might be Fingerprints: unlinked module names that must appear in
    // the repo.  Replace fingerprints with the primary module.
    void link() {
      // For all modules in repo
      for( ModPart mod : values() )
        // Get the parent's set of child modules and link them against the repo
        ((FilePart)mod._par).link(this);
    }
  }

  public static RuntimeException TODO() { return new RuntimeException("TODO"); }
}
