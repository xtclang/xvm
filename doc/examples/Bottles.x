/**
 * Display the complete lyrics for the song: 99 Bottles of Beer on the Wall. The lyrics follow this
 * form:
 *
 *     99 bottles of beer on the wall
 *     99 bottles of beer
 *     Take one down, pass it around
 *     98 bottles of beer on the wall
 *
 *     98 bottles of beer on the wall
 *     98 bottles of beer
 *     Take one down, pass it around
 *     97 bottles of beer on the wall
 *
 * ... and so on, until reaching 0 (zero). Grammatical support for "1 bottle of beer" is optional.
 */
module Bottles {
    void run() {
        function String(Int) num     = i -> i==0 ? "No" : i.toString();
        function String(Int) bottles = i -> i==1 ? "bottle" : "bottles";

        @Inject Console console;
        for (Int remain : 99..1) {
            console.print($|{num(remain)} {bottles(remain)} of beer on the wall
                           |{num(remain)} {bottles(remain)} of beer
                           |Take one down, pass it around
                           |{num(remain-1)} {bottles(remain-1)} of beer on the wall
                           |
                         );
        }
    }
}