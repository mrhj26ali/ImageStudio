#!/usr/bin/env python3
"""
Plot script for Parallel Image Studio benchmark results.
Reads benchmark_summary.csv and generates three charts:
  1. Time vs N per filter
  2. Speedup vs N with ideal line and 4-core line
  3. (Optional) StdDev error bars

Usage:
    python plot_benchmark.py

Requires: matplotlib, pandas
Install:  pip install matplotlib pandas
"""

import os
import sys

try:
    import pandas as pd
    import matplotlib.pyplot as plt
    import numpy as np
except ImportError as e:
    print(f"Missing dependency: {e}")
    print("Install with: pip install matplotlib pandas numpy")
    sys.exit(1)


def main():
    csv_path = "benchmark_summary.csv"
    if not os.path.exists(csv_path):
        print(f"ERROR: '{csv_path}' not found.")
        print("Run the BenchmarkRunner first to generate it.")
        sys.exit(1)

    df = pd.read_csv(csv_path)
    print(f"Loaded {len(df)} rows from {csv_path}")
    print(df.head())

    filters = df['Filter'].unique()
    colors = {'Grayscale': '#1f77b4', 'GaussianBlur': '#ff7f0e', 'Sobel': '#2ca02c'}
    markers = {'Grayscale': 'o', 'GaussianBlur': 's', 'Sobel': '^'}

    # --- Figure 1: Time vs N per filter ---
    fig, ax = plt.subplots(figsize=(10, 6))
    for f in filters:
        subset = df[df['Filter'] == f]
        ax.errorbar(subset['Threads'], subset['Median_ms'],
                    yerr=subset['StdDev_ms'],
                    label=f, color=colors.get(f, 'gray'),
                    marker=markers.get(f, 'o'), capsize=4, linewidth=2)
    ax.set_xlabel('Number of Threads (N)')
    ax.set_ylabel('Execution Time (ms)')
    ax.set_title('Execution Time vs Number of Threads')
    ax.set_xscale('log', base=2)
    ax.set_xticks([1, 2, 4, 8, 16])
    ax.set_xticklabels(['1', '2', '4', '8', '16'])
    ax.grid(True, which='both', linestyle='--', alpha=0.5)
    ax.legend()
    plt.tight_layout()
    plt.savefig('chart_time_vs_threads.png', dpi=150)
    print("Saved: chart_time_vs_threads.png")

    # --- Figure 2: Speedup vs N ---
    fig, ax = plt.subplots(figsize=(10, 6))
    thread_values = sorted(df['Threads'].unique())

    # Ideal linear speedup line
    ax.plot(thread_values, thread_values, 'k--', label='Ideal Linear Speedup', linewidth=2)

    # 4-core line (realistic for a 4-core machine)
    ax.plot(thread_values, [min(t, 4) for t in thread_values], 'k:',
            label='4-Core Limit', linewidth=1.5)

    for f in filters:
        subset = df[(df['Filter'] == f) & (df['Mode'] == 'Parallel')]
        # Add sequential point (N=1, speedup=1)
        seq_row = df[(df['Filter'] == f) & (df['Mode'] == 'Sequential')]
        if not seq_row.empty:
            subset = pd.concat([seq_row[['Threads', 'Speedup']], subset[['Threads', 'Speedup']]])
            subset = subset.sort_values('Threads')
        ax.plot(subset['Threads'], subset['Speedup'],
                label=f, color=colors.get(f, 'gray'),
                marker=markers.get(f, 'o'), linewidth=2, markersize=8)

    ax.set_xlabel('Number of Threads (N)')
    ax.set_ylabel('Speedup (T_seq / T_par)')
    ax.set_title('Speedup vs Number of Threads')
    ax.set_xscale('log', base=2)
    ax.set_xticks([1, 2, 4, 8, 16])
    ax.set_xticklabels(['1', '2', '4', '8', '16'])
    ax.grid(True, which='both', linestyle='--', alpha=0.5)
    ax.legend()
    plt.tight_layout()
    plt.savefig('chart_speedup_vs_threads.png', dpi=150)
    print("Saved: chart_speedup_vs_threads.png")

    # --- Figure 3: Efficiency vs N ---
    fig, ax = plt.subplots(figsize=(10, 6))
    for f in filters:
        subset = df[(df['Filter'] == f) & (df['Mode'] == 'Parallel')]
        ax.plot(subset['Threads'], subset['Efficiency'] * 100,
                label=f, color=colors.get(f, 'gray'),
                marker=markers.get(f, 'o'), linewidth=2, markersize=8)
    ax.axhline(y=100, color='k', linestyle='--', label='Ideal (100%)', linewidth=1.5)
    ax.set_xlabel('Number of Threads (N)')
    ax.set_ylabel('Efficiency (%)')
    ax.set_title('Parallel Efficiency vs Number of Threads')
    ax.set_xscale('log', base=2)
    ax.set_xticks([1, 2, 4, 8, 16])
    ax.set_xticklabels(['1', '2', '4', '8', '16'])
    ax.set_ylim(0, 110)
    ax.grid(True, which='both', linestyle='--', alpha=0.5)
    ax.legend()
    plt.tight_layout()
    plt.savefig('chart_efficiency_vs_threads.png', dpi=150)
    print("Saved: chart_efficiency_vs_threads.png")

    print("\nAll charts generated successfully!")


if __name__ == "__main__":
    main()