/**
 * Evolver API.
 */
interface Evolver {
    /**
     * Transform an old db into a new one.
     *
     * @throws an exception if the db can not be evolved
     */
    void evolve();
}