package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.AryInt;

/**
 * Represents an Ecstasy module version.
 */
public class Version implements Comparable<Version> {
  private static final String[] PREFIXS = {"CI", "Dev", "QC", "alpha", "beta", "rc"};

  final protected String _s;
  final protected int[]  _ints;
  final protected String _build;
  
  public Version( String s ) {
    _s = s;
    int len = s.length(), ix = 0;
    AryInt ints = new AryInt();
    String build = null;

    // Parse Version numbers of the form "1.2.3.4"
    while( ix < len && CPool.isDigit(s.charAt(ix)) ) {
      int n = 0;
      while( ix < len && CPool.isDigit(s.charAt(ix)) )
        n = n * 10 + (s.charAt(ix++) - '0');
      ints.push(n);
      if( ix == len ) break;
      if( s.charAt(ix) == '.' ) {  ix++;  continue; }
      if( s.charAt(ix) == '-' ) {  ix++;  break;    }
      throw bad(s);
    }

    // Prefix e.g. "-alpha" or "-rc", required to get the next parts
    if( ix < len ) {
      int old = ix;
      for( int i=0; i<PREFIXS.length; i++ ) {
        String pre = PREFIXS[i];
        if( match(s,ix,pre) ) {
          ints.push(i-PREFIXS.length);
          ix += pre.length();
          break;
        }
      }
      if( old==ix ) throw XEC.TODO(); // Not found
    }

    // Optional suffix, requires a prefix.
    int n = 0, old=ix;
    while( ix < len && CPool.isDigit(s.charAt(ix)) )
      n = n * 10 + (s.charAt(ix++) - '0');
    if( old!=ix ) ints.push(n);

    // Optional trailing build string; requires a prefix.
    if( ix < len ) {
      if( s.charAt(ix)!='+' ) throw bad(s);
      build = s.substring(ix+1);
    }

    _ints = ints.asAry();
    _build = build;
  }
  private static IllegalArgumentException bad(String s) {
    return new IllegalArgumentException("illegal version: "+s);
  }
  
  @Override public int compareTo(Version v) { throw XEC.TODO(); }
  
  private boolean match(String s, int off, String pre) {
    return s.regionMatches(true, off, pre, 0, pre.length());
  }
  
}
