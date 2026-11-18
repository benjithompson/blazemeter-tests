package com.example.jmeter.copilot.core;

/**
 * Represents a parameter that may need correlation (extraction + substitution).
 */
public class CorrelationCandidate {
    
    public enum Location {
        QUERY_PARAM,
        FORM_PARAM,
        JSON_BODY,
        HEADER,
        COOKIE,
        PATH_SEGMENT,
        URL,
        GENERAL
    }
    
    public enum Confidence {
        HIGH,      // Definitely needs correlation (auth tokens, session IDs)
        MEDIUM,    // Likely needs correlation (dynamic IDs, timestamps from server)
        LOW        // May not need correlation (timestamps, analytics, cache busters)
    }
    
    public enum ExtractorType {
        JSON_PATH,
        REGEX,
        BOUNDARY,
        XPATH,
        CSS_SELECTOR
    }

    private final String parameterName;
    private final Location location;
    private final String sampleLabel;           // Which request uses this parameter
    private final int sampleIndex;
    private final String[] valuesAcrossRuns;    // The different values seen across runs
    
    // Source information (where to extract from)
    private String sourceLabel;                 // Which response contains the value
    private int sourceIndex;
    private String sourceContentType;           // Content-Type of the source response
    private String sourceResponseSnippet;       // Snippet of response containing the value
    private ExtractorType suggestedExtractor;
    private String extractorExpression;         // JSONPath, regex, etc.
    
    private Confidence confidence;
    private String reason;                      // Why this was flagged
    private boolean selected;                   // User selection for inclusion

    public CorrelationCandidate(String parameterName, Location location, String sampleLabel,
                                 int sampleIndex, String[] valuesAcrossRuns) {
        this.parameterName = parameterName;
        this.location = location;
        this.sampleLabel = sampleLabel;
        this.sampleIndex = sampleIndex;
        this.valuesAcrossRuns = valuesAcrossRuns;
        this.confidence = Confidence.MEDIUM;
        this.selected = false;
    }

    // Getters
    public String getParameterName() { return parameterName; }
    public Location getLocation() { return location; }
    public String getSampleLabel() { return sampleLabel; }
    public int getSampleIndex() { return sampleIndex; }
    public String[] getValuesAcrossRuns() { return valuesAcrossRuns; }
    public String getSourceLabel() { return sourceLabel; }
    public int getSourceIndex() { return sourceIndex; }
    public String getSourceContentType() { return sourceContentType; }
    public String getSourceResponseSnippet() { return sourceResponseSnippet; }
    public ExtractorType getSuggestedExtractor() { return suggestedExtractor; }
    public String getExtractorExpression() { return extractorExpression; }
    public Confidence getConfidence() { return confidence; }
    public String getReason() { return reason; }
    public boolean isSelected() { return selected; }

    // Setters
    public void setSourceLabel(String sourceLabel) { this.sourceLabel = sourceLabel; }
    public void setSourceIndex(int sourceIndex) { this.sourceIndex = sourceIndex; }
    public void setSourceContentType(String sourceContentType) { this.sourceContentType = sourceContentType; }
    public void setSourceResponseSnippet(String snippet) { this.sourceResponseSnippet = snippet; }
    public void setSuggestedExtractor(ExtractorType suggestedExtractor) { this.suggestedExtractor = suggestedExtractor; }
    public void setExtractorExpression(String extractorExpression) { this.extractorExpression = extractorExpression; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }
    public void setReason(String reason) { this.reason = reason; }
    public void setSelected(boolean selected) { this.selected = selected; }

    /**
     * Check if this looks like a token/session that definitely needs correlation.
     */
    public boolean looksLikeAuthToken() {
        String lower = parameterName.toLowerCase();
        return lower.contains("token") || lower.contains("auth") || lower.contains("session")
                || lower.contains("csrf") || lower.contains("xsrf") || lower.contains("bearer")
                || lower.contains("jwt") || lower.contains("apikey") || lower.contains("api_key");
    }

    /**
     * Check if this looks like something that probably doesn't need correlation.
     */
    public boolean looksLikeIgnorable() {
        String lower = parameterName.toLowerCase();
        return lower.contains("timestamp") || lower.contains("_ts") || lower.contains("time")
                || lower.contains("cachebust") || lower.contains("_cb") || lower.contains("rand")
                || lower.contains("nonce") || lower.contains("analytics") || lower.contains("tracking")
                || lower.equals("t") || lower.equals("_");
    }

    @Override
    public String toString() {
        return String.format("CorrelationCandidate[%s @ %s (%s) - %s]",
                parameterName, sampleLabel, location, confidence);
    }
}
