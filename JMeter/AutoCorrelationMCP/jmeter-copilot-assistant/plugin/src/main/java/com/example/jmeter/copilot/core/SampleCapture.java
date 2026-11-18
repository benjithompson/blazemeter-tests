package com.example.jmeter.copilot.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Captures full request/response data from a JMeter sample for correlation analysis.
 */
public class SampleCapture {
    private final String label;
    private final int sampleIndex;
    private final String url;
    private final String method;
    private final Map<String, String> requestHeaders;
    private final Map<String, String> requestParameters;
    private final String requestBody;
    private final int responseCode;
    private final Map<String, String> responseHeaders;
    private final String responseBody;
    private final String contentType;
    private final long timestamp;

    public SampleCapture(String label, int sampleIndex, String url, String method,
                         Map<String, String> requestHeaders, Map<String, String> requestParameters,
                         String requestBody, int responseCode, Map<String, String> responseHeaders,
                         String responseBody, String contentType, long timestamp) {
        this.label = label;
        this.sampleIndex = sampleIndex;
        this.url = url;
        this.method = method;
        this.requestHeaders = requestHeaders != null ? new LinkedHashMap<>(requestHeaders) : new LinkedHashMap<>();
        this.requestParameters = requestParameters != null ? new LinkedHashMap<>(requestParameters) : new LinkedHashMap<>();
        this.requestBody = requestBody;
        this.responseCode = responseCode;
        this.responseHeaders = responseHeaders != null ? new LinkedHashMap<>(responseHeaders) : new LinkedHashMap<>();
        this.responseBody = responseBody;
        this.contentType = contentType;
        this.timestamp = timestamp;
    }

    public String getLabel() { return label; }
    public int getSampleIndex() { return sampleIndex; }
    public String getUrl() { return url; }
    public String getMethod() { return method; }
    public Map<String, String> getRequestHeaders() { return requestHeaders; }
    public Map<String, String> getRequestParameters() { return requestParameters; }
    public String getRequestBody() { return requestBody; }
    public int getResponseCode() { return responseCode; }
    public Map<String, String> getResponseHeaders() { return responseHeaders; }
    public String getResponseBody() { return responseBody; }
    public String getContentType() { return contentType; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("SampleCapture[%d: %s %s -> %d]", sampleIndex, method, label, responseCode);
    }
}
