import collections.ArrayDeque;

import ecstasy.collections.SkiplistMap;

import models.MethodModel;
import models.builderFor;

import resolvers.ClassResolver;
import resolvers.MethodResolver;
import resolvers.PackageResolver;

/**
 * The `DiscoveryEngine` is responsible for discovering all the test `Model`
 * instance that represent test fixtures to be executed.
 */
service DefaultDiscoveryEngine
        implements DiscoveryEngine {

    @Override
    Model[] discover(DiscoveryConfiguration configuration) {
        ArrayDeque<Selector>               selectorQueue = new ArrayDeque();
        Map<UniqueId, ModelBuilder<Model>> buildersById  = new HashMap();

        // ToDo Allow additional resolvers to be added in the config??
        SelectorResolver[] resolvers = [
                new PackageResolver(),
                new ClassResolver(),
                new MethodResolver(),
                ];

        selectorQueue.addAll(configuration.selectors);

        while (Selector selector := selectorQueue.next()) {
            for (SelectorResolver resolver : resolvers) {
                if ((ModelBuilder[] builders, Selector[] selectors) := resolver.resolve(configuration, selector)) {
                    selectorQueue.addAll(selectors);
                    for (ModelBuilder<Model> builder : builders) {
                        buildersById.put(builder.uniqueId, builder);
                    }
                    // the resolver handled the selector so we do not try any other resolvers
                    break;
                }
            }
        }

        Model[] models = new Array();

        // buildersById now contains all discovered Model builders
        // we first need to process all the MethodModel builders then the parents of each and so on up
        OrderedMap<UniqueId, Model[]> modelsByParent = new SkiplistMap();
        Model[]                       emptyModels    = [];
        Map<UniqueId, Model[]>        batch          = new HashMap();

        buildersById.values
                .filter(builder -> builder.ModelType == MethodModel)
                .forEach(builder -> batch.put(builder.uniqueId, emptyModels));

        build(configuration, batch, modelsByParent, buildersById, models);
        batch.clear();

        // modelsByParent now holds all the method models by parent, with the models at the
        // bottom of the hierarchy first in the map.
        // We can work our way top down creating the container models.
        // We can do this in batches by taking all the same length UniqueIds in a batch

        UniqueId key;
        while (key := modelsByParent.first()) {
            Int lastSize = key.size;

            assert Model[] modelsForParent := modelsByParent.get(key);
            batch.put(key, modelsForParent);
            modelsByParent.remove(key);

            while (key := modelsByParent.first()) {
                if (key.size != lastSize) {
                    break;
                }
                assert modelsForParent := modelsByParent.get(key);
                batch.put(key, modelsForParent);
                modelsByParent.remove(key);
            }

            build(configuration, batch, modelsByParent, buildersById, models);
            batch.clear();
        }

        return models.freeze(True);
    }

    /**
     * Process a batch of builders instances.
     *
     * Each entry in the `batch` is processed. The entry's `UniqueId` key is used to locate the `ModelBuilder`
     * in the `buildersById` map, which is then called to build a `Model` with the entry's `Model[]` value as
     * the children of the new `Model`.
     *
     * If there is no `ModelBuilder` in the `buildersById` map for the entry's key, the `Model`s in the entry's
     * `Model[]` value are  added to the `models` array.
     *
     * If the `UniqueId` of the built `Model` has a parent `UniqueId` the `Model` is added to the `modelsByParent`
     * map, using the parent `UniqueId` as the key, otherwise the built `Model` is added to the `models` array.
     *
     * @param configuration   the `DiscoveryConfiguration` to use
     * @param batch           a `Map` of `UniqueId` to child `Model`s to use to call the `ModelBuilder`
     * @param modelsByParent  a `Map` of `Models` keyed by the `Model`s parent `UniqueId`
     * @param buildersById    a `Map` of `ModelBuilder`s keyed by the `UniqueId` of the `Model` to be built
     * @param models          the array of `Model`s to add any new `Model`s to
     */
    private void build(DiscoveryConfiguration             configuration,
                       Map<UniqueId, Model[]>             batch,
                       OrderedMap<UniqueId, Model[]>      modelsByParent,
                       Map<UniqueId, ModelBuilder<Model>> buildersById,
                       Model[]                            models) {

        Future<Model>[] futures = new Array();

        for (Map<UniqueId, Model[]>.Entry entry : batch.entries) {
            ModelBuilder<Model> builder;
            if (builder := buildersById.get(entry.key)) {
                @Future Model futureModel = builder.build^(configuration, entry.value.freeze(True));
                futures.add(&futureModel);
            } else if (builder := builderFor(entry.key)) {
                @Future Model futureModel = builder.build^(configuration, entry.value.freeze(True));
                futures.add(&futureModel);
            } else {
                models.addAll(entry.value);
            }
        }

        for (Future<Model> future : futures) {
            Model model = future.get();
            if (UniqueId parentId := model.uniqueId.parent()) {
                Model[] modelsForId = modelsByParent.getOrDefault(parentId, new Array());
                modelsForId.add(model);
                modelsByParent.put(parentId, modelsForId);
            } else {
                models.add(model);
            }
            buildersById.remove(model.uniqueId);
        }
    }
}