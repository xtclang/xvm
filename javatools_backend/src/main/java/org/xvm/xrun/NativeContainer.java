package org.xvm.xrun;

import org.xvm.XEC;

public class NativeContainer extends Container {

  final NativeConsole _console;
  
  public NativeContainer() {
    super(null,null);
    _console = new NativeConsole();
  }

  @Override public NativeConsole console() { return _console; }
  @Override public NativeTimer   timer  () { return new NativeTimer(); }
  
  // Initialize default things into the container?
  void init() {
  }

}
