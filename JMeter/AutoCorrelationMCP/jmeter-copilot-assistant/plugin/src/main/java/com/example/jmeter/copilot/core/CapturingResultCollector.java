package com.example.jmeter.copilot.core;

import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.testelement.TestStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A ResultCollector that forwards sample events to a custom SampleListener.
 * This allows us to capture sample data for correlation analysis while still
 * integrating properly with the JMeter engine.
 */
public class CapturingResultCollector extends ResultCollector implements TestStateListener {

    private static final Logger log = LoggerFactory.getLogger(CapturingResultCollector.class);
    private final SampleListener delegateListener;

    public CapturingResultCollector(SampleListener listener) {
        super();
        this.delegateListener = listener;
        // Critical: Enable this element so it receives events
        setEnabled(true);
        setName("Copilot Capturing Collector");
        log.info("CapturingResultCollector created with delegate: {}", listener.getClass().getName());
    }

    @Override
    public void sampleOccurred(SampleEvent event) {
        log.debug("sampleOccurred: {}", event.getResult().getSampleLabel());
        // Forward to our delegate listener
        if (delegateListener != null) {
            delegateListener.sampleOccurred(event);
        }
        // Also call parent to maintain normal behavior
        super.sampleOccurred(event);
    }

    @Override
    public void sampleStarted(SampleEvent event) {
        if (delegateListener != null) {
            delegateListener.sampleStarted(event);
        }
        super.sampleStarted(event);
    }

    @Override
    public void sampleStopped(SampleEvent event) {
        if (delegateListener != null) {
            delegateListener.sampleStopped(event);
        }
        super.sampleStopped(event);
    }

    @Override
    public void testStarted() {
        log.info("Test started - CapturingResultCollector ready");
        super.testStarted();
    }

    @Override
    public void testStarted(String host) {
        log.info("Test started on host: {} - CapturingResultCollector ready", host);
        super.testStarted(host);
    }

    @Override
    public void testEnded() {
        log.info("Test ended");
        super.testEnded();
    }

    @Override
    public void testEnded(String host) {
        log.info("Test ended on host: {}", host);
        super.testEnded(host);
    }
}
