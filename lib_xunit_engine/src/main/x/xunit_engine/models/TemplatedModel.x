/**
 * A model that has a template applied to it.
 */
interface TemplatedModel<ModelType extends Model> {
    /**
     * The model to use as a template.
     */
    @RO ModelType template;
}
