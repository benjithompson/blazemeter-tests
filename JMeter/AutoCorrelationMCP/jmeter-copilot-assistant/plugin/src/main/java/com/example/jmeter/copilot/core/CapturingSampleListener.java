package com.example.jmeter.copilot.core;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.samplers.SampleResult;

/**
 * Listens to sample results and captures full request/response data for correlation analysis.
 */
public class CapturingSampleListener implements SampleListener {

    private final RunCapture runCapture;
    private final AtomicInteger sampleCounter = new AtomicInteger(0);
    private Consumer<SampleResult> sampleCallback;

    public CapturingSampleListener(RunCapture runCapture) {
        this.runCapture = runCapture;
    }

    /**
     * Set a callback to be notified when a sample completes.
     * Useful for updating UI with real-time progress.
     */
    public void setSampleCallback(Consumer<SampleResult> callback) {
        this.sampleCallback = callback;
    }

    @Override
    public void sampleOccurred(SampleEvent event) {
        SampleResult result = event.getResult();
        if (result == null) return;

        int index = sampleCounter.getAndIncrement();
        SampleCapture capture = createCapture(result, index);
        runCapture.addSample(capture);

        // Notify callback
        if (sampleCallback != null) {
            sampleCallback.accept(result);
        }

        // Also capture sub-results (redirects, embedded resources)
        SampleResult[] subResults = result.getSubResults();
        if (subResults != null) {
            for (SampleResult sub : subResults) {
                int subIndex = sampleCounter.getAndIncrement();
                runCapture.addSample(createCapture(sub, subIndex));
            }
        }
    }

    @Override
    public void sampleStarted(SampleEvent event) {
        // Not needed
    }

    @Override
    public void sampleStopped(SampleEvent event) {
        // Not needed
    }

    private SampleCapture createCapture(SampleResult result, int index) {
        String url = result.getUrlAsString();
        String method = "GET";
        Map<String, String> requestHeaders = new LinkedHashMap<>();
        Map<String, String> requestParams = new LinkedHashMap<>();
        String requestBody = "";
        Map<String, String> responseHeaders = new LinkedHashMap<>();

        // Extract HTTP-specific data if available
        if (result instanceof HTTPSampleResult) {
            HTTPSampleResult httpResult = (HTTPSampleResult) result;
            method = httpResult.getHTTPMethod();
            
            // Parse request headers
            String reqHeaders = httpResult.getRequestHeaders();
            if (reqHeaders != null) {
                requestHeaders = parseHeaders(reqHeaders);
            }

            // Get query string parameters from URL
            if (url != null && url.contains("?")) {
                String query = url.substring(url.indexOf("?") + 1);
                requestParams.putAll(parseQueryString(query));
            }

            // Get POST data
            String postData = httpResult.getQueryString();
            if (postData != null && !postData.isEmpty()) {
                requestBody = postData;
                // If it looks like form data, parse it
                if (!postData.trim().startsWith("{") && !postData.trim().startsWith("<")) {
                    requestParams.putAll(parseQueryString(postData));
                }
            }
        }

        // Parse response headers
        String respHeaders = result.getResponseHeaders();
        if (respHeaders != null) {
            responseHeaders = parseHeaders(respHeaders);
        }

        String responseBody = result.getResponseDataAsString();
        String contentType = result.getContentType();
        int responseCode = 0;
        try {
            responseCode = Integer.parseInt(result.getResponseCode());
        } catch (NumberFormatException e) {
            // Ignore
        }

        return new SampleCapture(
                result.getSampleLabel(),
                index,
                url,
                method,
                requestHeaders,
                requestParams,
                requestBody,
                responseCode,
                responseHeaders,
                responseBody,
                contentType,
                result.getTimeStamp()
        );
    }

    private Map<String, String> parseHeaders(String headerString) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (headerString == null) return headers;

        String[] lines = headerString.split("\\r?\\n");
        for (String line : lines) {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String name = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(name, value);
            }
        }
        return headers;
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) return params;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int eqIndex = pair.indexOf('=');
            if (eqIndex > 0) {
                try {
                    String name = URLDecoder.decode(pair.substring(0, eqIndex), "UTF-8");
                    String value = eqIndex < pair.length() - 1 
                            ? URLDecoder.decode(pair.substring(eqIndex + 1), "UTF-8") 
                            : "";
                    params.put(name, value);
                } catch (UnsupportedEncodingException e) {
                    // Fallback without decoding
                    String name = pair.substring(0, eqIndex);
                    String value = eqIndex < pair.length() - 1 ? pair.substring(eqIndex + 1) : "";
                    params.put(name, value);
                }
            }
        }
        return params;
    }

    public RunCapture getRunCapture() {
        return runCapture;
    }
}
