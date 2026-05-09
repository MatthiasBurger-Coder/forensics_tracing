package de.burger.forensics.domain.port.out;

/**
 * Stores raw data produced during a forensics analysis run.
 */
public interface AnalysisStorePort extends AnalysisRunStorePort, AnalysisDataStorePort, AutoCloseable {

    @Override
    void close();
}
