# Migration Epic: Move Analysis to the Forensics Analytics Service

## Background

The previous `forensics_tracing` plugin mixed build-tool integration with local
scanner, analysis, rule-generation, and persistence logic. Gradle tasks and
Maven goals could scan source code locally, derive control-flow information,
generate Byteman rules, and write a local H2 analysis store.

The active repository boundary is different: Gradle and Maven plugin entry
points are thin adapters that submit build context to the Forensics Analytics
server over gRPC. Legacy local analysis code may remain in this repository only
as migration-audit inventory until its server-side migration is proven.

## Scope

This epic tracks the migration of source scanning, semantic analysis, rule
generation, analysis storage, and runtime analytics ownership out of the
build-tool plugin and into the Forensics Analytics service.

The plugin remains responsible for collecting build identity, collecting
configured submission metadata, creating gRPC requests, sending payloads, and
failing with useful messages when the server rejects a request.

The server remains responsible for forensic analysis decisions, semantic
enrichment, generated artifacts, storage, indexing, reporting, and downstream
analytics behavior.

## Goals

1. Keep Gradle and Maven plugins as thin gRPC submission adapters.
2. Submit build identity and diagnostic payloads through the checked-in gRPC
   ingestion contract.
3. Keep source parsing, source analysis, rule generation, semantic enrichment,
   persistent analysis stores, and generated analysis packages out of active
   plugin behavior.
4. Retain legacy local analysis packages only as migration-audit inventory until
   server-side migration evidence exists.
5. Maintain test coverage for Gradle task wiring, Maven Mojo mapping, gRPC
   request mapping, response handling, and architecture boundaries.

## Non-Goals

- Reintroducing local analysis behavior through Gradle or Maven entry points.
- Adding REST orchestration to this repository.
- Generating local Byteman output files from active plugin tasks.
- Adding fallback task names, compatibility wrappers, or alternate RPC behavior
  without an explicit verified task.
- Moving server-side domain decisions into the plugin.

## Current gRPC Contract

The checked-in client contract is:

```text
src/main/proto/forensic_ingestion.proto
```

The active service is:

```text
ForensicIngestionService
```

The standard submission flow is:

1. `StartAnalysisSession`
2. client-streaming `UploadAnalysisData`
3. `CompleteAnalysisSession`

If a submission fails after a session starts, the client may call
`AbortAnalysisSession` with a failure reason.

### RPC Summary

| RPC | Type | Purpose |
| --- | --- | --- |
| `StartAnalysisSession` | unary | Opens a server-side ingestion session for the current build. |
| `UploadAnalysisData` | client streaming | Uploads one or more `AnalysisDataEnvelope` payloads for the session. |
| `CompleteAnalysisSession` | unary | Marks the session as complete after upload succeeds. |
| `AbortAnalysisSession` | unary | Aborts the session when the client cannot finish submission. |

### Contract Excerpt

```proto
service ForensicIngestionService {
  rpc StartAnalysisSession(StartAnalysisSessionRequest)
      returns (StartAnalysisSessionResponse);

  rpc UploadAnalysisData(stream AnalysisDataEnvelope)
      returns (UploadAnalysisDataResponse);

  rpc CompleteAnalysisSession(CompleteAnalysisSessionRequest)
      returns (CompleteAnalysisSessionResponse);

  rpc AbortAnalysisSession(AbortAnalysisSessionRequest)
      returns (AbortAnalysisSessionResponse);
}
```

## Submitted Context

The plugin sends build and plugin identity data that lets the server associate
uploaded payloads with a build, module, repository, and plugin version.

| Message | Key fields |
| --- | --- |
| `BuildIdentity` | `project_id`, `repository_url`, `branch_name`, `commit_hash`, `build_id`, `scan_timestamp` |
| `ModuleIdentity` | `module_name`, `module_path` |
| `PluginIdentity` | `plugin_name`, `plugin_version` |
| `AnalysisPayloadDescriptor` | `payload_id`, `kind`, `content_type`, `attributes` |

The active plugin currently submits a lightweight build-context diagnostic
payload. Server-owned analysis payload formats must be added through explicit
contract changes and tests.

## Gradle Plugin Flow

1. The consuming build applies plugin ID `de.burger.forensics.btmgen`.
2. The build configures the `forensicsTracing` extension.
3. The user runs `submitForensicsAnalysis`, or the aggregate task
   `forensicsAnalyze`.
4. The task creates a gRPC client for the configured server host, port,
   plaintext setting, and deadline.
5. The task starts an ingestion session, uploads the build-context payload, and
   completes the session.
6. The task fails with a descriptive Gradle exception if the server rejects the
   request or the gRPC call fails.

## Maven Plugin Flow

1. The consuming build configures the Maven plugin with prefix `forensics`.
2. The user runs `forensics:submit-analysis`.
3. The Mojo maps Maven project metadata and configured parameters into the same
   gRPC submission model used by Gradle.
4. The Mojo starts an ingestion session, uploads the build-context payload, and
   completes the session.
5. The Mojo fails with a descriptive `MojoExecutionException` if submission
   fails.

Legacy Maven goals may remain as thin submission aliases only when explicitly
required. They must not perform local analysis, semantic import, BTM generation,
or local store cleanup as active plugin behavior.

## Migration Work Items

### Plugin Repository

1. Keep Gradle task classes limited to task input declaration, build metadata
   collection, request creation, and gRPC submission.
2. Keep Maven Mojo classes limited to parameter mapping, request creation, and
   gRPC submission.
3. Keep gRPC client classes free of Gradle and Maven API dependencies.
4. Maintain tests near the affected Gradle, Maven, and gRPC packages.
5. Maintain ArchUnit rules that protect the build-tool boundary.
6. Document legacy local analysis code as migration-audit inventory only.

### Server Repository

1. Own repository checkout, source scanning, semantic analysis, rule generation,
   analysis storage, reporting, and runtime analytics.
2. Implement server-side ingestion handling for the payload kinds accepted by
   `forensic_ingestion.proto`.
3. Define any result-delivery API explicitly in the server contract before the
   plugin consumes it.
4. Provide migration evidence before legacy local analysis inventory is removed
   from this repository.

### Tests and Verification

1. Use in-process gRPC servers for client tests unless a real server integration
   test is explicitly requested.
2. Verify server rejection handling for Gradle tasks and Maven Mojos.
3. Verify request mapping against `forensic_ingestion.proto`.
4. Verify architecture rules for Gradle, Maven, and gRPC package boundaries.
5. Run the repository quality gate after behavior changes.

## Open Questions

- Authentication and transport security: production submissions need a verified
  TLS and credential model.
- Payload schema evolution: additional payload kinds require proto updates,
  generated class regeneration, and matching tests.
- Result delivery: generated artifacts are server-owned; any plugin download
  behavior needs a separate, explicit server contract.
- Retry and resume behavior: interrupted client-streaming uploads need a defined
  server-side policy before client retries are added.
- Migration evidence: legacy local analysis packages should remain until the
  server-side replacement can be proven.

## References

- [gRPC Java basics][grpc-java-basics]
- [Open Liberty gRPC streaming guide][open-liberty-grpc]

[grpc-java-basics]: https://grpc.io/docs/languages/java/basics/
[open-liberty-grpc]: https://openliberty.io/guides/grpc-intro.html
