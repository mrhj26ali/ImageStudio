# Parallel Image Studio

A high-performance batch image processing desktop application built in Java that demonstrates parallel programming concepts through practical implementation. The application applies computationally intensive image filters to batches of high-resolution images using both sequential and parallel execution modes, enabling direct performance comparison and speedup analysis.

## Overview

Parallel Image Studio processes batches of images using three different filters:

- **Grayscale**: Converts color images to grayscale using luminance calculation (0.299R + 0.587G + 0.114B)
- **Gaussian Blur**: Applies noise removal using 3x3 2D convolution with a Gaussian kernel
- **Sobel Edge Detection**: Detects edges using 2D convolution with Sobel X and Y gradient kernels

The application provides both sequential (single-threaded) and parallel (multi-threaded) execution modes, allowing direct performance comparison and speedup calculation using the formula: `Speedup = T_sequential / T_parallel`.

## Features

- **Multiple Filter Support**: Three image processing filters with different computational characteristics
- **Dual Execution Modes**: Sequential baseline and parallel execution with configurable thread count (1, 2, 4, 8, 16 threads)
- **Data Parallelism**: Each image is split into horizontal row bands processed by separate threads
- **Task Parallelism**: Multiple images are processed concurrently via a Thread Pool
- **Real-time Progress Tracking**: Non-blocking UI with live progress updates using AtomicInteger
- **Memory Management**: Semaphore-based limiter prevents OutOfMemory errors on large batches
- **Robust Error Handling**: Gracefully handles corrupt or unreadable images without aborting the batch
- **Automated Benchmarking**: Built-in tool for comprehensive performance analysis with warm-up, multiple repetitions, and statistical aggregation
- **Comparison Mode**: Direct side-by-side comparison of sequential vs parallel execution with speedup and efficiency metrics
- **Plotting Support**: Python script for generating publication-ready performance charts

## Architecture

The project follows a strict separation of concerns with five distinct layers, isolating threading logic from business logic and UI:

### Core Layer (`core/`)
Pure image processing algorithms with no threading or UI dependencies. Each filter implements the `Filter` interface with a method signature `apply(source, destination, startRow, endRow)` that operates on a specific range of rows. This design enables both sequential execution (startRow=0, endRow=height) and parallel execution (multiple threads each handling disjoint row ranges).

- `Filter.java`: Interface defining the filter contract
- `GrayscaleFilter.java`: Luminance-based grayscale conversion
- `GaussianBlurFilter.java`: 3x3 Gaussian kernel for noise removal
- `SobelFilter.java`: Sobel edge detection with X and Y gradient kernels

### Engine Layer (`engine/`)
Execution strategies for sequential and parallel processing.

- `SequentialEngine.java`: Single-threaded baseline implementation that processes images one by one
- `ParallelEngine.java`: Multi-threaded implementation with Thread Pool and row-band chunking using the formula from the course materials (`getChunkStart`/`getChunkEnd`)
- `ImageUtils.java`: Shared utilities for file discovery and naming

### Synchronization Layer (`sync/`)
Thread-safe utilities for coordination.

- `ProgressTracker.java`: AtomicInteger-based lock-free progress tracking, allowing the UI thread to read progress safely without any locks

### UI Layer (`ui/`)
JavaFX desktop application with non-blocking interface.

- `MainApp.java`: Main application with `javafx.concurrent.Task`-based background execution. UI updates are delegated to the JavaFX Application Thread via `Platform.runLater()`, ensuring the UI never freezes during heavy processing.

### Benchmark Layer (`benchmark/`)
Automated performance testing and analysis.

- `BenchmarkRunner.java`: Comprehensive benchmarking with warm-up, multiple repetitions, median/std-dev statistics, and machine info reporting

## How It Works

### Threading Strategy

The parallel engine uses a two-level parallelism approach:

1. **Task Parallelism (Thread Pool)**: An `ExecutorService` with N threads manages the overall workload. Each submitted task processes one complete image.

2. **Data Parallelism (Row Banding)**: Within each image, the height is divided into N horizontal bands using the chunking formula:
   ```
   chunkSize = (height + N - 1) / N
   startRow(i) = i * chunkSize
   endRow(i) = min((i + 1) * chunkSize, height)
   ```
   Each worker thread processes its assigned row band independently.

### Race Condition Prevention

The design eliminates race conditions by construction:
- Each worker thread writes to a disjoint set of rows in the output image
- The source image is read-only and shared safely
- No shared mutable state exists between worker threads
- `ProgressTracker` uses `AtomicInteger` for atomic increments without locks

### Synchronization Mechanisms

| Mechanism | Location | Purpose |
|-----------|----------|---------|
| Thread Pool (`ExecutorService`) | `ParallelEngine` | Reuses a fixed set of N worker threads |
| Atomic Variables (`AtomicInteger`) | `ProgressTracker` | Lock-free thread-safe progress tracking |
| Semaphore | `ParallelEngine` | Limits concurrent image loads to prevent OutOfMemory |
| Future | `ParallelEngine` | Waits for all tasks to complete before shutdown |
| Task + Platform.runLater | `MainApp` | Keeps UI responsive during heavy computation |

### Error Handling

- Corrupt or unreadable images are skipped gracefully without aborting the batch
- Failed files are collected and reported to the caller via `ProcessingResult`
- Semaphore permits are always released in `finally` blocks to prevent leaks
- `ExecutorService` is shut down in a `finally` block

## Prerequisites

- **Java Development Kit (JDK)**: Version 25 or later
- **Python 3.8+** (optional): Only required for the plotting script
- **Git**: For cloning the repository

No global Maven installation is required. The project includes the Maven Wrapper (`mvnw.cmd`) which automatically downloads the required Maven version.

## Building and Running

### Method 1: Using IntelliJ IDEA (Recommended)

1. Open the project folder in IntelliJ IDEA
2. Allow Maven to load dependencies (click "Load Maven Changes" if prompted)
3. Navigate to `src/main/java/edu/pp/imgstudio/ui/MainApp.java`
4. Click the green Run icon next to the `main` method

### Method 2: Using the Launch Script (Windows)

Double-click `Launch_ImageStudio.bat` in the project root. The script:
- Detects your `JAVA_HOME` or falls back to the default JDK path
- Compiles the project via Maven Wrapper
- Launches the JavaFX Desktop UI

### Method 3: Using Command Line

```bash
.\mvnw.cmd clean javafx:run
```

## Using the Application

1. **Select Input Folder**: Click "Select Input Folder" and choose a folder containing images (at least 5 images recommended)
2. **Select Output Folder**: Click "Select Output Folder" and choose where processed images will be saved
3. **Choose Filter**: Select from the dropdown:
    - Grayscale
    - Gaussian Blur (Noise Removal)
    - Sobel Edge Detection
4. **Set Thread Count**: Choose 1, 2, 4, 8, or 16 threads
5. **Execute**:
    - **Run Sequential**: Process using single-threaded baseline
    - **Run Parallel**: Process using multi-threaded engine
    - **Compare (Run Both)**: Run both modes and display speedup/efficiency metrics
6. **Monitor Progress**: The progress bar updates in real-time without freezing the UI
7. **View Results**: Processed images appear in the output folder with prefix `processed_`

## Running the Benchmark

The benchmark runner generates comprehensive performance data for analysis:

```bash
.\mvnw.cmd clean compile exec:java -Dexec.mainClass="edu.pp.imgstudio.benchmark.BenchmarkRunner"
```

Or right-click `BenchmarkRunner.java` in IntelliJ and select Run.

The benchmark:
- Prints machine information (OS, CPU count, JVM version, heap size)
- Performs a warm-up pass for JIT compilation
- Runs 5 measured iterations per configuration
- Tests all 3 filters across N = 1, 2, 4, 8, 16 threads
- Calculates median and standard deviation for statistical reliability
- Generates two CSV files:
    - `benchmark_raw.csv`: All individual run measurements
    - `benchmark_summary.csv`: Aggregated median, std-dev, speedup, efficiency

## Generating Performance Charts

After running the benchmark, generate publication-ready charts:

```bash
pip install matplotlib pandas numpy
python plot_benchmark.py
```

This generates three PNG charts:
- `chart_time_vs_threads.png`: Execution time vs N per filter with error bars
- `chart_speedup_vs_threads.png`: Speedup vs N with ideal linear and 4-core reference lines
- `chart_efficiency_vs_threads.png`: Parallel efficiency vs N

## Running Tests

```bash
.\mvnw.cmd test
```

The test suite verifies:
- **Pixel-perfect correctness**: Parallel output equals sequential output for all 3 filters and N = 1, 2, 4, 8, 16
- **Edge cases**: Images shorter than N, heights not divisible by N
- **Thread safety**: ProgressTracker reaches total under 16-thread concurrent increments
- **Error handling**: Corrupt files do not abort either engine
- **Resource safety**: Semaphore does not leak across consecutive batch runs

## Project Structure

```
ImageStudio/
|-- pom.xml                              # Maven build configuration
|-- Launch_ImageStudio.bat               # Windows launcher script
|-- mvnw.cmd                             # Maven Wrapper
|-- plot_benchmark.py                    # Python plotting script
|-- README.md                            # This file
|-- dataset/
|   |-- input/                           # Input images (user-provided)
|   |-- output/                          # Processed output images
|-- src/
|   |-- main/java/edu/pp/imgstudio/
|   |   |-- core/                        # Pure image processing algorithms
|   |   |   |-- Filter.java
|   |   |   |-- GrayscaleFilter.java
|   |   |   |-- GaussianBlurFilter.java
|   |   |   |-- SobelFilter.java
|   |   |-- engine/                      # Threading and execution logic
|   |   |   |-- SequentialEngine.java
|   |   |   |-- ParallelEngine.java
|   |   |   |-- ImageUtils.java
|   |   |-- sync/                        # Thread-safe utilities
|   |   |   |-- ProgressTracker.java
|   |   |-- model/                       # Data models
|   |   |   |-- FilterType.java
|   |   |-- ui/                          # JavaFX Desktop Application
|   |   |   |-- MainApp.java
|   |   |-- benchmark/                   # Performance testing
|   |       |-- BenchmarkRunner.java
|   |-- test/java/edu/pp/imgstudio/
|       |-- ImageStudioTest.java         # JUnit test suite
```

## Performance Characteristics

Based on benchmarking with high-resolution images:

- **Grayscale Filter** (memory-bandwidth-bound): Speedup plateaus around 2.8x at N=8 due to memory bandwidth saturation
- **Gaussian Blur and Sobel** (compute-bound 2D Convolution): Speedup reaches approximately 3.0x at N=4, then plateaus
- **Efficiency**: Drops significantly beyond N=4 due to thread creation overhead, context switching, and memory bandwidth contention

### Bottlenecks

1. **Memory Bandwidth**: Reading/writing large BufferedImage arrays saturates RAM bandwidth
2. **Thread Overhead**: Thread creation and synchronization overhead becomes significant at high N
3. **I/O Boundaries**: Image loading and saving are sequential operations that limit maximum theoretical speedup
4. **False Sharing**: Adjacent row bands may share cache lines, causing cache coherency traffic

## License

This project was developed for educational purposes to demonstrate parallel programming concepts in Java.