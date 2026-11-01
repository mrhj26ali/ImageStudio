package edu.pp.imgstudio.ui;

import edu.pp.imgstudio.core.Filter;
import edu.pp.imgstudio.core.GaussianBlurFilter;
import edu.pp.imgstudio.core.GrayscaleFilter;
import edu.pp.imgstudio.core.SobelFilter;
import edu.pp.imgstudio.engine.ImageUtils;
import edu.pp.imgstudio.engine.ParallelEngine;
import edu.pp.imgstudio.engine.SequentialEngine;
import edu.pp.imgstudio.model.FilterType;
import edu.pp.imgstudio.sync.ProgressTracker;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JavaFX front-end for Parallel Image Studio.
 *
 * <p>All heavy work runs inside a {@link Task} on a background thread, so the
 * UI thread never blocks. Progress is published with {@code Task.updateProgress}
 * and copied into the progress bar by a listener (the bar is deliberately NOT
 * bound, so it can still be reset programmatically between runs).</p>
 */
public class MainApp extends Application {

    /** Immutable outcome of one job (times in milliseconds, -1 = not measured). */
    private record JobResult(long seqMs, long parMs, int threads, int images, int failed) { }

    private ProgressBar progressBar;
    private Label statusLabel;
    private Label metricsLabel;
    private Label inputLabel;
    private Label outputLabel;

    private Button btnRunSequential;
    private Button btnRunParallel;
    private Button btnCompare;
    private Button btnOpenOutput;

    private ComboBox<FilterType> filterCombo;
    private ComboBox<Integer> threadCombo;

    private File inputDir;
    private File outputDir;

    private Task<JobResult> currentTask;
    private HostServices hostServices;

    @Override
    public void start(Stage stage) {
        hostServices = getHostServices();
        stage.setTitle("Parallel Image Studio (Track 1)");

        // Sensible defaults so the user does not have to pick folders every launch.
        File defIn = new File("dataset/input");
        File defOut = new File("dataset/output");
        if (defIn.isDirectory()) inputDir = defIn;
        if (defOut.isDirectory()) outputDir = defOut;

        Button btnSelectInput = new Button("Select Input Folder");
        Button btnSelectOutput = new Button("Select Output Folder");
        btnOpenOutput = new Button("Open Output Folder");

        filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll(FilterType.values());
        filterCombo.setValue(FilterType.SOBEL_EDGE);

        threadCombo = new ComboBox<>();
        threadCombo.getItems().addAll(1, 2, 4, 8, 16);
        threadCombo.setValue(Runtime.getRuntime().availableProcessors() >= 4 ? 4 : 2);

        btnRunSequential = new Button("Run Sequential");
        btnRunParallel = new Button("Run Parallel");
        btnCompare = new Button("Compare (Run Both)");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(660);
        statusLabel = new Label("Ready.");
        metricsLabel = new Label("");
        metricsLabel.setStyle("-fx-font-family: 'Consolas','monospace'; -fx-font-size: 12px;");

        inputLabel = new Label();
        outputLabel = new Label();
        refreshFolderLabels();

        VBox root = new VBox(12);
        root.setPadding(new Insets(18));
        root.getChildren().addAll(
                new HBox(10, btnSelectInput, btnSelectOutput, btnOpenOutput),
                new VBox(2, inputLabel, outputLabel),
                new HBox(10, new Label("Filter:"), filterCombo, new Label("Threads:"), threadCombo),
                new HBox(10, btnRunSequential, btnRunParallel, btnCompare),
                progressBar,
                statusLabel,
                metricsLabel
        );

        stage.setScene(new Scene(root, 720, 340));
        stage.show();

        btnSelectInput.setOnAction(e -> {
            File chosen = chooseFolder(stage, "Select Input Folder", inputDir);
            if (chosen != null) { inputDir = chosen; refreshFolderLabels(); }
        });

        btnSelectOutput.setOnAction(e -> {
            File chosen = chooseFolder(stage, "Select Output Folder", outputDir);
            if (chosen != null) { outputDir = chosen; refreshFolderLabels(); }
        });

        btnOpenOutput.setOnAction(e -> {
            if (outputDir != null && outputDir.isDirectory()) {
                hostServices.showDocument(outputDir.getAbsolutePath());
            }
        });

        btnRunSequential.setOnAction(e -> launchJob(Mode.SEQUENTIAL));
        btnRunParallel.setOnAction(e -> launchJob(Mode.PARALLEL));
        btnCompare.setOnAction(e -> launchJob(Mode.COMPARE));
    }

    private enum Mode { SEQUENTIAL, PARALLEL, COMPARE }

    // ------------------------------------------------------------------
    // Job launching / UI state
    // ------------------------------------------------------------------

    /** Validates input, resets the UI and starts one background job. */
    private void launchJob(Mode mode) {
        if (currentTask != null && currentTask.isRunning()) return; // guard against double clicks

        if (inputDir == null || outputDir == null) {
            showAlert("Please select both an input and an output folder first.");
            return;
        }
        if (inputDir.equals(outputDir)) {
            showAlert("Input and output folders must be different.");
            return;
        }

        List<File> files = ImageUtils.discoverImages(inputDir);
        if (files.isEmpty()) {
            showAlert("No .jpg / .jpeg / .png images found in:\n" + inputDir.getAbsolutePath());
            return;
        }

        final int threads = threadCombo.getValue();
        final FilterType filterType = filterCombo.getValue();
        final File outDir = outputDir;

        resetUI();
        setRunning(true);
        statusLabel.setText(switch (mode) {
            case SEQUENTIAL -> "Sequential run in progress...";
            case PARALLEL -> "Parallel run in progress (" + threads + " threads)...";
            case COMPARE -> "Comparison: sequential pass...";
        });

        Task<JobResult> task = new Task<>() {
            @Override
            protected JobResult call() throws Exception {
                AtomicBoolean finished = new AtomicBoolean(false);

                // Publishes ProgressTracker state as Task progress (0..1).
                Thread ticker = new Thread(() -> {
                    while (!finished.get()) {
                        updateProgress(currentFraction(), 1.0);
                        try { Thread.sleep(80); } catch (InterruptedException ie) { break; }
                    }
                }, "progress-ticker");
                ticker.setDaemon(true);
                ticker.start();

                try {
                    if (mode == Mode.COMPARE) {
                        phase = 0;
                        ProgressTracker t1 = new ProgressTracker(files.size());
                        activeTracker = t1;
                        long seq = SequentialEngine.processBatch(files, outDir, createFilter(filterType), t1).timeMs;

                        phase = 1;
                        updateMessage("Comparison: parallel pass...");
                        ProgressTracker t2 = new ProgressTracker(files.size());
                        activeTracker = t2;
                        ParallelEngine.ProcessingResult pr =
                                ParallelEngine.processBatch(files, outDir, createFilter(filterType), threads, t2);

                        return new JobResult(seq, pr.timeMs, threads, files.size(), pr.failedFiles.size());
                    }

                    phase = 0;
                    ProgressTracker t = new ProgressTracker(files.size());
                    activeTracker = t;

                    if (mode == Mode.PARALLEL) {
                        ParallelEngine.ProcessingResult pr =
                                ParallelEngine.processBatch(files, outDir, createFilter(filterType), threads, t);
                        return new JobResult(-1, pr.timeMs, threads, files.size(), pr.failedFiles.size());
                    }

                    ParallelEngine.ProcessingResult sr =
                            SequentialEngine.processBatch(files, outDir, createFilter(filterType), t);
                    return new JobResult(sr.timeMs, -1, 1, files.size(), sr.failedFiles.size());

                } finally {
                    finished.set(true);
                    ticker.interrupt();
                }
            }

            // Progress plumbing: phase 0 = first pass, phase 1 = second pass (compare only).
            private volatile int phase = 0;
            private volatile ProgressTracker activeTracker;

            private double currentFraction() {
                ProgressTracker t = activeTracker;
                double local = (t == null) ? 0.0 : Math.min(1.0, t.getPercentage() / 100.0);
                if (mode != Mode.COMPARE) return local;
                return phase == 0 ? local * 0.5 : 0.5 + local * 0.5;
            }
        };

        // Copy progress instead of binding: setProgress() must stay usable.
        task.progressProperty().addListener((obs, oldV, newV) -> progressBar.setProgress(newV.doubleValue()));
        task.messageProperty().addListener((obs, oldV, newV) -> statusLabel.setText(newV));

        task.setOnSucceeded(e -> {
            JobResult r = task.getValue();
            progressBar.setProgress(1.0);
            statusLabel.setText(String.format("Done - %d image(s) processed%s.",
                    r.images(), r.failed() > 0 ? ", " + r.failed() + " skipped/failed" : ""));
            metricsLabel.setText(formatMetrics(r));
            setRunning(false);
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            progressBar.setProgress(0);
            statusLabel.setText("Failed: " + (ex == null ? "unknown error" : ex.toString()));
            metricsLabel.setText("");
            if (ex != null) ex.printStackTrace();
            setRunning(false);
        });

        task.setOnCancelled(e -> {
            progressBar.setProgress(0);
            statusLabel.setText("Cancelled.");
            metricsLabel.setText("");
            setRunning(false);
        });

        currentTask = task;
        Thread runner = new Thread(task, "job-runner");
        runner.setDaemon(true);
        runner.start();
    }

    /** Formats the timing / speedup / efficiency line shown under the progress bar. */
    private String formatMetrics(JobResult r) {
        if (r.seqMs() >= 0 && r.parMs() >= 0) {
            double speedup = r.parMs() == 0 ? 0 : (double) r.seqMs() / r.parMs();
            double efficiency = speedup / r.threads();
            return String.format(
                    "T_seq = %.3f s   |   T_par(%d) = %.3f s   |   Speedup = %.2fx   |   Efficiency = %.1f%%",
                    r.seqMs() / 1000.0, r.threads(), r.parMs() / 1000.0, speedup, efficiency * 100);
        }
        if (r.parMs() >= 0) {
            return String.format("Mode = Parallel   |   Threads = %d   |   T_par = %.3f s   |   %.1f images/s",
                    r.threads(), r.parMs() / 1000.0,
                    r.parMs() == 0 ? 0.0 : r.images() / (r.parMs() / 1000.0));
        }
        return String.format("Mode = Sequential   |   Threads = 1   |   T_seq = %.3f s   |   %.1f images/s",
                r.seqMs() / 1000.0,
                r.seqMs() == 0 ? 0.0 : r.images() / (r.seqMs() / 1000.0));
    }

    /** Clears progress, status and previous results before a new run. */
    private void resetUI() {
        progressBar.progressProperty().unbind(); // safety: never leave a stale binding
        progressBar.setProgress(0);
        metricsLabel.setText("");
        statusLabel.setText("Starting...");
    }

    /** Enables/disables controls for the duration of a job. */
    private void setRunning(boolean running) {
        btnRunSequential.setDisable(running);
        btnRunParallel.setDisable(running);
        btnCompare.setDisable(running);
        filterCombo.setDisable(running);
        threadCombo.setDisable(running);
    }

    private void refreshFolderLabels() {
        inputLabel.setText("Input:  " + (inputDir == null ? "(not selected)" : inputDir.getAbsolutePath()));
        outputLabel.setText("Output: " + (outputDir == null ? "(not selected)" : outputDir.getAbsolutePath()));
    }

    private File chooseFolder(Stage owner, String title, File initial) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        if (initial != null && initial.isDirectory()) chooser.setInitialDirectory(initial);
        return chooser.showDialog(owner);
    }

    /** Factory for a fresh, stateless filter instance (one per pass, no sharing). */
    private Filter createFilter(FilterType type) {
        return switch (type) {
            case GRAYSCALE -> new GrayscaleFilter();
            case GAUSSIAN_BLUR -> new GaussianBlurFilter();
            case SOBEL_EDGE -> new SobelFilter();
        };
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Parallel Image Studio");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        if (currentTask != null && currentTask.isRunning()) currentTask.cancel(true);
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}