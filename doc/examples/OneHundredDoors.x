/**
 * There are 100 doors in a row that are all initially closed. You make 100 passes by the doors. The
 * first time through, visit every door and toggle the door (if the door is closed, open it; if it
 * is open, close it). The second time, only visit every 2nd door (door #2, #4, #6, ...), and toggle
 * it. The third time, visit every 3rd door (door #3, #6, #9, ...), etc, until you only visit the
 * 100th door. Answer the question: what state are the doors in after the last pass? Which are open,
 * which are closed?
 *
 * From: [https://rosettacode.org/wiki/100_doors]
 */
module OneHundredDoors {
    void run() {
        Boolean[] doors = new Boolean[100];
        for (Int pass : 0 ..< 100) {
            for (Int door = pass; door < 100; door += 1+pass) {
                doors[door] = !doors[door];
            }
        }

        @Inject Console console;
        console.print($|open doors: {doors.mapIndexed((d, i) -> d ? i+1 : 0)
                       |                  .filter(i -> i > 0)}
                     );
    }
}