package de.burger.forensics.adapters.javaparser;

import com.github.javaparser.JavaParser;
import de.burger.forensics.adaptersupport.javaparser.MethodEventExtractor;
import de.burger.forensics.domain.model.ScanEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Executes Java source scanning in parallel batches.
 */
final class JavaParallelFileScanExecutor {

    List<ScanEvent> scan(
        Path root,
        List<Path> sourceFiles,
        JavaParserFactory parserFactory,
        MethodEventExtractor methodEventExtractor
    ) {
        if (sourceFiles.isEmpty()) {
            return List.of();
        }
        int workerCount = Math.min(
            sourceFiles.size(),
            Math.max(1, Runtime.getRuntime().availableProcessors())
        );
        if (workerCount <= 1) {
            return scanBatch(root, sourceFiles, parserFactory, methodEventExtractor);
        }

        List<List<Path>> batches = partition(sourceFiles, workerCount);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        try {
            List<Callable<List<ScanEvent>>> tasks = batches.stream()
                .<Callable<List<ScanEvent>>>map(batch ->
                    () -> scanBatch(root, batch, parserFactory, methodEventExtractor))
                .toList();
            List<Future<List<ScanEvent>>> futures = executor.invokeAll(tasks);
            List<ScanEvent> allEvents = new ArrayList<>();
            for (Future<List<ScanEvent>> future : futures) {
                allEvents.addAll(future.get());
            }
            return allEvents;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return scanBatch(root, sourceFiles, parserFactory, methodEventExtractor);
        } catch (ExecutionException ignored) {
            return scanBatch(root, sourceFiles, parserFactory, methodEventExtractor);
        } finally {
            executor.shutdown();
        }
    }

    private List<ScanEvent> scanBatch(
        Path root,
        List<Path> files,
        JavaParserFactory parserFactory,
        MethodEventExtractor methodEventExtractor
    ) {
        JavaParser parser = parserFactory.create(root);
        return files.stream()
            .map(file -> JavaParserScanEventCollector.collectSafely(parser, file, methodEventExtractor))
            .flatMap(List::stream)
            .toList();
    }

    private List<List<Path>> partition(List<Path> sourceFiles, int partitions) {
        List<List<Path>> batches = new ArrayList<>(partitions);
        for (int i = 0; i < partitions; i++) {
            batches.add(new ArrayList<>());
        }
        for (int i = 0; i < sourceFiles.size(); i++) {
            batches.get(i % partitions).add(sourceFiles.get(i));
        }
        return batches.stream().filter(batch -> !batch.isEmpty()).toList();
    }
}
