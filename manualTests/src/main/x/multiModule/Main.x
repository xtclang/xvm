/**
 * The Main module for a very simple multi-module test.
 *
 * To compile and run (assuming working directory is .../xvm/manualTests)
 *
 *      xtc -L build -o build src/main/x/multiModule/Main.x src/main/x/multiModule/Lib.x
 *      xec -L build Main
 */
module Main {
    package lib import Lib;

    @Inject Console console;

    void run() {
        console.print(lib.greeting());
    }
}
