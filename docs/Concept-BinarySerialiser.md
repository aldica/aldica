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

In Alfresco cache values, prime candidates for dynamic value substitution are the immutable entities managed by the `QNameDAO`, `MimetypeDAO`, `LocaleDAO` and `EncodingDAO`. It can almost be guaranteed that values managed by those DAOs will always be cached in the `immutableEntitySharedCache` after having been looked up / used only once, that this cache is sufficiently sized to never evict a single entry, that no values are ever modified or deleted, invalidating entries, and that the underlying cache is set to be fully replicated on all members participating in the distributed grid. Based on these DAOs, the cache data structures for aspects, node properties in general and `MLText` values in particular, as well as content data can be significantly trimmed down by substituting the complex values / Strings with the corresponding database IDs. In the case of a qualified name, this would substitute an object of e.g. 58 bytes (using `cm:name` as a reference) with a simple 4 byte ID.

**Important**:
- Using dynamic value substitution and reconstituting the value by performing an ID-based lookup will significantly skew hit/miss statistics of secondary caches.
- Failure to ensure proper sizing and cache mode of secondary caches can result in performance degradation due to the execution of nested, incremental DB lookups, which in case of complex value structures such as `propertiesSharedCache` or `aspectsSharedCache` may involve multiple lookups per cache entry - it is recommended that secondary caches should be fully replicated  or at least partitioned, and the aldica module by default specifies the most critical caches as replicated / partitioned by default

### Value Type Substitution

In very specific cases, a default Java value class may be excessively expensive in its serial form compared to the effective value it represents in most if not all of its instances. This may be the result of custom serialisation in the value class, which does not differentiate which fragments of the value structure actually need to be written, or a complex runtime value structure that could be streamlined.
One such example is the default `java.util.Locale`, which uses custom serialisation hook methods to always write out 5 distinct textual value fields and an integer (dummy) hash code for backward compatibility support. Since most `Locale` instances used in Alfresco will at most specify the language and region elements, and all JVMs used in a distributed Alfresco cache grid are of the same version without the need for backwards compatibility, 4 out of 6 fields are completely redundant. Because Alfresco out-of-the-box provides type converter to turn a String into a `Locale` it is way more efficient to write any `Locale` instances as short Strings (using `toString()`) and simply convert those back upon de-serialisation. Not accounting for structural / metadata overhead, this replacement can already save 20 out of 26 byte on a simple language-only value just by eliminating the unnecessary values (4 non-null, zero-length Strings with 4-byte length values and a 4-byte integer for the dummy hash code).

### Raw Serial Form

Optimisations in this last category simply aim to write out the value structure of an object in a raw serial form with as little as possible of structural metadata. Essentially, the serial form is written out in an unbroken stream of data without separator bits, and it is up to the (de-)serialisation logic on how to handle / interpret the data. Instead of metadata about class field names, the structural data of an object merely includes an offset / length of the serial form to delimit the overall object.

Writing the value structure of an object in raw serial form without metadata offers very limited improvements for objects with very few fields, and can even be less efficient on objects with only one or two fields. But on objects with complex internal structures, e.g. with nested collections / maps, it can potentially provide decent improvements (single digit percentage range) when collection / map metadata can also be avoided to be written in the serial form. In case object structures use primitive integers or Strings, either regular or using special value patterns, the raw serial form also allows for advanced optimisations like using variable length primitives for parts of the data structure, which may turn the raw serial form into a net improvement even on objects with only single fields.
Using this type of optimisation prevents any use of the Ignite-capabilities to index and modify cached data without deserialisation, but these specific capabilities are not used at all in the current state of the aldica module.

**Important**:
The use of variable length integers reduces the available value space by using some of the bits of integer values to determine how many bytes / shorts are used to represent the overall value. By default, values are written in consecutive byte fragments with the top bit of each fragment (except for the last) indicating whether another fragment follows. For long values, this reduces the value space to 2^57 bit, for integer values to 2^29 bit. In any use cases where user-provided values need to be considered, safe guards have been put in place to switch to regular static length integer values for values that absolutely need the full 64/32 bit value space.
With regards to database IDs, the limited value space is still sufficient to support any possible non-negative ID unless more than 450.000 DB entries are created every millisecond for 10 years without interruption. While Alfresco does not use negative DB IDs on any supported database by default, a separate configuration option has been put in place to toggle support in case manual manipulation in the database has added such values.
File sizes in `ContentData` and similar objects will never be negative and while Alfresco supports storing contents of arbitrary length, it is typically highly unlikely that file sizes reach or exceed 128 PiB. For the unexpected case that this may indeed be the case in an Alfresco system, the aldica module provides two options to enable support for file sizes between 128 PiB and 2 EiB (using short fragments), or even exceeding 2 EiB (using static length long).
The length / size fragment of String values will also be written as a variable length integer by any custom serialiser that aldica provides. The reduced value space still allows for String values up to 0.5 GiB in size, which should be more than any reasonable in-memory, cached String will ever need to be.

While an alternative approach would have been possible which uses the first 2 or 3 bits in the first value fragment to encode the number of overall fragments to support larger value spaces, e.g. support 2^61 for long values with the first 3 bit encoding the number of additional byte fragment, this approach was not chosen as it would not optimise small values as well and large values requiring the expanded value space are generally less likely to be used. In case of String values, the alternative approach would have been less efficient for short Strings from 64 and 128 byte in length, and would require Strings between 2 and 4 MiB in length to be more efficient. Database IDs between 32 and 64, as well 8192 and 16384 would have been less efficient, and the alternative approach would have been more efficient for the first time only when an entry with an ID from 2^28 to 2^29 existed in an Alfresco table.

## aldica-provided Optimisations

The aldica module currently adds the following, flexibly configurable serialisation options / optimisations with its Repository-tier Ignited-backed module:

- `org.alfresco.repo.cache.TransactionalCache$CacheRegionKey`: structure flattening (eliminating reconstructible hash code) and raw serial form
- `org.alfresco.repo.cache.lookup.CacheRegionKey`: structure flattening (eliminating reconstructible hash code), well-known value substitution and raw serial form
- `org.alfresco.repo.cache.lookup.CacheRegionValueKey`: structure flattening (eliminating reconstructible hash code), well-known value substitution and raw serial form
- `org.alfresco.service.cmr.repository.StoreRef`: well-known value substitution (on the `protocol` field) and raw serial form
- `org.alfresco.service.cmr.repository.NodeRef`: structure flattening (inline `StoreRef` fields), well-known value substitution (full `StoreRef` or only `protocol` field of `StoreRef`) and raw serial form
- `org.alfresco.service.namespace.QName`: structure flattening (eliminating reconstructible `hashCode` and optional `prefix`), well-known value substitution (namespace URIs) and raw serial form
- `org.alfresco.service.cmr.repository.MLText`: dynamic value substitution (substituting `Locale` instances with ID), value type substitution for `Locale` and raw serial form
- `org.alfresco.repo.domain.node.ContentDataWithId` / `org.alfresco.repo.domain.node.ContentData`: dynamic value substitution (substituting `Mimetype`, `Encoding` and/or `Locale` instances with ID), value type substitution for `Locale` and raw serial form
- `org.alfresco.repo.module.ModuleVersionNumber`: effectively no optimisation (even slightly less efficient), but custom serialiser provided to override `Externalizable` behaviour and support raw serial form
- `org.aldica.repo.ignite.cache.NodeAspectsCacheSet`: dynamic value substitution (substituting `QName` instances with ID) and raw serial form
- `org.aldica.repo.ignite.cache.NodePropertiesCacheMap`: dynamic value substitution (substituting `QName` and `ContentDataWithId` instances with ID) and raw serial form

The optimisations can be configured on a high-level via `alfresco-global.properties` and the following properties:

- `aldica.core.binary.optimisation.enabled` - global enablement flag for non-trivial optimisations - defaults to `true`
- `aldica.core.binary.optimisation.useRawSerial` - global enablement flag for using raw serial form - defaults to `aldica.core.binary.optimisation.enabled`
- `aldica.core.binary.optimisation.useIdsWhenReasonable` - global enablement flag for using dynamic value substitution for any entities backed by the Alfresco `immutableEntityCache` (`QName`, `Locale`, `Mimetype`, `Encoding`) - defaults to `aldica.core.binary.optimisation.enabled`
- `aldica.core.binary.optimisation.useIdsWhenPossible` - global enablement flag for using dynamic value substitution for all types of complex entities that can be resolved via secondary caches - defaults to `aldica.core.binary.optimisation.enabled`
- `aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers` - global enablement flag for using variable length numeric primitives where possible - defaults to `aldica.core.binary.optimisation.useRawSerial`
- `aldica.core.binary.optimisation.rawSerial.handleNegativeIds` - global compatibility flag for enabling extra handling of potentially negative database IDs, only relevant if `aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers` is used and some IDs in the Alfresco database have been set with negative values (non-standard, typically requires manual manipulation of DB) - defaults to `false`
- `aldica.core.binary.optimisation.rawSerial.handle128PiBFileSizes` - global compatibility flag for handling content URL / file sizes of 128 PiB or higher, only relevant if `aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers` is used - defaults to `false`
- `aldica.core.binary.optimisation.rawSerial.handle2EiBFileSizes` - global compatibility flag for handling content URL / file sizes of 2 EiB or higher, only relevant if `aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers` is used - defaults to `false`

In addition, for each type-specific listing at the start of this section, there are low-level detailed configuration properties, which mostly inherit default settings from the high-level properties, unless a specific setting makes more sense than whatever is configured in 80+% of the cases.

## Note on Complementary / Fallback Optimisations

Many of the binary serialisation optimisations provided by the aldica module are built in such a way that they can either complement each other or act as fallback optimisations. Examples for this are:

- if `Locale` key in an `MLText` is not being substituted with its database ID (due to `aldica.core.binary.optimisation.useIdsWhenReasonable` being disabled), it will still be written as a plain String instead of a costly `Locale` instance
- a `QName` key in a node properties map being substituted with its database ID will be further optimised in raw serial form by having the database ID written as a variable length integer, likely cutting the cost for most `QName` instances to 25% or less (7-14 bit integer written as 8-16 bit values instead of 64 bit long)