package com.example.jmeter.copilot.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes multiple test runs to detect parameters that need correlation.
 */
public class CorrelationAnalyzer {

    private static final Pattern JSON_STRING_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_VALUE_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*([^,}\\]]+)");

    // Auth-related header names to proactively detect
    private static final Set<String> AUTH_HEADERS = new HashSet<>(Arrays.asList(
            "authorization", "x-auth-token", "x-access-token", "x-api-key", 
            "x-session-id", "x-csrf-token", "x-xsrf-token", "x-request-id",
            "bearer", "api-key", "session-id", "token"
    ));

    // Auth-related cookie names
    private static final Set<String> AUTH_COOKIES = new HashSet<>(Arrays.asList(
            "jsessionid", "phpsessid", "asp.net_sessionid", "connect.sid",
            "token", "session", "auth", "sid", "ssid", "csrf", "xsrf"
    ));

    // Auth-related parameter names  
    private static final Set<String> AUTH_PARAMS = new HashSet<>(Arrays.asList(
            "access_token", "accesstoken", "auth_token", "authtoken", "refresh_token",
            "id_token", "idtoken", "api_key", "apikey", "session_id", "sessionid",
            "csrf_token", "csrftoken", "xsrf_token", "_token", "token", "bearer",
            "nonce", "state", "code", "oauth_token", "jwt"
    ));

    // Auth-related JSON field names
    private static final Set<String> AUTH_JSON_FIELDS = new HashSet<>(Arrays.asList(
            "token", "accessToken", "access_token", "refreshToken", "refresh_token",
            "sessionId", "session_id", "authToken", "auth_token", "apiKey", "api_key",
            "idToken", "id_token", "csrfToken", "csrf_token", "bearer", "jwt",
            "authorization", "nonce", "state"
    ));

    /**
     * Analyze runs and find correlation candidates.
     * Works with 1 or more runs:
     * - Single run: Detects failed requests, auth patterns, and potential dynamic values
     * - Multiple runs: Also compares values across runs to find changing parameters
     */
    public List<CorrelationCandidate> analyzeRuns(List<RunCapture> runs) {
        List<CorrelationCandidate> candidates = new ArrayList<>();
        Set<String> foundCandidateKeys = new HashSet<>(); // Track to avoid duplicates

        if (runs == null || runs.isEmpty()) {
            return candidates;
        }

        RunCapture baseRun = runs.get(0);
        int sampleCount = baseRun.getSampleCount();

        // Single-run analysis: detect failed requests that may need correlation
        for (int sampleIdx = 0; sampleIdx < sampleCount; sampleIdx++) {
            SampleCapture sample = baseRun.getSampleByIndex(sampleIdx);
            if (sample == null) continue;

            // Check for failed requests - these often indicate correlation issues
            List<CorrelationCandidate> failedCandidates = findFailedRequestCandidates(sample, baseRun, sampleIdx);
            for (CorrelationCandidate c : failedCandidates) {
                String key = c.getSampleLabel() + ":" + c.getLocation() + ":" + c.getParameterName();
                if (!foundCandidateKeys.contains(key)) {
                    foundCandidateKeys.add(key);
                    candidates.add(c);
                }
            }
        }

        // Compare each sample across runs (if we have multiple runs)
        if (runs.size() >= 2) {
            for (int sampleIdx = 0; sampleIdx < sampleCount; sampleIdx++) {
                SampleCapture baseSample = baseRun.getSampleByIndex(sampleIdx);
                if (baseSample == null) continue;

                // Collect corresponding samples from other runs
                List<SampleCapture> correspondingSamples = new ArrayList<>();
                correspondingSamples.add(baseSample);
                
                for (int runIdx = 1; runIdx < runs.size(); runIdx++) {
                    SampleCapture otherSample = runs.get(runIdx).getSampleByIndex(sampleIdx);
                    if (otherSample != null) {
                        correspondingSamples.add(otherSample);
                    }
                }

                if (correspondingSamples.size() < 2) continue;

                // Find parameters that differ
                List<CorrelationCandidate> diffCandidates = new ArrayList<>();
                diffCandidates.addAll(findDifferingParameters(correspondingSamples, runs));
                diffCandidates.addAll(findDifferingHeaders(correspondingSamples, runs));
                diffCandidates.addAll(findDifferingJsonBody(correspondingSamples, runs));
                
                for (CorrelationCandidate c : diffCandidates) {
                    String key = c.getSampleLabel() + ":" + c.getLocation() + ":" + c.getParameterName();
                    if (!foundCandidateKeys.contains(key)) {
                        foundCandidateKeys.add(key);
                        candidates.add(c);
                    }
                }
            }
        }

        // Proactive auth detection: scan for auth patterns even if values don't change
        for (int sampleIdx = 0; sampleIdx < sampleCount; sampleIdx++) {
            SampleCapture sample = baseRun.getSampleByIndex(sampleIdx);
            if (sample == null) continue;

            List<CorrelationCandidate> authCandidates = findAuthPatterns(sample, runs);
            for (CorrelationCandidate c : authCandidates) {
                String key = c.getSampleLabel() + ":" + c.getLocation() + ":" + c.getParameterName();
                if (!foundCandidateKeys.contains(key)) {
                    foundCandidateKeys.add(key);
                    candidates.add(c);
                }
            }
        }

        // For each candidate, try to find the source (where to extract from)
        for (CorrelationCandidate candidate : candidates) {
            findExtractionSource(candidate, runs);
            assignConfidence(candidate);
        }

        return candidates;
    }

    /**
     * Find correlation candidates from a single run by analyzing failed requests.
     * This is useful for iterative correlation workflow where you run once,
     * identify failures, add correlations, and run again.
     * 
     * @param run The single run to analyze
     * @return List of correlation candidates based on failed requests
     */
    public List<CorrelationCandidate> findFailedRequestCandidates(RunCapture run) {
        List<CorrelationCandidate> candidates = new ArrayList<>();
        Set<String> foundCandidateKeys = new HashSet<>();
        
        List<SampleCapture> samples = run.getSamples();
        
        // First, find correlations by tracing values from responses to subsequent requests
        List<CorrelationCandidate> tracedCandidates = findCorrelationsByTracing(run);
        for (CorrelationCandidate c : tracedCandidates) {
            String key = c.getSampleLabel() + ":" + c.getLocation() + ":" + c.getParameterName();
            if (!foundCandidateKeys.contains(key)) {
                foundCandidateKeys.add(key);
                candidates.add(c);
            }
        }
        
        // Then look at failed requests
        for (int i = 0; i < samples.size(); i++) {
            SampleCapture sample = samples.get(i);
            List<CorrelationCandidate> failedCandidates = findFailedRequestCandidates(sample, run, i);
            for (CorrelationCandidate c : failedCandidates) {
                String key = c.getSampleLabel() + ":" + c.getLocation() + ":" + c.getParameterName();
                if (!foundCandidateKeys.contains(key)) {
                    foundCandidateKeys.add(key);
                    candidates.add(c);
                }
            }
        }
        
        // Assign confidence and source info
        List<RunCapture> singleRunList = new ArrayList<>();
        singleRunList.add(run);
        for (CorrelationCandidate candidate : candidates) {
            if (candidate.getSourceLabel() == null) {
                findExtractionSource(candidate, singleRunList);
            }
            assignConfidence(candidate);
        }
        
        return candidates;
    }

    /**
     * Find correlations by tracing dynamic values from responses to subsequent requests.
     * This is the main correlation detection - finding tokens in responses that are used later.
     */
    private List<CorrelationCandidate> findCorrelationsByTracing(RunCapture run) {
        List<CorrelationCandidate> candidates = new ArrayList<>();
        List<SampleCapture> samples = run.getSamples();
        
        // For each sample, extract potential dynamic values from its response
        for (int sourceIdx = 0; sourceIdx < samples.size(); sourceIdx++) {
            SampleCapture sourceSample = samples.get(sourceIdx);
            String responseBody = sourceSample.getResponseBody();
            
            if (responseBody == null || responseBody.isEmpty()) {
                continue;
            }
            
            // Extract potential tokens/dynamic values from JSON responses
            Map<String, String> extractedValues = new LinkedHashMap<>();
            
            // Parse JSON for token-like fields
            if (responseBody.trim().startsWith("{") || responseBody.trim().startsWith("[")) {
                extractedValues.putAll(extractTokensFromJson(responseBody));
            }
            
            // Also look for common token patterns in any response
            extractedValues.putAll(extractTokenPatterns(responseBody));
            
            // Now check if any of these values appear in subsequent requests
            for (Map.Entry<String, String> entry : extractedValues.entrySet()) {
                String fieldName = entry.getKey();
                String value = entry.getValue();
                
                // Skip very short values (less likely to be tokens)
                if (value == null || value.length() < 8) {
                    continue;
                }
                
                // Search for this value in subsequent requests
                for (int targetIdx = sourceIdx + 1; targetIdx < samples.size(); targetIdx++) {
                    SampleCapture targetSample = samples.get(targetIdx);
                    
                    // Check request headers
                    for (Map.Entry<String, String> header : targetSample.getRequestHeaders().entrySet()) {
                        if (header.getValue() != null && header.getValue().contains(value)) {
                            CorrelationCandidate candidate = createTracedCandidate(
                                fieldName, value, 
                                sourceSample, sourceIdx,
                                targetSample, targetIdx,
                                header.getKey(), CorrelationCandidate.Location.HEADER,
                                responseBody
                            );
                            candidates.add(candidate);
                        }
                    }
                    
                    // Check request body
                    String targetBody = targetSample.getRequestBody();
                    if (targetBody != null && targetBody.contains(value)) {
                        CorrelationCandidate candidate = createTracedCandidate(
                            fieldName, value,
                            sourceSample, sourceIdx,
                            targetSample, targetIdx,
                            fieldName, CorrelationCandidate.Location.JSON_BODY,
                            responseBody
                        );
                        candidates.add(candidate);
                    }
                    
                    // Check URL/query parameters
                    String url = targetSample.getUrl();
                    if (url != null && url.contains(value)) {
                        CorrelationCandidate candidate = createTracedCandidate(
                            fieldName, value,
                            sourceSample, sourceIdx,
                            targetSample, targetIdx,
                            fieldName, CorrelationCandidate.Location.URL,
                            responseBody
                        );
                        candidates.add(candidate);
                    }
                }
            }
        }
        
        return candidates;
    }
    
    /**
     * Extract token-like values from JSON response.
     */
    private Map<String, String> extractTokensFromJson(String json) {
        Map<String, String> tokens = new LinkedHashMap<>();
        
        // Match JSON string values
        Matcher matcher = JSON_STRING_PATTERN.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            
            // Check if the key suggests it's a token
            String keyLower = key.toLowerCase();
            if (keyLower.contains("token") || keyLower.contains("access") || 
                keyLower.contains("auth") || keyLower.contains("bearer") ||
                keyLower.contains("session") || keyLower.contains("csrf") ||
                keyLower.contains("xsrf") || keyLower.contains("jwt") ||
                keyLower.contains("key") || keyLower.contains("id_token") ||
                keyLower.contains("refresh") || keyLower.contains("nonce") ||
                keyLower.contains("code") || keyLower.contains("state")) {
                tokens.put(key, value);
            }
            
            // Also check if the value looks like a token (long alphanumeric string)
            if (value.length() >= 20 && value.matches("[A-Za-z0-9_\\-+=/.]+")) {
                tokens.put(key, value);
            }
        }
        
        return tokens;
    }
    
    /**
     * Extract token patterns from any response (regex-based).
     */
    private Map<String, String> extractTokenPatterns(String response) {
        Map<String, String> tokens = new LinkedHashMap<>();
        
        // JWT pattern
        Pattern jwtPattern = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
        Matcher jwtMatcher = jwtPattern.matcher(response);
        while (jwtMatcher.find()) {
            tokens.put("JWT_TOKEN", jwtMatcher.group());
        }
        
        // UUID pattern
        Pattern uuidPattern = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}", Pattern.CASE_INSENSITIVE);
        Matcher uuidMatcher = uuidPattern.matcher(response);
        int uuidCount = 0;
        while (uuidMatcher.find() && uuidCount < 5) {
            tokens.put("UUID_" + uuidCount, uuidMatcher.group());
            uuidCount++;
        }
        
        return tokens;
    }
    
    /**
     * Create a correlation candidate from traced data.
     */
    private CorrelationCandidate createTracedCandidate(
            String fieldName, String value,
            SampleCapture sourceSample, int sourceIdx,
            SampleCapture targetSample, int targetIdx,
            String parameterName, CorrelationCandidate.Location location,
            String sourceResponseBody) {
        
        CorrelationCandidate candidate = new CorrelationCandidate(
            parameterName,
            location,
            targetSample.getLabel(),
            targetIdx,
            new String[]{value}
        );
        
        candidate.setSourceLabel(sourceSample.getLabel());
        candidate.setSourceIndex(sourceIdx);
        candidate.setSourceContentType(sourceSample.getContentType());
        candidate.setReason("🔗 Value from '" + fieldName + "' in " + sourceSample.getLabel() + 
                          " response is used in " + targetSample.getLabel() + " " + location.name().toLowerCase());
        candidate.setConfidence(CorrelationCandidate.Confidence.HIGH);
        candidate.setSelected(true);
        
        // Set up extractor
        if (sourceSample.getContentType() != null && sourceSample.getContentType().contains("json")) {
            candidate.setSuggestedExtractor(CorrelationCandidate.ExtractorType.JSON_PATH);
            candidate.setExtractorExpression(findJsonPath(sourceResponseBody, value));
        } else {
            candidate.setSuggestedExtractor(CorrelationCandidate.ExtractorType.REGEX);
            candidate.setExtractorExpression(createRegexForValue(value));
        }
        
        // Create response snippet
        int valueIndex = sourceResponseBody.indexOf(value);
        if (valueIndex >= 0) {
            int snippetStart = Math.max(0, valueIndex - 50);
            int snippetEnd = Math.min(sourceResponseBody.length(), valueIndex + value.length() + 50);
            String snippet = sourceResponseBody.substring(snippetStart, snippetEnd);
            if (snippetStart > 0) snippet = "..." + snippet;
            if (snippetEnd < sourceResponseBody.length()) snippet = snippet + "...";
            candidate.setSourceResponseSnippet(snippet);
        }
        
        return candidate;
    }

    /**
     * Find correlation candidates from failed requests.
     * Failed requests (4xx, 5xx) often indicate missing or invalid dynamic values.
     */
    private List<CorrelationCandidate> findFailedRequestCandidates(SampleCapture sample, RunCapture run, int sampleIdx) {
        List<CorrelationCandidate> candidates = new ArrayList<>();
        
        int responseCode = sample.getResponseCode();
        
        // Check for failed requests
        boolean isFailed = responseCode >= 400 || responseCode == 0;
        boolean isAuthError = responseCode == 401 || responseCode == 403;
        boolean isNotFound = responseCode == 404;
        boolean isBadRequest = responseCode == 400;
        
        if (!isFailed) {
            return candidates; // Request succeeded, no correlation issues detected
        }

        String responseBody = sample.getResponseBody();
        
        // For auth errors, look for tokens/sessions in the request that may be stale
        if (isAuthError) {
            // Check for auth headers that might need correlation
            for (Map.Entry<String, String> header : sample.getRequestHeaders().entrySet()) {
                String headerName = header.getKey();
                if (isAuthHeader(headerName)) {
                    CorrelationCandidate candidate = new CorrelationCandidate(
                            headerName,
                            CorrelationCandidate.Location.HEADER,
                            sample.getLabel(),
                            sampleIdx,
                            new String[]{header.getValue()}
                    );
                    candidate.setReason("⚠️ Auth error (" + responseCode + ") - this auth value likely needs correlation");
                    candidate.setConfidence(CorrelationCandidate.Confidence.HIGH);
                    candidates.add(candidate);
                }
            }
            
            // Check for auth params
            for (Map.Entry<String, String> param : sample.getRequestParameters().entrySet()) {
                if (isAuthParam(param.getKey())) {
                    CorrelationCandidate candidate = new CorrelationCandidate(
                            param.getKey(),
                            CorrelationCandidate.Location.QUERY_PARAM,
                            sample.getLabel(),
                            sampleIdx,
                            new String[]{param.getValue()}
                    );
                    candidate.setReason("⚠️ Auth error (" + responseCode + ") - this parameter likely needs correlation");
                    candidate.setConfidence(CorrelationCandidate.Confidence.HIGH);
                    candidates.add(candidate);
                }
            }
        }
        
        // For bad requests, check error message for clues about which field is invalid
        if (isBadRequest && responseBody != null) {
            // Look for common error patterns in response
            String lowerBody = responseBody.toLowerCase();
            
            // Check if response mentions specific fields
            for (Map.Entry<String, String> param : sample.getRequestParameters().entrySet()) {
                String paramName = param.getKey();
                if (lowerBody.contains(paramName.toLowerCase()) || 
                    lowerBody.contains("invalid") || 
                    lowerBody.contains("expired") ||
                    lowerBody.contains("token")) {
                    CorrelationCandidate candidate = new CorrelationCandidate(
                            paramName,
                            CorrelationCandidate.Location.QUERY_PARAM,
                            sample.getLabel(),
                            sampleIdx,
                            new String[]{param.getValue()}
                    );
                    candidate.setReason("⚠️ Bad request (" + responseCode + ") - error response may indicate this needs correlation");
                    candidate.setConfidence(CorrelationCandidate.Confidence.MEDIUM);
                    candidates.add(candidate);
                    break; // Only add one to avoid flooding
                }
            }
        }

        // For 404 errors, check if there's a dynamic ID in the URL
        if (isNotFound) {
            String url = sample.getUrl();
            if (url != null) {
                // Look for UUID patterns or numeric IDs in URL path
                java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile(
                    "/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}|\\d{5,})/",
                    java.util.regex.Pattern.CASE_INSENSITIVE
                );
                java.util.regex.Matcher matcher = idPattern.matcher(url);
                if (matcher.find()) {
                    String idValue = matcher.group(1);
                    CorrelationCandidate candidate = new CorrelationCandidate(
                            "URL_PATH_ID",
                            CorrelationCandidate.Location.URL,
                            sample.getLabel(),
                            sampleIdx,
                            new String[]{idValue}
                    );
                    candidate.setReason("⚠️ Not found (" + responseCode + ") - URL contains ID that may need correlation: " + idValue);
                    candidate.setConfidence(CorrelationCandidate.Confidence.HIGH);
                    candidates.add(candidate);
                }
            }
        }

        // If request failed but we couldn't identify specific candidates, create a general one
        if (candidates.isEmpty() && isFailed) {
            CorrelationCandidate candidate = new CorrelationCandidate(
                    "FAILED_REQUEST",
                    CorrelationCandidate.Location.GENERAL,
                    sample.getLabel(),
                    sampleIdx,
                    new String[]{"Response code: " + responseCode}
            );
            candidate.setReason("❌ Request failed (" + responseCode + ") - review request for dynamic values");
            candidate.setConfidence(CorrelationCandidate.Confidence.MEDIUM);
            candidates.add(candidate);
        }

        return candidates;
    }

    /**
     * Proactively find auth-related patterns that may need correlation,
     * even if they didn't change between runs.
     */
    private List<CorrelationCandidate> findAuthPatterns(SampleCapture sample, List<RunCapture> runs) {
        List<CorrelationCandidate> candidates = new ArrayList<>();

        // Check headers for auth patterns
        for (Map.Entry<String, String> header : sample.getRequestHeaders().entrySet()) {
            String headerName = header.getKey();
            String headerValue = header.getValue();
            
            if (isAuthHeader(headerName) && headerValue != null && !headerValue.isEmpty()) {
                String[] values = new String[runs.size()];
                for (int i = 0; i < runs.size(); i++) {
                    SampleCapture s = runs.get(i).getSampleByIndex(sample.getSampleIndex());
                    values[i] = s != null ? s.getRequestHeaders().get(headerName) : null;
                }
                
                CorrelationCandidate candidate = new CorrelationCandidate(
                        headerName,
                        CorrelationCandidate.Location.HEADER,
                        sample.getLabel(),
                        sample.getSampleIndex(),
                        values
                );
                candidate.setReason("Auth header detected (may need correlation for future runs)");
                candidates.add(candidate);
            }

            // Check for Bearer tokens in Authorization header
            if ("authorization".equalsIgnoreCase(headerName) && headerValue != null 
                    && headerValue.toLowerCase().startsWith("bearer ")) {
                String[] values = new String[runs.size()];
                for (int i = 0; i < runs.size(); i++) {
                    SampleCapture s = runs.get(i).getSampleByIndex(sample.getSampleIndex());
                    values[i] = s != null ? s.getRequestHeaders().get(headerName) : null;
                }
                
                CorrelationCandidate candidate = new CorrelationCandidate(
                        "Authorization (Bearer)",
                        CorrelationCandidate.Location.HEADER,
                        sample.getLabel(),
                        sample.getSampleIndex(),
                        values
                );
                candidate.setReason("Bearer token detected (likely needs correlation)");
                candidates.add(candidate);
            }
        }

        // Check cookies for auth patterns
        String cookieHeader = sample.getRequestHeaders().get("Cookie");
        if (cookieHeader != null) {
            String[] cookies = cookieHeader.split(";");
            for (String cookie : cookies) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2) {
                    String cookieName = parts[0].trim();
                    if (isAuthCookie(cookieName)) {
                        CorrelationCandidate candidate = new CorrelationCandidate(
                                cookieName,
                                CorrelationCandidate.Location.COOKIE,
                                sample.getLabel(),
                                sample.getSampleIndex(),
                                new String[]{parts[1].trim()}
                        );
                        candidate.setReason("Session cookie detected (may need correlation)");
                        candidates.add(candidate);
                    }
                }
            }
        }

        // Check query parameters for auth patterns
        for (Map.Entry<String, String> param : sample.getRequestParameters().entrySet()) {
            if (isAuthParam(param.getKey()) && param.getValue() != null && !param.getValue().isEmpty()) {
                String[] values = new String[runs.size()];
                for (int i = 0; i < runs.size(); i++) {
                    SampleCapture s = runs.get(i).getSampleByIndex(sample.getSampleIndex());
                    values[i] = s != null ? s.getRequestParameters().get(param.getKey()) : null;
                }
                
                CorrelationCandidate candidate = new CorrelationCandidate(
                        param.getKey(),
                        CorrelationCandidate.Location.QUERY_PARAM,
                        sample.getLabel(),
                        sample.getSampleIndex(),
                        values
                );
                candidate.setReason("Auth parameter detected (may need correlation)");
                candidates.add(candidate);
            }
        }

        // Check JSON body for auth patterns
        String body = sample.getRequestBody();
        if (body != null && body.trim().startsWith("{")) {
            Map<String, String> jsonFields = extractJsonFields(body);
            for (Map.Entry<String, String> field : jsonFields.entrySet()) {
                if (isAuthJsonField(field.getKey()) && field.getValue() != null && !field.getValue().isEmpty()) {
                    String[] values = new String[runs.size()];
                    for (int i = 0; i < runs.size(); i++) {
                        SampleCapture s = runs.get(i).getSampleByIndex(sample.getSampleIndex());
                        if (s != null && s.getRequestBody() != null) {
                            Map<String, String> otherFields = extractJsonFields(s.getRequestBody());
                            values[i] = otherFields.get(field.getKey());
                        }
                    }
                    
                    CorrelationCandidate candidate = new CorrelationCandidate(
                            field.getKey(),
                            CorrelationCandidate.Location.JSON_BODY,
                            sample.getLabel(),
                            sample.getSampleIndex(),
                            values
                    );
                    candidate.setReason("Auth JSON field detected (may need correlation)");
                    candidates.add(candidate);
                }
            }
        }

        return candidates;
    }

    private boolean isAuthHeader(String headerName) {
        if (headerName == null) return false;
        String lower = headerName.toLowerCase();
        return AUTH_HEADERS.contains(lower) || 
               lower.contains("token") || 
               lower.contains("auth") || 
               lower.contains("session") ||
               lower.contains("csrf") ||
               lower.contains("api-key");
    }

    private boolean isAuthCookie(String cookieName) {
        if (cookieName == null) return false;
        String lower = cookieName.toLowerCase();
        for (String auth : AUTH_COOKIES) {
            if (lower.contains(auth)) return true;
        }
        return false;
    }

    private boolean isAuthParam(String paramName) {
        if (paramName == null) return false;
        String lower = paramName.toLowerCase().replace("-", "_");
        return AUTH_PARAMS.contains(lower) ||
               lower.contains("token") ||
               lower.contains("auth") ||
               lower.contains("session") ||
               lower.contains("csrf");
    }

    private boolean isAuthJsonField(String fieldName) {
        if (fieldName == null) return false;
        String lower = fieldName.toLowerCase();
        return AUTH_JSON_FIELDS.contains(fieldName) ||
               AUTH_JSON_FIELDS.contains(lower) ||
               lower.contains("token") ||
               lower.contains("auth") ||
               lower.contains("session");
    }

    private List<CorrelationCandidate> findDifferingParameters(List<SampleCapture> samples, List<RunCapture> runs) {
        List<CorrelationCandidate> candidates = new ArrayList<>();
        SampleCapture first = samples.get(0);

        // Get all parameter names from first sample
        Set<String> paramNames = new HashSet<>(first.getRequestParameters().keySet());

        for (String paramName : paramNames) {
            String[] values = new String[samples.size()];
            boolean differs = false;
            String firstValue = first.getRequestParameters().get(paramName);
            values[0] = firstValue;

            for (int i = 1; i < samples.size(); i++) {
                String value = samples.get(i).getRequestParameters().get(paramName);
                values[i] = value;
                if (!equals(firstValue, value)) {
                    differs = true;
                }
            }

            if (differs) {
                CorrelationCandidate candidate = new CorrelationCandidate(
                        paramName,
                        CorrelationCandidate.Location.QUERY_PARAM,
                        first.getLabel(),
                        first.getSampleIndex(),
                        values
                );
                candidate.setReason("Parameter value changes between runs");
                candidates.add(candidate);
            }
        }

        return candidates;
    }

    private List<CorrelationCandidate> findDifferingHeaders(List<SampleCapture> samples, List<RunCapture> runs) {
        List<CorrelationCandidate> candidates = new ArrayList<>();
        SampleCapture first = samples.get(0);

        // Check specific headers that commonly need correlation
        Set<String> headersToCheck = new HashSet<>();
        headersToCheck.add("Authorization");
        headersToCheck.add("X-CSRF-Token");
        headersToCheck.add("X-XSRF-TOKEN");
        headersToCheck.add("X-Auth-Token");
        headersToCheck.add("Cookie");

        // Also add any custom headers from the request
        for (String header : first.getRequestHeaders().keySet()) {
            if (header.toLowerCase().contains("token") || 
                header.toLowerCase().contains("auth") ||
                header.toLowerCase().contains("session")) {
                headersToCheck.add(header);
            }
        }

        for (String headerName : headersToCheck) {
            String[] values = new String[samples.size()];
            boolean differs = false;
            boolean hasValue = false;
            String firstValue = first.getRequestHeaders().get(headerName);
            values[0] = firstValue;

            for (int i = 1; i < samples.size(); i++) {
                String value = samples.get(i).getRequestHeaders().get(headerName);
                values[i] = value;
                if (value != null) hasValue = true;
                if (!equals(firstValue, value)) {
                    differs = true;
                }
            }

            if (differs && hasValue) {
                CorrelationCandidate candidate = new CorrelationCandidate(
                        headerName,
                        CorrelationCandidate.Location.HEADER,
                        first.getLabel(),
                        first.getSampleIndex(),
                        values
                );
                candidate.setReason("Header value changes between runs");
                candidates.add(candidate);
            }
        }

        return candidates;
    }

    private List<CorrelationCandidate> findDifferingJsonBody(List<SampleCapture> samples, List<RunCapture> runs) {
        List<CorrelationCandidate> candidates = new ArrayList<>();
        SampleCapture first = samples.get(0);
        String firstBody = first.getRequestBody();

        if (firstBody == null || !firstBody.trim().startsWith("{")) {
            return candidates; // Not JSON
        }

        // Extract all JSON fields from first sample
        Map<String, String> firstFields = extractJsonFields(firstBody);

        for (Map.Entry<String, String> entry : firstFields.entrySet()) {
            String fieldName = entry.getKey();
            String[] values = new String[samples.size()];
            boolean differs = false;
            values[0] = entry.getValue();

            for (int i = 1; i < samples.size(); i++) {
                Map<String, String> otherFields = extractJsonFields(samples.get(i).getRequestBody());
                String value = otherFields.get(fieldName);
                values[i] = value;
                if (!equals(values[0], value)) {
                    differs = true;
                }
            }

            if (differs) {
                CorrelationCandidate candidate = new CorrelationCandidate(
                        fieldName,
                        CorrelationCandidate.Location.JSON_BODY,
                        first.getLabel(),
                        first.getSampleIndex(),
                        values
                );
                candidate.setReason("JSON field value changes between runs");
                candidates.add(candidate);
            }
        }

        return candidates;
    }

    private Map<String, String> extractJsonFields(String json) {
        Map<String, String> fields = new HashMap<>();
        if (json == null) return fields;

        // Simple JSON field extraction (handles basic cases)
        Matcher stringMatcher = JSON_STRING_PATTERN.matcher(json);
        while (stringMatcher.find()) {
            fields.put(stringMatcher.group(1), stringMatcher.group(2));
        }

        return fields;
    }

    /**
     * Try to find where the dynamic value originates (which prior response contains it).
     */
    private void findExtractionSource(CorrelationCandidate candidate, List<RunCapture> runs) {
        if (runs.isEmpty()) return;

        RunCapture run = runs.get(0);
        String valueToFind = candidate.getValuesAcrossRuns()[0];
        if (valueToFind == null || valueToFind.isEmpty()) return;

        int targetSampleIndex = candidate.getSampleIndex();

        // Search in prior responses
        for (int i = 0; i < targetSampleIndex; i++) {
            SampleCapture sample = run.getSampleByIndex(i);
            if (sample == null) continue;

            // Check response body
            String responseBody = sample.getResponseBody();
            if (responseBody != null && responseBody.contains(valueToFind)) {
                candidate.setSourceLabel(sample.getLabel());
                candidate.setSourceIndex(i);
                candidate.setSourceContentType(sample.getContentType());
                
                // Extract a snippet of the response around the value for context
                int valueIndex = responseBody.indexOf(valueToFind);
                int snippetStart = Math.max(0, valueIndex - 100);
                int snippetEnd = Math.min(responseBody.length(), valueIndex + valueToFind.length() + 100);
                String snippet = responseBody.substring(snippetStart, snippetEnd);
                if (snippetStart > 0) snippet = "..." + snippet;
                if (snippetEnd < responseBody.length()) snippet = snippet + "...";
                candidate.setSourceResponseSnippet(snippet);

                // Determine extractor type based on content type
                if (sample.getContentType() != null && sample.getContentType().contains("json")) {
                    candidate.setSuggestedExtractor(CorrelationCandidate.ExtractorType.JSON_PATH);
                    candidate.setExtractorExpression(findJsonPath(responseBody, valueToFind));
                } else {
                    candidate.setSuggestedExtractor(CorrelationCandidate.ExtractorType.REGEX);
                    candidate.setExtractorExpression(createRegexForValue(valueToFind));
                }
                return;
            }

            // Check response headers
            for (Map.Entry<String, String> header : sample.getResponseHeaders().entrySet()) {
                if (header.getValue() != null && header.getValue().contains(valueToFind)) {
                    candidate.setSourceLabel(sample.getLabel());
                    candidate.setSourceIndex(i);
                    candidate.setSourceResponseSnippet("Header: " + header.getKey() + ": " + header.getValue());
                    candidate.setSuggestedExtractor(CorrelationCandidate.ExtractorType.REGEX);
                    candidate.setExtractorExpression(header.getKey() + ": (.+)");
                    return;
                }
            }
        }
    }

    private String findJsonPath(String json, String value) {
        // Simple JSONPath finder - looks for the key that contains this value
        Matcher matcher = JSON_STRING_PATTERN.matcher(json);
        while (matcher.find()) {
            if (value.equals(matcher.group(2))) {
                return "$." + matcher.group(1);
            }
        }
        return "$.[?(@==\"" + value + "\")]";
    }

    private String createRegexForValue(String value) {
        // Escape regex special characters and create a capturing group
        String escaped = Pattern.quote(value);
        return "(" + escaped + ")";
    }

    private void assignConfidence(CorrelationCandidate candidate) {
        if (candidate.looksLikeAuthToken()) {
            candidate.setConfidence(CorrelationCandidate.Confidence.HIGH);
            candidate.setReason(candidate.getReason() + " - Looks like authentication/session token");
            candidate.setSelected(true); // Auto-select high-confidence items
        } else if (candidate.looksLikeIgnorable()) {
            candidate.setConfidence(CorrelationCandidate.Confidence.LOW);
            candidate.setReason(candidate.getReason() + " - Likely timestamp/analytics (may not need correlation)");
            candidate.setSelected(false);
        } else if (candidate.getSourceLabel() != null) {
            candidate.setConfidence(CorrelationCandidate.Confidence.MEDIUM);
            candidate.setReason(candidate.getReason() + " - Source found in " + candidate.getSourceLabel());
            candidate.setSelected(true);
        } else {
            candidate.setConfidence(CorrelationCandidate.Confidence.LOW);
            candidate.setReason(candidate.getReason() + " - No source found (may be generated client-side)");
            candidate.setSelected(false);
        }
    }

    private boolean equals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
