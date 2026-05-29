package de.burger.forensics.plugin.btmgen.grpc;

public interface ForensicIngestionClient extends AutoCloseable {
    ForensicsSubmissionResult submit(ForensicsSubmission submission);

    @Override
    void close();
}
