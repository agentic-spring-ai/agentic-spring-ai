# Provider Serializer Extension Point

Status: Design approved for a future implementation. This document does not
change runtime serializer behavior.

## Objective

Remove provider-specific DeepSeek code and reflective ZhiPu AI code from Graph
Core without changing existing checkpoint formats. Core continues to own the
serializer contracts and the Spring AI message serializers that are independent
of a model provider. Agentic Spring AI Extensions owns provider adapters.

The implementation must support both object-stream and Jackson state
serializers, provide explicit non-Spring and Spring Boot registration paths, and
fail deterministically when registrations conflict.

## Current Coupling

Graph Core currently contains two provider-specific paths:

- `SpringAIStateSerializer` conditionally loads DeepSeek and ZhiPu AI message
  classes and registers provider serializers. The DeepSeek serializer has a
  direct compile-time dependency; the ZhiPu AI serializer invokes the provider
  API reflectively.
- `SpringAIJacksonStateSerializer` registers provider-specific Jackson handlers
  and type mappings. It persists the type ids `DEEPSEEK_ASSISTANT` and
  `ZHI_PU_AI_ASSISTANT`.

Consequently, `agentic-spring-ai-graph-core` still declares the optional
`spring-ai-deepseek` dependency and owns code that changes whenever a provider
message API changes.

## Target Ownership

Core owns provider-neutral extension contracts under
`io.github.agentic.spring.ai.graph.serializer.spi`:

- `ObjectStreamStateSerializerCustomizer`
- `JacksonStateSerializerCustomizer`
- `ObjectStreamSerializerRegistry`
- `JacksonSerializerRegistry`

Core also owns construction of `SpringAIStateSerializer` and
`SpringAIJacksonStateSerializer`. It does not use classpath scanning,
`ServiceLoader`, reflection, or Spring to discover provider support.

Extensions owns one plain Java adapter artifact per provider:

| Artifact | Package | Responsibility |
| --- | --- | --- |
| `agentic-spring-ai-graph-serializer-deepseek` | `io.github.agentic.spring.ai.graph.serializer.deepseek` | DeepSeek object-stream serializer, Jackson handler, and both customizers |
| `agentic-spring-ai-graph-serializer-zhipuai` | `io.github.agentic.spring.ai.graph.serializer.zhipuai` | ZhiPu AI object-stream serializer, Jackson handler, and both customizers |

Each adapter declares its provider library directly and is managed only by the
Extensions BOM. Core and the Core BOM must not depend on either adapter or the
Extensions BOM.

If Spring Boot convenience modules are added, they are also owned by Extensions
and named `agentic-spring-ai-starter-graph-serializer-deepseek` and
`agentic-spring-ai-starter-graph-serializer-zhipuai`. A starter depends on its
plain adapter and contributes customizer beans only. It must not replace a
user-defined `StateSerializer`, mutate `StateGraph.DEFAULT_JACKSON_SERIALIZER`,
or install a process-wide registry.

## Core Contracts

The implementation should add contracts equivalent to the following API. The
exact generic bounds may be adjusted during implementation, but the ownership,
ordering, and validation semantics are binding.

```java
public interface ObjectStreamStateSerializerCustomizer {

    String id();

    default int order() {
        return 0;
    }

    void customize(ObjectStreamSerializerRegistry registry);
}

public interface JacksonStateSerializerCustomizer {

    String id();

    default int order() {
        return 0;
    }

    void customize(JacksonSerializerRegistry registry);
}
```

The object-stream registry exposes one atomic registration operation:

```java
<T> void register(Class<T> javaType, Serializer<T> serializer);
```

The Jackson registry exposes an atomic message-type registration operation:

```java
<T> void registerMessageType(
        String serializedTypeId,
        Class<T> javaType,
        JsonSerializer<T> serializer,
        JsonDeserializer<T> deserializer);
```

Atomic registration prevents a Jackson handler from being installed without
its matching persisted type mapping. Core-only handlers that do not require a
persisted type id use a separate Core-internal registration path and are not
part of the provider SPI.

Both `SpringAIStateSerializer` and `SpringAIJacksonStateSerializer` gain additive
constructors or builders that accept customizers. Existing constructors and
public methods remain present during the compatibility phase. Registries become
immutable after serializer construction; registration after first use is not
supported.

## Ordering and Conflict Rules

Registration is deterministic:

1. Core registrations are applied first in a fixed, documented order.
2. Customizers are sorted by ascending `order()` and then lexicographically by
   their non-blank `id()`.
3. Customizer ids must be unique. Duplicate ids fail serializer construction.
4. A Java class may be registered only once in each serializer registry.
5. A Jackson serialized type id may be registered only once.
6. Duplicate Java classes or serialized type ids throw an
   `IllegalStateException` that names both conflicting customizer ids. Order
   never selects a winner and no registration silently replaces another.

Object-stream lookup first uses an exact Java-class registration. Assignable
fallbacks then follow the frozen registration order, preserving the current
generic `Message` behavior while removing `HashMap` iteration as a source of
nondeterminism. Provider message classes use exact registrations, so they take
precedence over the generic `Message` serializer.

The implementation must use serializer-local registries. Static mutable
registries would make test order and application-context order observable and
are prohibited.

## Persisted Id and Format Contract

The provider adapters preserve the existing identifiers exactly:

- DeepSeek Jackson type id: `DEEPSEEK_ASSISTANT`
- ZhiPu AI Jackson type id: `ZHI_PU_AI_ASSISTANT`

They also preserve the current JSON fields and null behavior for `text`,
`toolCalls`, `reasoningContent`, and metadata. No alias, case normalization, or
new provider-prefixed id is introduced.

Object-stream adapters preserve the current field order and nullable encoding:

1. text
2. metadata
3. tool calls
4. reasoning content

The stream continues to identify the runtime provider message by its Java class.
Moving the serializer implementation class to Extensions must not change the
bytes written for an equivalent message.

## Registration Paths

### Non-Spring applications

Registration is explicit at serializer construction:

```java
var support = new DeepSeekSerializerSupport();

var objectStreamSerializer = SpringAIStateSerializer.builder(OverAllState::new)
        .customizer(support.objectStreamCustomizer())
        .build();

var jacksonSerializer = SpringAIJacksonStateSerializer.builder(OverAllState::new)
        .objectMapper(new ObjectMapper())
        .customizer(support.jacksonCustomizer())
        .build();
```

Applications pass the resulting `StateSerializer` to `StateGraph`, agent
builders, and persistence builders as they do today. Merely placing an adapter
jar on the classpath does not modify a serializer.

### Spring Boot applications

The provider starter contributes the provider's object-stream and Jackson
customizers as beans, guarded by `@ConditionalOnClass` for the provider message
class and `@ConditionalOnMissingBean` for the specific customizer bean.

Applications explicitly choose the serializer flavor and construct the
`StateSerializer` bean from the ordered customizer beans. For Jackson, the
application passes a dedicated `ObjectMapper` or a copy of the application
mapper because the state serializer configures visibility and type handling.

```java
@Bean
StateSerializer graphStateSerializer(
        ObjectProvider<JacksonStateSerializerCustomizer> customizers,
        ObjectMapper objectMapper) {
    return SpringAIJacksonStateSerializer.builder(OverAllState::new)
            .objectMapper(objectMapper.copy())
            .customizers(customizers.orderedStream().toList())
            .build();
}
```

The application injects this bean into graph or agent construction. Existing
no-argument `StateGraph` constructors do not consult the Spring context and are
not silently changed by a starter.

## Missing Provider or Customizer Behavior

- If a provider jar is absent, its starter contributes no customizer bean. Core
  serializer construction and all provider-neutral message handling continue to
  work.
- If a provider jar is present but its customizer is not registered, provider
  support is not enabled. The configurable serializer path must fail before
  writing a provider-specific `AssistantMessage` subtype rather than serialize
  it through the generic handler and lose provider fields.
- Reading a persisted provider type without its adapter fails with a targeted
  unknown-type or missing-provider exception that includes the persisted type id
  and the required Extensions artifact. It must not deserialize the payload as a
  generic `AssistantMessage`.
- Object-stream reads still require the provider message class because that
  class name is part of the existing Java stream. A missing class remains a
  `ClassNotFoundException`, augmented with the adapter coordinate where
  possible.
- During the 2.2 coexistence phase, legacy constructors retain their current
  conditional behavior. The new configurable construction path uses the rules
  above. The stricter missing-customizer behavior becomes the default only at
  the documented breaking-release boundary.

## Compatibility Phases

### 2.2 coexistence

- Add the Core SPI, validated registries, and additive constructors/builders.
- Add provider adapters in Extensions and prove that they write the same
  object-stream bytes and Jackson documents as Core.
- Keep Core's existing provider-specific classes, public methods, conditional
  registration behavior, reflective ZhiPu AI path, and optional
  `spring-ai-deepseek` dependency.
- Keep this serializer SPI outside the 2.1 persistence and Docker release scope;
  the design remains implementation work for 2.2.

This phase is source and binary compatible. Applications may opt into the new
adapters without changing existing checkpoints.

### 2.x migration

- Update active examples and documentation to construct serializers with the
  Extensions customizers.
- Deprecate public Core provider handlers with replacements naming the new
  artifacts and classes. Package-private implementation classes need no public
  deprecation contract but remain until the removal release.
- Run both legacy and extension implementations against the same golden
  checkpoint corpus in both directions.

### 3.0 removal

- Remove DeepSeek and ZhiPu AI serializer implementations and reflective class
  names from Core.
- Remove `spring-ai-deepseek` from the Graph Core POM and dependency management
  reachable from Core.
- Keep provider-neutral SPI, type registry, generic message serializers, and
  existing `StateSerializer` integration points in Core.
- Make the validated configurable construction path the default. Provider users
  add the matching Extensions adapter explicitly.

Removing currently public provider-specific Core handler types is a source and
binary breaking change and therefore occurs only in 3.0.

## Persisted-Data and Rollback Compatibility

No checkpoint rewrite or schema migration is permitted. Golden fixtures created
by the legacy Core serializers must be readable by the Extensions adapters, and
fixtures created by the Extensions adapters must be readable by the legacy Core
serializers.

Rollback from the new adapter path to the 2.2 Core implementation is supported
by preserving Java provider class names, object-stream field order, Jackson type
ids, JSON fields, and null behavior. A rollback deployment must include the same
provider library required to resolve provider message classes. Core and
Extensions versions should be rolled back together when their SPI major versions
differ; persisted data itself requires no rollback transformation.

## Required Tests

Before Core removes `spring-ai-deepseek`, all of the following gates must pass:

1. Core unit tests with DeepSeek and ZhiPu AI jars absent prove provider-neutral
   serializers construct, serialize, and deserialize supported Core types.
2. Core dependency-tree and bytecode/string scans show no DeepSeek or ZhiPu AI
   classes, reflective provider class names, or provider artifacts in Graph Core.
3. Object-stream golden tests compare legacy and adapter bytes for null and
   non-null text/reasoning, metadata, no tool calls, and multiple tool calls.
4. Jackson golden tests compare legacy and adapter JSON trees and assert the
   exact `DEEPSEEK_ASSISTANT` and `ZHI_PU_AI_ASSISTANT` type ids.
5. Bidirectional persisted-data tests read legacy-written checkpoints with the
   adapters and adapter-written checkpoints with the legacy Core serializers.
6. Nested list, map, array, and top-level provider-message round trips pass for
   both serializer families.
7. Registry tests prove deterministic order independent of bean discovery order,
   exact-class precedence, duplicate customizer-id rejection, duplicate
   Java-class rejection, and duplicate serialized-type-id rejection.
8. Missing-provider and missing-customizer tests prove fail-fast behavior and
   verify that provider-specific fields are never silently discarded.
9. Spring Boot context tests cover provider present, provider absent, user
   customizer override, both provider starters together, and a user-defined
   `StateSerializer` remaining authoritative.
10. Source and binary compatibility checks pass for the 2.2 additive phase; the
    3.0 report lists each intentionally removed public Core provider type.
11. Core and Extensions full tests, format, checkstyle, license, package, and
    dependency-boundary checks pass with Core installed before Extensions.

## Release Order

For every implementation phase, publish Core first and then publish compatible
Extensions adapters against that exact Core release. Core may document the
adapter coordinates but must remain buildable and usable without Extensions.
