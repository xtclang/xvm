/**
 * There is a fundamental question: Which came first, the chicken or the egg?
 *
 * When a type reflectively recurses upon itself, it will find a temporary (egg) Mapping for the
 * type which has deferred the realization of the actual (chicken) Mapping.
 */
const ChickenOrEggMapping<Serializable>(function Mapping<Serializable>() egg)
        delegates Mapping<Serializable>(chicken)
    {
    private function Mapping<Serializable>() egg;

    private @Lazy Mapping<Serializable> chicken.calc()
        {
        return egg();
        }
    }
