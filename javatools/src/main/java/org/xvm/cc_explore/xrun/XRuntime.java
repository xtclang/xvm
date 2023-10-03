package org.xvm.cc_explore.xrun;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
   Support for thread pools, perhaps other system and runtime services.  One
   per JVM, per physical hardware.  Shared amongst all Containers and services.
*/
public abstract class XRuntime {
  //  The executor for XVM services.
  public static final ThreadPoolExecutor _executorXVM;

  // The executor for XVM services.
  public static final ThreadPoolExecutor _executorIO;
  
  static {
    int parallelism = Integer.parseInt(System.getProperty("xvm.parallelism", "0"));
    if (parallelism <= 0)
      parallelism = java.lang.Runtime.getRuntime().availableProcessors();

    ThreadGroup groupXVM = new ThreadGroup("XVM");
    ThreadFactory factoryXVM = r -> {
      Thread thread = new Thread(groupXVM, r);
      thread.setDaemon(true);
      thread.setName("XvmWorker@" + thread.hashCode());
      return thread;
    };

    // TODO: replace with a fair scheduling based ExecutorService; and a concurrent blocking queue
    _executorXVM = new ThreadPoolExecutor(parallelism, parallelism, 0, TimeUnit.SECONDS,  new LinkedBlockingQueue<>(), factoryXVM);

    ThreadGroup groupIO = new ThreadGroup("IO");
    ThreadFactory factoryIO = r -> {
      Thread thread = new Thread(groupIO, r);
      thread.setDaemon(true);
      thread.setName("IOWorker@" + thread.hashCode());
      return thread;
    };

    _executorIO = new ThreadPoolExecutor(parallelism, 1024, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), factoryIO);
  }

  
  /** Submit ServiceContext work for eventual processing by the runtime.
   *  @param task the task to process   */
  static protected void submitService(Runnable task) { _executorXVM.submit(task); }

  /** Submit IO work for eventual processing by the runtime.
   *  @param task the task to process    */
  protected static void submitIO(Runnable task) { _executorIO.submit(task); }

  public static void start() {}

  public static void shutdownXVM() {
    _executorIO .shutdown();
    _executorXVM.shutdown();
  }

  // $tmp expression wrapper, to allow side effects in the arguments and still
  // have a java expression
  public static boolean $t(long x) { return true; }
  public static boolean $t(Object x) { return true; }
}
