package de.burger.forensics.plugin.btmgen.grpc;

import com.google.protobuf.ByteString;
import de.burger.forensics.analytics.ingestion.v1.AnalysisDataEnvelope;
import de.burger.forensics.analytics.ingestion.v1.AnalysisPayloadDescriptor;
import de.burger.forensics.analytics.ingestion.v1.AnalysisPayloadKind;
import de.burger.forensics.analytics.ingestion.v1.BuildIdentity;
import de.burger.forensics.analytics.ingestion.v1.CompleteAnalysisSessionRequest;
import de.burger.forensics.analytics.ingestion.v1.ForensicIngestionServiceGrpc;
import de.burger.forensics.analytics.ingestion.v1.IngestionStatus;
import de.burger.forensics.analytics.ingestion.v1.ModuleIdentity;
import de.burger.forensics.analytics.ingestion.v1.PluginIdentity;
import de.burger.forensics.analytics.ingestion.v1.StartAnalysisSessionRequest;
import de.burger.forensics.analytics.ingestion.v1.UploadAnalysisDataResponse;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class GrpcForensicIngestionClient implements ForensicIngestionClient {
    private final ManagedChannel channel;
    private final Duration deadline;
    private final boolean ownsChannel;

    public static GrpcForensicIngestionClient connect(
            String host,
            int port,
            boolean plaintext,
            Duration deadline
    ) {
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(host, port);
        if (plaintext) {
            builder.usePlaintext();
        }
        return new GrpcForensicIngestionClient(builder.build(), deadline, true);
    }

    public GrpcForensicIngestionClient(ManagedChannel channel, Duration deadline) {
        this(channel, deadline, false);
    }

    private GrpcForensicIngestionClient(ManagedChannel channel, Duration deadline, boolean ownsChannel) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.ownsChannel = ownsChannel;
        if (deadline.isZero() || deadline.isNegative()) {
            throw new IllegalArgumentException("deadline must be positive");
        }
    }

    @Override
    public ForensicsSubmissionResult submit(ForensicsSubmission submission) {
        Objects.requireNonNull(submission, "submission");
        var blocking = ForensicIngestionServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS);
        var start = blocking.startAnalysisSession(startRequest(submission));
        ensureStatus(start.getStatus(), IngestionStatus.INGESTION_STATUS_ACCEPTED, "start analysis session");
        UploadAnalysisDataResponse upload = uploadPayloads(start.getSessionId(), submission);
        ensureStatus(upload.getStatus(), IngestionStatus.INGESTION_STATUS_ACCEPTED, "upload analysis data");
        var completed = blocking.completeAnalysisSession(CompleteAnalysisSessionRequest.newBuilder()
                .setSessionId(start.getSessionId())
                .build());
        ensureStatus(completed.getStatus(), IngestionStatus.INGESTION_STATUS_COMPLETED, "complete analysis session");
        return new ForensicsSubmissionResult(
                completed.getSessionId(),
                completed.getStatus().name(),
                completed.getMessage(),
                upload.getReceivedItems());
    }

    @Override
    public void close() {
        if (!ownsChannel) {
            return;
        }
        channel.shutdown();
        try {
            if (!channel.awaitTermination(deadline.toMillis(), TimeUnit.MILLISECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }

    private UploadAnalysisDataResponse uploadPayloads(String sessionId, ForensicsSubmission submission) {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<UploadAnalysisDataResponse> response = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        StreamObserver<UploadAnalysisDataResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(UploadAnalysisDataResponse value) {
                response.set(value);
            }

            @Override
            public void onError(Throwable throwable) {
                failure.set(throwable);
                completed.countDown();
            }

            @Override
            public void onCompleted() {
                completed.countDown();
            }
        };
        StreamObserver<AnalysisDataEnvelope> requestObserver = ForensicIngestionServiceGrpc.newStub(channel)
                .withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                .uploadAnalysisData(responseObserver);
        try {
            submission.payloads().forEach(payload ->
                    requestObserver.onNext(envelope(sessionId, submission, payload)));
            requestObserver.onCompleted();
            await(completed);
        } catch (RuntimeException exception) {
            requestObserver.onError(exception);
            throw exception;
        }
        Throwable uploadFailure = failure.get();
        if (uploadFailure != null) {
            throw grpcFailure("upload analysis data", uploadFailure);
        }
        UploadAnalysisDataResponse uploadResponse = response.get();
        if (uploadResponse == null) {
            throw new IllegalStateException("Server completed upload without a response.");
        }
        return uploadResponse;
    }

    private void await(CountDownLatch completed) {
        try {
            if (!completed.await(deadline.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Timed out waiting for upload response.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for upload response.", exception);
        }
    }

    private static StartAnalysisSessionRequest startRequest(ForensicsSubmission submission) {
        return StartAnalysisSessionRequest.newBuilder()
                .setBuildIdentity(buildIdentity(submission))
                .setPluginIdentity(pluginIdentity(submission))
                .setSchemaVersion(submission.schemaVersion())
                .build();
    }

    private static AnalysisDataEnvelope envelope(
            String sessionId,
            ForensicsSubmission submission,
            ForensicsPayload payload
    ) {
        return AnalysisDataEnvelope.newBuilder()
                .setSessionId(sessionId)
                .setBuildIdentity(buildIdentity(submission))
                .setModuleIdentity(moduleIdentity(submission))
                .setPluginIdentity(pluginIdentity(submission))
                .setSchemaVersion(submission.schemaVersion())
                .setPayload(ByteString.copyFrom(payload.content()))
                .setPayloadDescriptor(payloadDescriptor(payload))
                .build();
    }

    private static BuildIdentity buildIdentity(ForensicsSubmission submission) {
        return BuildIdentity.newBuilder()
                .setProjectId(submission.projectId())
                .setRepositoryUrl(submission.repositoryUrl())
                .setBranchName(submission.branchName())
                .setCommitHash(submission.commitHash())
                .setBuildId(submission.buildId())
                .setScanTimestamp(submission.scanTimestamp())
                .build();
    }

    private static ModuleIdentity moduleIdentity(ForensicsSubmission submission) {
        return ModuleIdentity.newBuilder()
                .setModuleName(submission.moduleName())
                .setModulePath(submission.modulePath())
                .build();
    }

    private static PluginIdentity pluginIdentity(ForensicsSubmission submission) {
        return PluginIdentity.newBuilder()
                .setPluginName(submission.pluginName())
                .setPluginVersion(submission.pluginVersion())
                .build();
    }

    private static AnalysisPayloadDescriptor payloadDescriptor(ForensicsPayload payload) {
        return AnalysisPayloadDescriptor.newBuilder()
                .setPayloadId(payload.payloadId())
                .setKind(payloadKind(payload.kind()))
                .setContentType(payload.contentType())
                .putAllAttributes(payload.attributes())
                .build();
    }

    private static AnalysisPayloadKind payloadKind(ForensicsPayload.Kind kind) {
        return switch (kind) {
            case SOURCE_FACTS -> AnalysisPayloadKind.ANALYSIS_PAYLOAD_KIND_SOURCE_FACTS;
            case SEMANTIC_ARTIFACTS -> AnalysisPayloadKind.ANALYSIS_PAYLOAD_KIND_SEMANTIC_ARTIFACTS;
            case RULE_ARTIFACTS -> AnalysisPayloadKind.ANALYSIS_PAYLOAD_KIND_RULE_ARTIFACTS;
            case RUNTIME_TRACE -> AnalysisPayloadKind.ANALYSIS_PAYLOAD_KIND_RUNTIME_TRACE;
            case DIAGNOSTIC_REPORT -> AnalysisPayloadKind.ANALYSIS_PAYLOAD_KIND_DIAGNOSTIC_REPORT;
        };
    }

    private static void ensureStatus(IngestionStatus actual, IngestionStatus expected, String operation) {
        if (actual != expected) {
            throw new IllegalStateException(operation + " returned " + actual + ", expected " + expected + ".");
        }
    }

    private static RuntimeException grpcFailure(String operation, Throwable failure) {
        if (failure instanceof StatusRuntimeException statusFailure) {
            return new IllegalStateException(operation + " failed: " + statusFailure.getStatus(), statusFailure);
        }
        return new IllegalStateException(operation + " failed: " + failure.getMessage(), failure);
    }
}
