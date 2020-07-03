# Concept: Custom Binary Serialiser

## Ignite Binary Marshaller

Apache Ignite by default uses [a custom binary marshaller](https://apacheignite.readme.io/docs/binary-marshaller) for handling data serialisation in off-heap caches, distributed grid messaging or job execution. This marshaller replaces Java's default `Serializable` and `Externalizable` handling, unless a specific class actually implements one of the serialisation hook methods (e.g. `writeExternal`/`readExternal`) to apply custom serialisation. The serial form emitted by this marshaller allows for modification of individual field values without fully deserialising the entire object, and is also the basis for Ignite's support of SQL-like queries across stored data. It also simplifies aspects of dealing with class hierarchies / metadata, which on average should be more efficient than Java's default serialisation (ignoring custom serialisation via the aforementioned serialisation hooks).

In addition to the custom interface `Binarylizable`, which can be used just like `Serializable` / `Externalizable` to customise the serialisation of any class, Ignite also provides the means to implement global serialisation overrides via `BinarySerializer` registered per class using the `BinaryTypeConfiguration`. This second approach also allows the aldica module to provide serialisation optimisations for generally used Alfresco key and value types.

## Serialisation Optimisation Categories

### Structure Flattening

When serialising complex value objects, a small reduction in memory footprint can be gained by simplifying the structure of the serialised data or eliminating unnecessary fields.
An example for potential simplification of serialised data structures is the Alfresco class for node references, `org.alfresco.service.cmr.repository.NodeRef`. Instances of this class contain another complex value object, the store reference, of type `org.alfresco.service.cmr.repository.StoreRef`, as well as the uid of the node as a simple String. In a default serialisation, the serial form would consist of the class metadata for both the node and the store reference, but by flattening the structure and inlining the fields of the store reference as if they were fields of the node reference, the overhead of having store reference class metadata can be eliminated.
Similarly, instances of the Alfresco class for qualified names, `org.alfresco.service.namespace.QName`, contain four instance members (`namespaceURI`, `localName`, `hashCode`, `prefix`), but only two of those are essential for the identity of the value while the other two are solely for runtime optimisations. As long as such non-essential fields can be recreated during serialisation or initialised as part of the regular use of the object after deserialisation, they can be safely dropped and reduce the footprint of the serialised form.

### Well-Known Value Substitution

In various value classes, components of the value object may typically fall within a very narrow range of values, which may potentially be well-known at development / compile time and not subject to be replaced by dynamic values at runtime most of the time. A prime example of this can again be found with the value class for node references. In any Alfresco system, the store component of the majority of node references comes down to six common values which are known in advance as a result of being initialised by the basic bootstrap of an Alfresco system. These stores are

- `user://alfrescoUserStore`
- `system://system`
- `workspace://lightWeightVersionStore`
- `workspace://version2Store`
- `archive://SpacesStore`
- `workspace://SpacesStore`

Unless multi-tenancy is enabled in Alfresco or custom stores are initialised via code or bootstrapp - both features which only a tiny fraction of installations ever use - virtually all node references will use one of these values as its store component. The serialised size of a node reference can be significantly reduced by substituting the full store reference with a single byte denoting which of these values is used. Constituting an object of 16 to 36 bytes + value structure overhead with a single byte is a substantial reduction in size, considering that a full node reference is about 56 to 76 bytes + value structure overhead in total size without optimisations.

Similarly to node references, a substantial fraction of all qualified names will use a namespace URI from the set of well known Alfresco namespace URIs, most dominantly the Alfresco system and content model namespace URIs. Replacing the 49 bytes of the URI for the Alfresco content model in 50 - 80% of qualified names used (depending on the breadth of custom metadata models) with a single byte can be a huge saving.

In a worst case scenario where all custom values are used in store references or qualified name namespace URIs, the overhead of using a value substitution approach to serialisation can be kept extremely small. ideally, all well-known values should be representeble with a single byte value flag that can be written as a kind of prolog / meta flag in the serialised form of a value object. If - instead of a well-known value - a custom value is encountered during serialisation, the same byte flag can be used to denote this circumstance and the custom value be written into the serialised form as normal. The worst case overhead can thus be limited to about two bytes (including the overhead of having the extra field identifier in the serialised form), which compares favoribly to best case savings of at least 16 up to 47 bytes for the majority of values.

### Dynamic Value Substitution

Similar to substituting well-known, static values within the structure of complex objects, potentially dynamic values may be substituted with a shorter, identifying data element, typically a unique ID, in order to simplify the serialised structure. The substituted value will be reconstituted by performing a lookup with the identifying data upon deserialisation. In this case, a conscious choice has to be made between reducing the memory footprint of the serialised data structure and the cost of reconstituting any substituted values by performing additional lookup operations. Generally speaking, such substitutions should only be performed on value components

- which may be guaranteed to never require database lookups once they have been cached for the first time (e.g. by using sufficiently sized, fully replicated caches without expiration)
- which constitute a substantial fraction of the serialised data structure, either via the size of an individual instance or the accumulated size of many instances

In Alfresco cache values, prime candidates for dynamic value substitution are the immutable entities managed by the `QNameDAO`, `MimetypeDAO`, `LocaleDAO` and `EncodingDAO`. It can almost be guaranteed that values managed by those DAOs will always be cached in the `immutableEntitySharedCache` after having been looked up / used only once, that this cache is sufficiently sized to never evict a single entry, that no values are ever modified or deleted, invalidating entries, and that the underlying cache is set to be fully replicated on all members participating in the distributed grid. Based on these DAOs, the cache data structures for aspects, node properties in general and `MLText` values in particular, as well as content data can be significantly trimmed down by substituting the complex values / Strings with the corresponding database IDs. In the case of a qualified name, this would sustitute an object of e.g. 58 bytes (using `cm:name` as a reference) with a simple 4 byte ID.

**Important**:
- Using dynamic value substitution and reconstituting the value by performing an ID-based lookup will significantly skew hit/miss statistics of secondary caches.
- Failure to ensure proper sizing of secondary caches can result in significant performance degradation due to the execution of nested, incremental DB lookups, which in case of complex value structures such as `propertiesSharedCache` or `aspectsSharedCache` may involve multiple lookups per cache entry

### Serial Form without Structure Metadata

Optimisations in this last category simply aim to write out the value structure of an object with as little as possible of structural metadata, meaning information about field names. Essentially, the serial form is written out in an unbroken stream of data without separator bits, and it is up to the (de-)serialisation logic on how to handle / interpret the data. Instead of metadata about class field names, the structural data of an object merely includes an offset / length of the serial form to delimit the overall object.

Writing the value structure of an object in serial form without metadata offers very limited improvements for objects with very few fields, and can even be less efficient on objects with only one or two fields. But on objects with complex internal structures, e.g. with nested collections / maps, it can potentially provide decent improvements (single digit percentage range) when collection / map metadata can also be avoided to be written in the serial form.
Using this type of optimisation prevents any use of the Ignite-capabilities to index and modify cached data without deserialisation, but these specific capabilities are not used at all in the current state of the aldica module.

## aldica-provided Optimisations

The aldica module currently adds the following, flexibly configurable serialisation options / optimisations with its Repository-tier Ignited-backed module:

- `org.alfresco.repo.cache.TransactionalCache$CacheRegionKey`: structure flattening (eliminating reconstructible hash code) and serial form without structure metadata
- `org.alfresco.repo.cache.lookup.CacheRegionKey`: structure flattening (eliminating reconstructible hash code), well-known value substitution and serial form without structure metadata
- `org.alfresco.repo.cache.lookup.CacheRegionValueKey`: structure flattening (eliminating reconstructible hash code), well-known value substitution and serial form without structure metadata
- `org.alfresco.service.cmr.repository.StoreRef`: well-known value substitution (on the `protocol` field) and serial form without structure metadata
- `org.alfresco.service.cmr.repository.NodeRef`: structure flattening (inline `StoreRef` fields), well-known value substitution (full `StoreRef` or only `protocol` field of `StoreRef`) and serial form without structure metadata
- `org.alfresco.service.namespace.QName`: structure flattening (eliminating reconstructible `hashCode` and optional `prefix`), well-known value substitution (namespace URIs) and serial form without structure metadata
- `org.alfresco.service.cmr.repository.MLText`: dynamic value substitution (substituting `Locale` instances with ID) and serial form without structure metadata
- `org.alfresco.repo.domain.node.ContentDataWithId` / `org.alfresco.repo.domain.node.ContentData`: dynamic value substitution (substituting `Mimetype`, `Encoding` and/or `Locale` instances with ID) and serial form without structure metadata
- `org.alfresco.repo.module.ModuleVersionNumber`: effectively no optimisation (even slightly less efficient), but custom serialiser provided to override `Externalizable` behaviour and suport serial format without structure metadata
- `org.aldica.repo.ignite.cache.NodeAspectsCacheSet`: dynamic value substitution (substituting `QName` instances with ID) and serial form without structure metadata
- `org.aldica.repo.ignite.cache.NodePropertiesCacheMap`: dynamic value substitution (substituting `QName` and `ContentDataWithId` instances with ID) and serial form without structure metadata

The optimisations can be configured on a high-level via `alfresco-global.properties` and the following properties:

- `aldica.core.binary.optimisation.enabled` - global enablement flag for non-trivial optimisations - defaults to `true`
- `aldica.core.binary.optimisation.useRawSerial` - global enablement flag for using raw serial form without structure metadata - defaults to `aldica.core.binary.optimisation.enabled`
- `aldica.core.binary.optimisation.useIdsWhenReasonable` - global enablement flag for using dynamic value substitution for any entities backed by the Alfresco `immutableEntityCache` (`QName`, `Locale`, `Mimetype`, `Encoding`) - defaults to `aldica.core.binary.optimisation.enabled`
- `aldica.core.binary.optimisation.useIdsWhenPossible` - global enablement flag for using dynamic value substitution for all types of complex entities that can be resolved via secondary caches, no matter the cost / overhead - defaults to `false`

In addition, for each type-specific listing at the start of this section, there are low-level detailed configuration properties, which mostly inherit default settings from the high-level properites, unless a specific setting makes more sense than whatever is configured in 80+% of the cases.  