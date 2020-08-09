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

Writing the value structure of an object in raw serial form without metadata offers very limited improvements for objects with very few fields, and can even be less efficient on objects with only one or two fields. But on objects with complex internal structures, e.g. with nested collections / maps, it can potentially provide decent improvements (single digit percentage range) when collection / map metadata can also be avoided to be written in the serial form. In case object structures use primitive integer values (`integer`/`long` types) or Strings, either regular or using special value patterns, the raw serial form also allows for advanced optimisations like using variable length primitives for parts of the data structure, which may turn the raw serial form into a net improvement even on objects with only single fields. Furthermore, in any objects containing content URLs, this form of serialisation can be used to optimise any URLs using the Alfresco default format based on date, time, UUID and well known protocol / file name suffix, writing the various elements in compressed binary instead of plain textual form.
Using this type of optimisation prevents any use of the Ignite-capabilities to index and modify cached data without de-serialisation, but these specific capabilities are not used at all in the current state of the aldica module.

#### Variable Length Integer Considerations

The use of variable length integers reduces the available value space by using some of the bits of integer values to determine how many bytes / shorts are used to represent the overall value. Values are written in consecutive fragments of a primitive integer type 2 orders of (binary) magnitude smaller, i.e. `long` values are written using `short` fragments, `integer` values using `byte` fragments. The top two bits of the first value fragment is used to encode how many *additional* fragments are part of the same value. For signed long values, this reduces the value space to 2^61 bit, for signed integer values to 2^29 bit. Since some use cases of integer values always deal with non-negative values, e.g. the length of a `String` or the size of a `Collection`, there is also support for writing/reading unsigned values. In any use cases where user-provided values need to be considered (primarily node property values), safe guards have been put in place to switch to regular static length integer values for values that absolutely need the full 64/32 bit value space.
With regards to database IDs, the limited value space is still sufficient to support any possible non-negative ID unless more than 14.6 million DB entries are created every millisecond for 10 years without interruption. While Alfresco does not use negative DB IDs on any supported database by default, a separate configuration option has been put in place to toggle support in case manual manipulation in the database has added such values.
File sizes in `ContentData` and similar objects will never be negative and while Alfresco supports storing contents of arbitrary length, it is typically highly unlikely that file sizes reach or exceed 4 PiB. For the unexpected case that this may indeed be the case in an Alfresco system, the aldica module provides an option to enable support for file sizes exceeding 4 EiB (using static length long).
The length / size fragment of String values will also be written as a variable length integer by any custom serialiser that aldica provides when variable length integers are enabled. The reduced value space still allows for String values up to 1 GiB in size, which should be more than any reasonable in-memory, cached String will ever need to be.

While the use of variable length integer values reduces the size of values in the cache - depending on the specific comparison with reductions up into the low double-digit percentage range - it introduces serialisation overhead which can limit the throughput on highly concurrent processes with intensive cache access. Micro-benchmarks have been conducted on multiple variants of writing variable length integer values and compared against the default Ignite static length long serialisation. These benchmarks can be found among the test classes of the Repository module in the `VariableLengthLongMicroBenchmark` class. These benchmarks have been helpful in evolving our own approach to this feature, showing how our initially chosen variant was sub-optimal in terms of the relative computing cost, and providing us with a basis to compare various alternatives to pick one specifically targeted for low read overhead. The currently chosen variant has the following cost characteristics:

| Measurement | Metric | aldica variable length | Ignite default |
| :--- | :--- | ---: | ---: |
| Read | average | 4.4 ns/op | 1.2 ns/op |
| Read | std. dev. | 1.8 ns/op | 1 ns/op |
| Read | 90th percentile | 7 ns/op | 2 ns/op |
| Read | 80th percentile | 6 ns/op | 2 ns/op |
| Read | 60th percentile | 4 ns/op | 2 ns/op |
| Read | Maximum | 13 ns/op | 9 ns/op |
| Write | average | 8 ns/op | 1.3 ns/op |
| Write | std. dev. | 2.2 ns/op | 0.8 ns/op |
| Write | 90th percentile | 11.1 ns/op | 2 ns/op |
| Write | 80th percentile | 9 ns/op | 1 ns/op |
| Write | 60th percentile | 7 ns/op | 1 ns/op |
| Write | Maximum | 19 ns/op | 6 ns/op |

All values were derived from the CSV output of the micro-benchmark, processed via LibreOffice, and excluding the first 50 result rows (JVM warmup / JIT optimisation stabilisation) from consideration.

**Note**: Micro-benchmark results can differ significantly between test runs and test environments (JVM versions / CPU architectures), as well as JIT compiler used within the JVM (e.g. Hotspot vs. Graal). While we show specific values reported in our benchmark runs, the relative differences within each run should be the primary focus.

## aldica-provided Optimisations

The aldica module currently adds the following, flexibly configurable serialisation options / optimisations with its Repository-tier Ignited-backed module:

- `org.alfresco.repo.cache.TransactionalCache$CacheRegionKey`: structure flattening (eliminating reconstructible hash code) and raw serial form
- `org.alfresco.repo.cache.TransactionalCache$ValueHolder`: structure flattening (substituting well known sentinel values; inlining simple numerical/textual values) and raw serial form
- `org.alfresco.repo.cache.lookup.CacheRegionKey`: structure flattening (eliminating reconstructible hash code), well-known value substitution and raw serial form
- `org.alfresco.repo.cache.lookup.CacheRegionValueKey`: structure flattening (eliminating reconstructible hash code), well-known value substitution and raw serial form
- `org.alfresco.repo.domain.node.NodeVersionKey`: raw serial form
- `org.alfresco.repo.domain.node.TransactionEntity`: raw serial form
- `org.alfresco.repo.domain.node.StoreEntity`: structure flattening (inlining store protocol / identifier) and raw serial form
- `org.alfresco.repo.domain.node.NodeEntity`: structure flattening (eliminating constant state, eliminating irrelevant state, eliminiate state only used for SQL ORM, inlining auditable properties, handling auditable dates as timestamp, not text) and raw serial form
- `org.alfresco.repo.domain.node.ChildAssocEntity`: structure flattening (eliminiate state only used for SQL ORM) and raw serial form
- `org.alfresco.repo.domain.permissions.AclEntity`: structure flattening (aggregating booleans and small value space enum into bitmask, handling the ACL (UU)ID as binary) and raw serial form
- `org.alfresco.repo.domain.contentdata.ContentUrlEntity`: structure flattening (inlining content URL key), dynamic value substitution (substituting `Mimetype`, `Encoding` and/or `Locale` instances with ID), value type substitution for `Locale` and raw serial form
- `org.alfresco.repo.domain.propval.PropertyUniqueContextEntity`: raw serial form
- `org.alfresco.service.cmr.repository.StoreRef`: well-known value substitution (on the `protocol` field) and raw serial form
- `org.alfresco.service.cmr.repository.NodeRef`: structure flattening (inline `StoreRef` fields), well-known value substitution (full `StoreRef` or only `protocol` field of `StoreRef`) and raw serial form
- `org.alfresco.service.namespace.QName`: structure flattening (eliminating reconstructible `hashCode` and optional `prefix`), well-known value substitution (namespace URIs) and raw serial form
- `org.alfresco.service.cmr.repository.MLText`: dynamic value substitution (substituting `Locale` instances with ID), value type substitution for `Locale` and raw serial form
- `org.alfresco.repo.domain.node.ContentDataWithId` / `org.alfresco.repo.domain.node.ContentData`: dynamic value substitution (substituting `Mimetype`, `Encoding` and/or `Locale` instances with ID), value type substitution for `Locale` and raw serial form
- `org.alfresco.repo.module.ModuleVersionNumber`: effectively no optimisation (even slightly less efficient), but custom serialiser provided to override `Externalizable` behaviour and support raw serial form
- `org.aldica.repo.ignite.cache.NodeAspectsCacheSet`: dynamic value substitution (substituting `QName` instances with ID) and raw serial form
- `org.aldica.repo.ignite.cache.NodePropertiesCacheMap`: dynamic value substitution (substituting `QName` and `ContentDataWithId` instances with ID) and raw serial form
- `org.alfresco.repo.security.authentication.InMemoryTicketComponentImpl#Ticket`: structure flattening (inlining duration and expiry) and raw serial form
- `org.alfresco.service.cmr.repository.datatype.Duration`: raw serial form
- `org.alfresco.repo.security.authentication.RepositoryAuthenticationDao$CacheEntry`: raw serial form
- `org.alfresco.service.cmr.action.ExecutionDetails`: structure flattening (aggregating boolean flags into bitmask, inlining user detail fields) and raw serial form

The optimisations can be configured on a high-level via `alfresco-global.properties` and the following properties:

- `aldica.core.binary.optimisation.enabled` - global enablement flag for non-trivial optimisations - defaults to `true`
- `aldica.core.binary.optimisation.useRawSerial` - global enablement flag for using raw serial form - defaults to `aldica.core.binary.optimisation.enabled`
- `aldica.core.binary.optimisation.useIdsWhenReasonable` - global enablement flag for using dynamic value substitution for any entities backed by the Alfresco `immutableEntityCache` (`QName`, `Locale`, `Mimetype`, `Encoding`) - defaults to `aldica.core.binary.optimisation.enabled`
- `aldica.core.binary.optimisation.useIdsWhenPossible` - global enablement flag for using dynamic value substitution for all types of complex entities that can be resolved via secondary caches - defaults to `aldica.core.binary.optimisation.enabled`
- `aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers` - global enablement flag for using variable length numeric primitives where possible - defaults to `aldica.core.binary.optimisation.useRawSerial`
- `aldica.core.binary.optimisation.rawSerial.handleNegativeIds` - global compatibility flag for enabling extra handling of potentially negative database IDs, only relevant if `aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers` is used and some IDs in the Alfresco database have been set with negative values (non-standard, typically requires manual manipulation of DB) - defaults to `false`
- `aldica.core.binary.optimisation.rawSerial.handle4EiBFileSizes` - global compatibility flag for handling content URL / file sizes of 4 EiB or higher, only relevant if `aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers` is used - defaults to `false`
- `aldica.core.binary.optimisation.rawSerial.useOptimisedContentURL` - global compatibility flag for using a size-optimised serialisation format for content URL - defaults to `aldica.core.binary.optimisation.useRawSerial`

In addition, for each type-specific listing at the start of this section, there are low-level detailed configuration properties, which mostly inherit default settings from the high-level properties, unless a specific setting makes more sense than whatever is configured in 80+% of the cases.

## Note on Complementary / Fallback Optimisations

Many of the binary serialisation optimisations provided by the aldica module are built in such a way that they can either complement each other or act as fallback optimisations. Examples for this are:

- if `Locale` key in an `MLText` is not being substituted with its database ID (due to `aldica.core.binary.optimisation.useIdsWhenReasonable` being disabled), it will still be written as a plain String instead of a costly `Locale` instance
- a `QName` key in a node properties map being substituted with its database ID will be further optimised in raw serial form by having the database ID written as a variable length integer, likely cutting the cost for most `QName` instances to 25% (positive values with up to 14 significant bits written as unsigned 16 bit values instead of signed 64 bit long)