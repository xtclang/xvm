/**
 * A model that has a template applied to it.
 */
interface TemplatedModel<ModelType extends Model> {

    @RO ModelType template;
}