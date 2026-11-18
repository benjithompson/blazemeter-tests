package com.example.jmeter.copilot.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures all samples from a single test run for comparison.
 */
public class RunCapture {
    private final int runNumber;
    private final long startTime;
    private final List<SampleCapture> samples;
    private long endTime;

    public RunCapture(int runNumber) {
        this.runNumber = runNumber;
        this.startTime = System.currentTimeMillis();
        this.samples = new ArrayList<>();
    }

    public void addSample(SampleCapture sample) {
        samples.add(sample);
    }

    public void finish() {
        this.endTime = System.currentTimeMillis();
    }

    public int getRunNumber() { return runNumber; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public List<SampleCapture> getSamples() { return samples; }
    public int getSampleCount() { return samples.size(); }

    public SampleCapture getSampleByIndex(int index) {
        return index >= 0 && index < samples.size() ? samples.get(index) : null;
    }

    public SampleCapture getSampleByLabel(String label) {
        for (SampleCapture sample : samples) {
            if (sample.getLabel().equals(label)) {
                return sample;
            }
        }
        return null;
    }
}
