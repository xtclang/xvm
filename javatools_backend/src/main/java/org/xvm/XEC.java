package org.xvm;

import org.xvm.xrun.*;
import org.xvm.xtc.FilePart;
import org.xvm.xtc.ModPart;
import org.xvm.xtc.Part;
import org.xvm.util.S;

import java.io.FileFilter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;

/**
   Exploring XTC bytecodes.  Fakes as a XEC runtime translator to JVM bytecodes.

 */
public class XEC {
  // Local project root
  public static final String ROOT = "org.xvm";
  // All the generated code is generated here:
  public static final String XCLZ = ROOT+".xec";

  // Every thread runs in the context of some Container, which manages runtime
  // & CPU gas, memory, resources (such as i/o and access to the file system).
  public static final ThreadLocal<Container> CONTAINER = new ThreadLocal<>();

  // The Repository of all code
  public static ModRepo REPO;

  // The main Ecstasy module
  public static ModPart ECSTASY;
  
  // Main Launcher.
  // Usage: (-L path)* [-M main] file.xtc args  
  public static void main( String[] args ) throws IOException {

    // Parse options
    // Parse (-L path)* libs
    String[] libs = libs(args);
    // Check for alternate run method
    String xrun = "run";
    int ndx = libs.length*2;
    if( ndx < args.length && args[ndx].equals("-M") ) throw XEC.TODO();
    // File to run
    String xtc = xtc(ndx++,args);
    // Arguments
    String[] xargs = args(ndx,args);

    
    REPO = new ModRepo();
    // Load XTC file into repo
    ModPart mod = REPO.load(xtc);
    // Load XDK
    for( String lib : libs ) REPO.load(lib);
    // Link the repo
    REPO.link();
    
    // Start the thread pool up
    XRuntime.start();

    // Start the native container.  Top of the container tree.
    NativeContainer N = new NativeContainer();
    // Start the initial container
    MainContainer M = new MainContainer(N,mod);
    CONTAINER.set(M);
    
    /*Joinable J=*/M.invoke(xrun,xargs); // Returns something to join against
    //J.join();
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


  // Module Repository: Mapping from module name Strings to ModParts.
  public static class ModRepo extends HashMap<String,ModPart> {
    // Load a single file or directory of files.  Return a single module or null.
    ModPart load( String s ) throws IOException { return load(new File(s)); }
    ModPart load( File f ) throws IOException {
      // Check for file already parsed
      ModPart mod = get(f.toString());
      if( mod != null ) return mod;
      // Recursively load directories
      if( f.isDirectory() ) {
        for( File file : f.listFiles(ModulesOnly) )
          load(file);
        return null;            // Null for directories
      } else {
        byte[] buf = Files.readAllBytes(f.toPath()); // The only IO, might throw here
        FilePart file = new FilePart(buf,f.toString()); // Parse the entire file, drops buffer after parsing
        mod = file._mod;        // Extract main module
        put(mod._name,mod);     // Installed under module name
        put(f.toString(),mod);  // Installed under file   name
        if( S.eq(mod._name,"ecstasy.xtclang.org") ) ECSTASY = mod;
        return mod;             // Return single module for single file
      }
    }
    // Filter to readable XTC named files only
    static final FileFilter ModulesOnly = file ->
      file.getName().length() > 4 && file.getName().endsWith(".xtc") &&
      file.exists() && file.isFile() && file.canRead() && file.length() > 0;

    // Link.  Replace *Con references to *Part references.
    public static final HashMap<Part,Part> VISIT = new HashMap<>();
    void link() {
      VISIT.clear();
      // For all modules in repo
      for( ModPart mod : values() )
        // Get the parent's set of child modules and link them against the repo
        mod._par.link(this);
    }
  }

  public static RuntimeException TODO() { return TODO("TODO"); }
  public static RuntimeException TODO(String msg) { return new RuntimeException(msg); }

}
