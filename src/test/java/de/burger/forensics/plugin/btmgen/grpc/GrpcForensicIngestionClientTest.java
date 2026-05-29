package de.burger.forensics.plugin.btmgen.grpc;

import de.burger.forensics.analytics.ingestion.v1.AnalysisDataEnvelope;
import de.burger.forensics.analytics.ingestion.v1.CompleteAnalysisSessionRequest;
import de.burger.forensics.analytics.ingestion.v1.CompleteAnalysisSessionResponse;
import de.burger.forensics.analytics.ingestion.v1.ForensicIngestionServiceGrpc;
import de.burger.forensics.analytics.ingestion.v1.IngestionStatus;
import de.burger.forensics.analytics.ingestion.v1.StartAnalysisSessionRequest;
import de.burger.forensics.analytics.ingestion.v1.StartAnalysisSessionResponse;
import de.burger.forensics.analytics.ingestion.v1.UploadAnalysisDataResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcForensicIngestionClientTest {

    @Test
    void submitsSessionPayloadAndCompletionThroughVerifiedGrpcContract() throws Exception {
        RecordingIngestionService service = new RecordingIngestionService();
        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        try {
            var client = new GrpcForensicIngestionClient(channel, Duration.ofSeconds(5));
            var result = client.submit(submission());

            assertThat(result.sessionId()).isEqualTo("session-1");
            assertThat(result.status()).isEqualTo("INGESTION_STATUS_COMPLETED");
            assertThat(result.uploadedPayloads()).isEqualTo(5);
            assertThat(service.startRequest.getBuildIdentity().getProjectId()).isEqualTo("project-a");
            assertThat(service.startRequest.getBuildIdentity().getRepositoryUrl()).isEqualTo("https://example.test/repo.git");
            assertThat(service.startRequest.getBuildIdentity().getCommitHash()).isEqualTo("abc123");
            assertThat(service.uploaded).hasSize(5);
            AnalysisDataEnvelope envelope = service.uploaded.get(0);
            assertThat(envelope.getSessionId()).isEqualTo("session-1");
            assertThat(envelope.getModuleIdentity().getModulePath()).isEqualTo(":module-a");
            assertThat(envelope.getPayloadDescriptor().getPayloadId()).isEqualTo("build-context");
            assertThat(envelope.getPayloadDescriptor().getAttributesMap()).containsEntry("artifact", "build-context");
            assertThat(envelope.getPayload().toString(StandardCharsets.UTF_8)).isEqualTo("{\"ok\":true}");
            assertThat(service.completeRequest.getSessionId()).isEqualTo("session-1");
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void rejectsInvalidDeadlineValues() {
        ManagedChannel channel = InProcessChannelBuilder.forName(InProcessServerBuilder.generateName())
                .directExecutor()
                .build();
        try {
            assertThatThrownBy(() -> new GrpcForensicIngestionClient(channel, Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("deadline must be positive");
            assertThatThrownBy(() -> new GrpcForensicIngestionClient(channel, Duration.ofSeconds(-1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("deadline must be positive");
        } finally {
            channel.shutdownNow();
        }
    }

    @Test
    void closeOnlyShutsDownOwnedChannels() {
        ManagedChannel externalChannel = InProcessChannelBuilder.forName(InProcessServerBuilder.generateName())
                .directExecutor()
                .build();
        try {
            new GrpcForensicIngestionClient(externalChannel, Duration.ofMillis(50)).close();
            assertThat(externalChannel.isShutdown()).isFalse();
        } finally {
            externalChannel.shutdownNow();
        }

        GrpcForensicIngestionClient plaintextClient = GrpcForensicIngestionClient.connect(
                "localhost",
                65535,
                true,
                Duration.ofMillis(50));
        plaintextClient.close();

        GrpcForensicIngestionClient tlsClient = GrpcForensicIngestionClient.connect(
                "localhost",
                65535,
                false,
                Duration.ofMillis(50));
        tlsClient.close();
    }

    @Test
    void rejectsUnexpectedStartUploadAndCompletionStatuses() throws Exception {
        assertThatThrownBy(() -> submitWith(new RejectedStartService()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("start analysis session returned INGESTION_STATUS_REJECTED");

        assertThatThrownBy(() -> submitWith(new RejectedUploadService()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("upload analysis data returned INGESTION_STATUS_REJECTED");

        assertThatThrownBy(() -> submitWith(new RejectedCompletionService()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("complete analysis session returned INGESTION_STATUS_REJECTED");
    }

    @Test
    void failsWhenUploadStreamCompletesInvalidlyOrWithGrpcError() throws Exception {
        assertThatThrownBy(() -> submitWith(new NoUploadResponseService()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("upload analysis data failed");

        assertThatThrownBy(() -> submitWith(new UploadErrorService()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("upload analysis data failed");
    }

    private static void submitWith(ForensicIngestionServiceGrpc.ForensicIngestionServiceImplBase service)
            throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        try {
            new GrpcForensicIngestionClient(channel, Duration.ofSeconds(5)).submit(submission());
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static ForensicsSubmission submission() {
        return new ForensicsSubmission(
                "1",
                "project-a",
                "https://example.test/repo.git",
                "main",
                "abc123",
                "build-42",
                "1970-01-01T00:00:00Z",
                "module-a",
                ":module-a",
                "forensics-tracing",
                "1.2.3",
                List.of(
                        payload("build-context", ForensicsPayload.Kind.DIAGNOSTIC_REPORT),
                        payload("source-facts", ForensicsPayload.Kind.SOURCE_FACTS),
                        payload("semantic-artifacts", ForensicsPayload.Kind.SEMANTIC_ARTIFACTS),
                        payload("rule-artifacts", ForensicsPayload.Kind.RULE_ARTIFACTS),
                        payload("runtime-trace", ForensicsPayload.Kind.RUNTIME_TRACE)));
    }

    private static ForensicsPayload payload(String payloadId, ForensicsPayload.Kind kind) {
        return new ForensicsPayload(
                payloadId,
                kind,
                "application/json",
                "{\"ok\":true}".getBytes(StandardCharsets.UTF_8),
                Map.of("artifact", payloadId));
    }

    private static class RecordingIngestionService
            extends ForensicIngestionServiceGrpc.ForensicIngestionServiceImplBase {
        private final List<AnalysisDataEnvelope> uploaded = new ArrayList<>();
        private StartAnalysisSessionRequest startRequest;
        private CompleteAnalysisSessionRequest completeRequest;

        @Override
        public void startAnalysisSession(
                StartAnalysisSessionRequest request,
                StreamObserver<StartAnalysisSessionResponse> responseObserver
        ) {
            startRequest = request;
            responseObserver.onNext(StartAnalysisSessionResponse.newBuilder()
                    .setSessionId("session-1")
                    .setStatus(IngestionStatus.INGESTION_STATUS_ACCEPTED)
                    .setMessage("accepted")
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public StreamObserver<AnalysisDataEnvelope> uploadAnalysisData(
                StreamObserver<UploadAnalysisDataResponse> responseObserver
        ) {
            return new StreamObserver<>() {
                @Override
                public void onNext(AnalysisDataEnvelope value) {
                    uploaded.add(value);
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    responseObserver.onNext(UploadAnalysisDataResponse.newBuilder()
                            .setSessionId("session-1")
                            .setStatus(IngestionStatus.INGESTION_STATUS_ACCEPTED)
                            .setReceivedItems(uploaded.size())
                            .setMessage("uploaded")
                            .build());
                    responseObserver.onCompleted();
                }
            };
        }

        @Override
        public void completeAnalysisSession(
                CompleteAnalysisSessionRequest request,
                StreamObserver<CompleteAnalysisSessionResponse> responseObserver
        ) {
            completeRequest = request;
            responseObserver.onNext(CompleteAnalysisSessionResponse.newBuilder()
                    .setSessionId(request.getSessionId())
                    .setStatus(IngestionStatus.INGESTION_STATUS_COMPLETED)
                    .setMessage("completed")
                    .build());
            responseObserver.onCompleted();
        }
    }

    private static final class RejectedStartService extends RecordingIngestionService {
        @Override
        public void startAnalysisSession(
                StartAnalysisSessionRequest request,
                StreamObserver<StartAnalysisSessionResponse> responseObserver
        ) {
            responseObserver.onNext(StartAnalysisSessionResponse.newBuilder()
                    .setSessionId("session-1")
                    .setStatus(IngestionStatus.INGESTION_STATUS_REJECTED)
                    .setMessage("rejected")
                    .build());
            responseObserver.onCompleted();
        }
    }

    private static final class RejectedUploadService extends RecordingIngestionService {
        @Override
        public StreamObserver<AnalysisDataEnvelope> uploadAnalysisData(
                StreamObserver<UploadAnalysisDataResponse> responseObserver
        ) {
            return new StreamObserver<>() {
                @Override
                public void onNext(AnalysisDataEnvelope value) {
                    // The test only verifies server status handling.
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    responseObserver.onNext(UploadAnalysisDataResponse.newBuilder()
                            .setSessionId("session-1")
                            .setStatus(IngestionStatus.INGESTION_STATUS_REJECTED)
                            .setReceivedItems(0)
                            .setMessage("rejected")
                            .build());
                    responseObserver.onCompleted();
                }
            };
        }
    }

    private static final class RejectedCompletionService extends RecordingIngestionService {
        @Override
        public void completeAnalysisSession(
                CompleteAnalysisSessionRequest request,
                StreamObserver<CompleteAnalysisSessionResponse> responseObserver
        ) {
            responseObserver.onNext(CompleteAnalysisSessionResponse.newBuilder()
                    .setSessionId(request.getSessionId())
                    .setStatus(IngestionStatus.INGESTION_STATUS_REJECTED)
                    .setMessage("rejected")
                    .build());
            responseObserver.onCompleted();
        }
    }

    private static final class NoUploadResponseService extends RecordingIngestionService {
        @Override
        public StreamObserver<AnalysisDataEnvelope> uploadAnalysisData(
                StreamObserver<UploadAnalysisDataResponse> responseObserver
        ) {
            return new StreamObserver<>() {
                @Override
                public void onNext(AnalysisDataEnvelope value) {
                    // The server intentionally completes without a response.
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }
    }

    private static final class UploadErrorService extends RecordingIngestionService {
        @Override
        public StreamObserver<AnalysisDataEnvelope> uploadAnalysisData(
                StreamObserver<UploadAnalysisDataResponse> responseObserver
        ) {
            return new StreamObserver<>() {
                @Override
                public void onNext(AnalysisDataEnvelope value) {
                    responseObserver.onError(Status.INTERNAL.withDescription("upload failed").asRuntimeException());
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    // The stream already failed.
                }
            };
        }
    }
}
