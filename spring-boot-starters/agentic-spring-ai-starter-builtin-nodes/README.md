# Built-in Nodes

## Secure Defaults

The code and document nodes use restrictive defaults. Applications upgrading from earlier releases
must opt in to broader access explicitly.

### Code Execution

`DockerCodeExecutor` disables networking, uses a read-only root filesystem, drops capabilities, and
applies CPU, memory, swap, and PID limits. `maxOutputBytes` defaults to 1 MiB and limits output retained
in the host JVM; it truncates captured output but does not replace the execution timeout. The timeout
still controls how long a process or container may run. When the root filesystem is read-only, `/tmp`
is a writable 64 MiB in-memory filesystem for normal runtime and compiler temporary files. Set
`readOnlyRootFilesystem` to `false` only for trusted images that must write elsewhere in the image.

```java
CodeExecutionConfig config = new CodeExecutionConfig()
    .setDisableNetwork(false) // Only when executed code requires outbound access.
    .setMemoryLimitBytes(512L * 1024L * 1024L)
    .setMemorySwapBytes(512L * 1024L * 1024L)
    .setPidsLimit(256)
    .setMaxOutputBytes(2L * 1024L * 1024L)
    .setTimeout(120);
```

Both Docker and local executors create an isolated child directory under `workDir`. Multiple code
blocks in one `executeCodeBlocks` invocation share that directory, and the directory is deleted when
the invocation finishes. Do not use `workDir` as cross-invocation storage. The local executor runs code
on the host and is only suitable for trusted input.

### Document Extraction

Local documents are restricted to the configured root. Remote documents allow only HTTP and HTTPS,
block private, loopback, link-local, and metadata addresses by default, cap response bytes, and enforce
connect, read, and total elapsed timeouts.

```java
DocumentExtractorNode node = DocumentExtractorNode.builder()
    .localRoot(Path.of("/srv/application-documents"))
    .maxBytes(10L * 1024L * 1024L)
    .totalTimeout(Duration.ofSeconds(60))
    .build();
```

For a trusted internal document service, private-network access can be enabled explicitly:

```java
DocumentExtractorNode node = DocumentExtractorNode.builder()
    .allowPrivateNetworkAccess(true)
    .build();
```

`HttpNode` applies URI and resolved-address preflight checks even with a custom `WebClient`. A custom
client connector must enforce the same policy during connect-time DNS resolution to prevent rebinding.
