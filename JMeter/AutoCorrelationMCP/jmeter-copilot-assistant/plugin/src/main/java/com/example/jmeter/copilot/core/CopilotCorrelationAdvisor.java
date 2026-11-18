package com.example.jmeter.copilot.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jmeter.copilot.auth.CopilotAuthManager;

/**
 * Uses Copilot/AI to intelligently analyze correlation candidates
 * and determine which are truly required vs optional.
 */
public class CopilotCorrelationAdvisor {

    private static final String COPILOT_COMPLETIONS_URL = "https://api.githubcopilot.com/chat/completions";
    
    // Available models - matches VS Code Copilot Chat models
    public static final String[] AVAILABLE_MODELS = {
        "gpt-5.2",
        "claude-opus-4.5",
        "claude-sonnet-4.5",
        "gemini-2.5-pro",
        "o4-mini",
        "o3",
        "gpt-4.1",
        "gpt-4o",
        "claude-sonnet-4",
        "o1"
    };
    
    private final CopilotAuthManager authManager;
    private String selectedModel = "claude-sonnet-4.5";

    public CopilotCorrelationAdvisor(CopilotAuthManager authManager) {
        this.authManager = authManager;
    }

    public void setModel(String model) {
        this.selectedModel = model;
    }

    public String getModel() {
        return selectedModel;
    }

    /**
     * Ask Copilot to analyze correlation candidates and provide recommendations.
     */
    public String analyzeCorrelationCandidates(List<CorrelationCandidate> candidates) throws IOException {
        if (!authManager.isAuthenticated()) {
            return "Not authenticated with Copilot. Please sign in first.";
        }

        if (candidates == null || candidates.isEmpty()) {
            return "No candidates to analyze.";
        }

        String token = authManager.getCopilotToken();
        String prompt = buildAnalysisPrompt(candidates);

        return callCopilotApi(token, prompt);
    }

    /**
     * Generate JMeter extractor XML elements for the selected correlations.
     */
    public String generateExtractorsXml(List<CorrelationCandidate> candidates, String currentJmx) throws IOException {
        if (!authManager.isAuthenticated()) {
            return "Not authenticated with Copilot. Please sign in first.";
        }

        if (candidates == null || candidates.isEmpty()) {
            return "No candidates selected.";
        }

        String token = authManager.getCopilotToken();
        String prompt = buildExtractorPrompt(candidates, currentJmx);

        return callCopilotApi(token, prompt);
    }

    /**
     * Generate JMeter extractor XML elements with streaming response.
     * @param candidates The correlation candidates to generate extractors for
     * @param currentJmx Optional current JMX content
     * @param streamCallback Callback that receives text chunks as they arrive
     * @return The complete response
     */
    public String generateExtractorsXmlStreaming(List<CorrelationCandidate> candidates, String currentJmx, 
                                                  Consumer<String> streamCallback) throws IOException {
        if (!authManager.isAuthenticated()) {
            String msg = "Not authenticated with Copilot. Please sign in first.";
            streamCallback.accept(msg);
            return msg;
        }

        if (candidates == null || candidates.isEmpty()) {
            String msg = "No candidates selected.";
            streamCallback.accept(msg);
            return msg;
        }

        String token = authManager.getCopilotToken();
        String prompt = buildExtractorPrompt(candidates, currentJmx);

        return callCopilotApiStreaming(token, prompt, streamCallback);
    }

    private String callCopilotApi(String token, String prompt) throws IOException {
        System.out.println("[CopilotAdvisor] Calling Copilot API with model: " + selectedModel);
        System.out.println("[CopilotAdvisor] Prompt length: " + prompt.length() + " chars");
        
        HttpURLConnection conn = (HttpURLConnection) new URL(COPILOT_COMPLETIONS_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Copilot-Integration-Id", "vscode-chat");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000); // Increase to 2 minutes for longer responses
        conn.setDoOutput(true);

        String requestBody = buildRequestBody(prompt);
        System.out.println("[CopilotAdvisor] Request body length: " + requestBody.length() + " chars");
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }
        System.out.println("[CopilotAdvisor] Request sent, waiting for response...");

        int responseCode = conn.getResponseCode();
        System.out.println("[CopilotAdvisor] Response code: " + responseCode);
        
        if (responseCode != 200) {
            String error = readResponse(conn, true);
            System.out.println("[CopilotAdvisor] Error response: " + error);
            throw new IOException("Copilot API error (" + responseCode + "): " + error);
        }

        String response = readResponse(conn, false);
        System.out.println("[CopilotAdvisor] Response length: " + response.length() + " chars");
        return extractContent(response);
    }

    /**
     * Call Copilot API with streaming enabled, calling the callback for each chunk.
     */
    private String callCopilotApiStreaming(String token, String prompt, Consumer<String> streamCallback) throws IOException {
        System.out.println("[CopilotAdvisor] Calling Copilot API (streaming) with model: " + selectedModel);
        
        HttpURLConnection conn = (HttpURLConnection) new URL(COPILOT_COMPLETIONS_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setRequestProperty("Copilot-Integration-Id", "vscode-chat");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);

        String requestBody = buildStreamingRequestBody(prompt);
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        System.out.println("[CopilotAdvisor] Streaming response code: " + responseCode);
        
        if (responseCode != 200) {
            String error = readResponse(conn, true);
            throw new IOException("Copilot API error (" + responseCode + "): " + error);
        }

        // Read streaming response (Server-Sent Events format)
        StringBuilder fullContent = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    // Extract content delta from: {"choices":[{"delta":{"content":"..."}}]}
                    String chunk = extractDeltaContent(data);
                    if (chunk != null && !chunk.isEmpty()) {
                        fullContent.append(chunk);
                        streamCallback.accept(chunk);
                    }
                }
            }
        }
        
        return fullContent.toString();
    }

    /**
     * Extract content from streaming delta: {"choices":[{"delta":{"content":"..."}}]}
     */
    private String extractDeltaContent(String jsonData) {
        // Look for "content" in delta object
        Pattern pattern = Pattern.compile("\"delta\"\\s*:\\s*\\{[^}]*\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher matcher = pattern.matcher(jsonData);
        if (matcher.find()) {
            String content = matcher.group(1);
            // Unescape JSON string (including unicode escapes)
            content = unescapeJsonString(content);
            return content;
        }
        return null;
    }

    /**
     * Properly unescape a JSON string, including unicode escapes like backslash-u003c
     */
    private String unescapeJsonString(String s) {
        if (s == null || s.isEmpty()) return s;
        
        StringBuilder result = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n': result.append('\n'); i += 2; break;
                    case 'r': result.append('\r'); i += 2; break;
                    case 't': result.append('\t'); i += 2; break;
                    case '"': result.append('"'); i += 2; break;
                    case '\\': result.append('\\'); i += 2; break;
                    case '/': result.append('/'); i += 2; break;
                    case 'b': result.append('\b'); i += 2; break;
                    case 'f': result.append('\f'); i += 2; break;
                    case 'u':
                        // Unicode escape sequence (backslash u followed by 4 hex digits)
                        if (i + 5 < s.length()) {
                            try {
                                String hex = s.substring(i + 2, i + 6);
                                int codePoint = Integer.parseInt(hex, 16);
                                result.append((char) codePoint);
                                i += 6;
                            } catch (NumberFormatException e) {
                                result.append(c);
                                i++;
                            }
                        } else {
                            result.append(c);
                            i++;
                        }
                        break;
                    default:
                        result.append(next);
                        i += 2;
                        break;
                }
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }

    private String buildAnalysisPrompt(List<CorrelationCandidate> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze these JMeter correlation candidates and classify each as:\n");
        sb.append("- REQUIRED: Must be correlated for the test to work (auth tokens, session IDs, CSRF tokens)\n");
        sb.append("- RECOMMENDED: Should be correlated for realistic simulation\n");
        sb.append("- OPTIONAL: Can be correlated but test may work without it\n");
        sb.append("- IGNORE: Should not be correlated (timestamps, analytics, cache busters)\n\n");
        sb.append("Candidates:\n");

        for (int i = 0; i < candidates.size(); i++) {
            CorrelationCandidate c = candidates.get(i);
            sb.append(String.format("%d. Parameter: %s\n", i + 1, c.getParameterName()));
            sb.append(String.format("   Location: %s in request '%s'\n", c.getLocation(), c.getSampleLabel()));
            sb.append(String.format("   Values across runs: %s\n", formatValues(c.getValuesAcrossRuns())));
            if (c.getSourceLabel() != null) {
                sb.append(String.format("   Source: Found in response from '%s'\n", c.getSourceLabel()));
            }
            sb.append("\n");
        }

        sb.append("\nFor each candidate, provide:\n");
        sb.append("1. Classification (REQUIRED/RECOMMENDED/OPTIONAL/IGNORE)\n");
        sb.append("2. Brief reason\n");
        sb.append("3. Suggested extractor expression if applicable\n");

        return sb.toString();
    }

    private String formatValues(String[] values) {
        if (values == null || values.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length && i < 3; i++) {
            if (i > 0) sb.append(", ");
            String v = values[i];
            if (v != null && v.length() > 30) {
                sb.append("\"").append(v.substring(0, 30)).append("...\"");
            } else {
                sb.append("\"").append(v).append("\"");
            }
        }
        if (values.length > 3) sb.append(", ...");
        sb.append("]");
        return sb.toString();
    }

    /**
     * Generate extractors and the modified JMX content.
     * Provides full JMX context to the AI so it can make intelligent edits.
     * @param candidates The correlation candidates
     * @param jmxFilePath Path to the JMX file for context
     * @param streamCallback Callback for streaming response
     * @return The AI response with JMX modifications
     */
    public String generateExtractorsForAutoApply(List<CorrelationCandidate> candidates, 
                                                  String jmxFilePath,
                                                  Consumer<String> streamCallback) throws IOException {
        if (!authManager.isAuthenticated()) {
            String msg = "Not authenticated with Copilot. Please sign in first.";
            streamCallback.accept(msg);
            return msg;
        }

        if (candidates == null || candidates.isEmpty()) {
            String msg = "No candidates selected.";
            streamCallback.accept(msg);
            return msg;
        }

        // Read the JMX file content
        String jmxContent = null;
        if (jmxFilePath != null) {
            try {
                jmxContent = new String(Files.readAllBytes(new File(jmxFilePath).toPath()), StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("[CopilotAdvisor] Could not read JMX file: " + e.getMessage());
            }
        }

        String token = authManager.getCopilotToken();
        String prompt = buildAutoApplyPrompt(candidates, jmxContent);

        return callCopilotApiStreaming(token, prompt, streamCallback);
    }

    /**
     * Generate a complete new JMX file with correlations applied.
     * The AI receives the full JMX and correlation candidates, and outputs a complete new JMX.
     * @param candidates The correlation candidates to apply
     * @param jmxContent The full current JMX content
     * @param streamCallback Callback that receives chunks of the new JMX as they're generated
     * @return The complete new JMX content
     */
    public String generateCorrelatedJmx(List<CorrelationCandidate> candidates, 
                                         String jmxContent,
                                         Consumer<String> streamCallback) throws IOException {
        if (!authManager.isAuthenticated()) {
            throw new IOException("Not authenticated with Copilot. Please sign in first.");
        }

        if (candidates == null || candidates.isEmpty()) {
            throw new IOException("No candidates selected.");
        }

        if (jmxContent == null || jmxContent.isEmpty()) {
            throw new IOException("No JMX content provided.");
        }

        String token = authManager.getCopilotToken();
        String prompt = buildCompleteJmxPrompt(candidates, jmxContent);

        return callCopilotApiStreaming(token, prompt, streamCallback);
    }

    /**
     * Build prompt that instructs AI to output a complete new JMX file with correlations.
     */
    private String buildCompleteJmxPrompt(List<CorrelationCandidate> candidates, String jmxContent) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("You are a JMeter expert. A copy of the original JMX file has been created.\n");
        sb.append("Your task is to MODIFY this copy by adding correlations and output the complete modified JMX.\n\n");
        
        sb.append("═══════════════════════════════════════════════════════════════════\n");
        sb.append("CORRELATION CANDIDATES TO APPLY\n");
        sb.append("═══════════════════════════════════════════════════════════════════\n\n");

        for (int i = 0; i < candidates.size(); i++) {
            CorrelationCandidate c = candidates.get(i);
            sb.append(String.format("CANDIDATE %d:\n", i + 1));
            sb.append(String.format("  Parameter: %s\n", c.getParameterName()));
            sb.append(String.format("  Used in request: %s\n", c.getSampleLabel()));
            sb.append(String.format("  Location: %s\n", c.getLocation()));
            
            if (c.getSourceLabel() != null) {
                sb.append(String.format("  Extract from response of: %s\n", c.getSourceLabel()));
            }
            if (c.getSourceContentType() != null) {
                sb.append(String.format("  Source Content-Type: %s\n", c.getSourceContentType()));
            }
            if (c.getSourceResponseSnippet() != null) {
                String snippet = c.getSourceResponseSnippet();
                if (snippet.length() > 500) snippet = snippet.substring(0, 500) + "...";
                sb.append(String.format("  Response snippet where value appears:\n%s\n", snippet));
            }
            
            String[] values = c.getValuesAcrossRuns();
            if (values != null && values.length > 0 && values[0] != null) {
                String sample = values[0].length() > 200 ? values[0].substring(0, 200) + "..." : values[0];
                sb.append(String.format("  Sample value to correlate: %s\n", sample));
            }
            sb.append("\n");
        }

        sb.append("═══════════════════════════════════════════════════════════════════\n");
        sb.append("CORRELATION RULES\n");
        sb.append("═══════════════════════════════════════════════════════════════════\n\n");
        
        sb.append("For each candidate:\n");
        sb.append("1. ADD an appropriate extractor (JSONPostProcessor, RegexExtractor, or HtmlExtractor)\n");
        sb.append("   - Place it INSIDE the <hashTree> that follows the source sampler\n");
        sb.append("   - Use descriptive variable names (e.g., auth_token, session_id)\n");
        sb.append("   - Add <hashTree/> after each extractor\n\n");
        
        sb.append("2. REPLACE the hardcoded value with ${variable_name}\n");
        sb.append("   - In headers: replace the value in <stringProp name=\"Header.value\">\n");
        sb.append("   - In parameters: replace the value in <stringProp name=\"Argument.value\">\n");
        sb.append("   - In body: replace the value wherever it appears\n\n");
        
        sb.append("EXTRACTOR EXAMPLES:\n\n");
        
        sb.append("JSON (for application/json responses):\n");
        sb.append("<JSONPostProcessor guiclass=\"JSONPostProcessorGui\" testclass=\"JSONPostProcessor\" testname=\"Extract token\" enabled=\"true\">\n");
        sb.append("  <stringProp name=\"JSONPostProcessor.referenceNames\">auth_token</stringProp>\n");
        sb.append("  <stringProp name=\"JSONPostProcessor.jsonPathExprs\">$.token</stringProp>\n");
        sb.append("  <stringProp name=\"JSONPostProcessor.match_numbers\">1</stringProp>\n");
        sb.append("  <stringProp name=\"JSONPostProcessor.defaultValues\">NOT_FOUND</stringProp>\n");
        sb.append("</JSONPostProcessor>\n");
        sb.append("<hashTree/>\n\n");
        
        sb.append("Regex (for headers or text):\n");
        sb.append("<RegexExtractor guiclass=\"RegexExtractorGui\" testclass=\"RegexExtractor\" testname=\"Extract session\" enabled=\"true\">\n");
        sb.append("  <stringProp name=\"RegexExtractor.useHeaders\">false</stringProp>\n");
        sb.append("  <stringProp name=\"RegexExtractor.refname\">session_id</stringProp>\n");
        sb.append("  <stringProp name=\"RegexExtractor.regex\">session[\"']?\\s*[:=]\\s*[\"']?([^\"'&amp;]+)</stringProp>\n");
        sb.append("  <stringProp name=\"RegexExtractor.template\">$1$</stringProp>\n");
        sb.append("  <stringProp name=\"RegexExtractor.default\">NOT_FOUND</stringProp>\n");
        sb.append("  <stringProp name=\"RegexExtractor.match_number\">1</stringProp>\n");
        sb.append("</RegexExtractor>\n");
        sb.append("<hashTree/>\n\n");

        sb.append("═══════════════════════════════════════════════════════════════════\n");
        sb.append("JMX FILE TO MODIFY\n");
        sb.append("═══════════════════════════════════════════════════════════════════\n\n");
        sb.append(jmxContent);
        sb.append("\n\n");

        sb.append("═══════════════════════════════════════════════════════════════════\n");
        sb.append("OUTPUT INSTRUCTIONS\n");
        sb.append("═══════════════════════════════════════════════════════════════════\n\n");
        
        sb.append("Output ONLY the complete MODIFIED JMX file. No explanations, no markdown code blocks.\n");
        sb.append("Start directly with <?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("The output must be a valid, complete JMX file that JMeter can open.\n");
        sb.append("The file above is your starting point - modify it by adding extractors and replacing hardcoded values.\n");
        sb.append("Preserve ALL existing elements exactly as they are, only ADD extractors and REPLACE the specific hardcoded values with variable references.\n\n");
        
        sb.append("BEGIN OUTPUT:\n");

        return sb.toString();
    }

    /**
     * Generate structured JSON output for direct JMX modification.
     * Returns a JSON object that can be parsed to apply changes.
     */
    public String generateStructuredCorrelations(List<CorrelationCandidate> candidates, 
                                                  String jmxFilePath) throws IOException {
        if (!authManager.isAuthenticated()) {
            throw new IOException("Not authenticated with Copilot. Please sign in first.");
        }

        if (candidates == null || candidates.isEmpty()) {
            throw new IOException("No candidates selected.");
        }

        // Read the JMX file content
        String jmxContent = null;
        if (jmxFilePath != null) {
            try {
                jmxContent = new String(Files.readAllBytes(new File(jmxFilePath).toPath()), StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("[CopilotAdvisor] Could not read JMX file: " + e.getMessage());
            }
        }

        String token = authManager.getCopilotToken();
        String prompt = buildStructuredPrompt(candidates, jmxContent);

        return callCopilotApi(token, prompt);
    }

    /**
     * Build a prompt that instructs the AI to return structured JSON for direct application.
     */
    private String buildStructuredPrompt(List<CorrelationCandidate> candidates, String jmxContent) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("You are a JMeter correlation expert. Analyze the correlation candidates and generate a JSON response with the EXACT changes needed.\n\n");
        
        sb.append("═══════════════════════════════════════════════════════════════════\n");
        sb.append("CORRELATION CANDIDATES\n");
        sb.append("═══════════════════════════════════════════════════════════════════\n\n");

        for (int i = 0; i < candidates.size(); i++) {
            CorrelationCandidate c = candidates.get(i);
            sb.append(String.format("CANDIDATE %d:\n", i + 1));
            sb.append(String.format("  Parameter Name: %s\n", c.getParameterName()));
            sb.append(String.format("  Used In Request: %s\n", c.getSampleLabel()));
            sb.append(String.format("  Location: %s\n", c.getLocation()));
            
            if (c.getSourceLabel() != null) {
                sb.append(String.format("  Extract From Response Of: %s\n", c.getSourceLabel()));
            }
            if (c.getSourceContentType() != null) {
                sb.append(String.format("  Source Content-Type: %s\n", c.getSourceContentType()));
            }
            if (c.getSourceResponseSnippet() != null) {
                String snippet = c.getSourceResponseSnippet();
                if (snippet.length() > 500) snippet = snippet.substring(0, 500) + "...";
                sb.append(String.format("  Response Context: %s\n", snippet));
            }
            
            String[] values = c.getValuesAcrossRuns();
            if (values != null && values.length > 0 && values[0] != null) {
                String sample = values[0].length() > 200 ? values[0].substring(0, 200) + "..." : values[0];
                sb.append(String.format("  Sample Value: %s\n", sample));
            }
            sb.append("\n");
        }

        if (jmxContent != null) {
            sb.append("═══════════════════════════════════════════════════════════════════\n");
            sb.append("JMX FILE CONTENT\n");
            sb.append("═══════════════════════════════════════════════════════════════════\n\n");
            
            if (jmxContent.length() > 20000) {
                sb.append(extractJmxStructure(jmxContent));
            } else {
                sb.append(jmxContent);
            }
            sb.append("\n\n");
        }

        sb.append("═══════════════════════════════════════════════════════════════════\n");
        sb.append("REQUIRED OUTPUT FORMAT (JSON ONLY)\n");
        sb.append("═══════════════════════════════════════════════════════════════════\n\n");
        
        sb.append("Return ONLY a JSON object (no markdown, no explanation) with this exact structure:\n\n");
        sb.append("{\n");
        sb.append("  \"correlations\": [\n");
        sb.append("    {\n");
        sb.append("      \"variableName\": \"descriptive_variable_name\",\n");
        sb.append("      \"extractorType\": \"JSONPostProcessor|RegexExtractor|HtmlExtractor|XPath2Extractor\",\n");
        sb.append("      \"addAfterSampler\": \"Exact Sampler Name Where Value Is Returned\",\n");
        sb.append("      \"extractorXml\": \"<complete XML element including closing tag and <hashTree/>>\",\n");
        sb.append("      \"replacements\": [\n");
        sb.append("        {\n");
        sb.append("          \"sampler\": \"Exact Sampler Name Where Value Is Used\",\n");
        sb.append("          \"location\": \"header|parameter|body\",\n");
        sb.append("          \"fieldName\": \"header or parameter name (optional for body)\",\n");
        sb.append("          \"oldValue\": \"exact value to find and replace\",\n");
        sb.append("          \"newValue\": \"${variableName}\"\n");
        sb.append("        }\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");

        sb.append("IMPORTANT RULES:\n");
        sb.append("1. Return ONLY the JSON object - no markdown code blocks, no explanations\n");
        sb.append("2. Sampler names must match EXACTLY as they appear in the JMX\n");
        sb.append("3. extractorXml must be complete, valid XML including the trailing <hashTree/>\n");
        sb.append("4. For JSON responses use JSONPostProcessor, for HTML use HtmlExtractor\n");
        sb.append("5. oldValue must match EXACTLY what's in the JMX file\n");
        sb.append("6. Use descriptive variable names (auth_token, session_id, csrf_token)\n\n");

        return sb.toString();
    }

    /**
     * Build prompt for auto-apply that generates directly applicable JMX XML modifications.
     * Includes full JMX content so the AI can make informed decisions.
     */
    private String buildAutoApplyPrompt(List<CorrelationCandidate> candidates, String jmxContent) {
        StringBuilder sb = new StringBuilder();
        
        // System prompt with best practices
        sb.append("You are a JMeter correlation expert. Your task is to generate the EXACT XML modifications needed to correlate dynamic values in a JMeter test plan.\n\n");
        
        sb.append("═══════════════════════════════════════════════════════════════════\n");
        sb.append("JMETER CORRELATION BEST PRACTICES (FOLLOW STRICTLY)\n");
        sb.append("═══════════════════════════════════════════════════════════════════\n\n");
        
        sb.append("1. EXTRACTOR SELECTION:\n");
        sb.append("   - JSON responses: Use JSONPostProcessor (most efficient, easiest to maintain)\n");
        sb.append("   - HTML responses: Use HtmlExtractor with CSS selectors (more readable than XPath)\n");
        sb.append("   - Response headers: Use RegexExtractor with useHeaders=true\n");
        sb.append("   - Other text: Use RegexExtractor as last resort\n\n");
        
        sb.append("2. EXTRACTOR PLACEMENT:\n");
        sb.append("   - Extractors go INSIDE the <hashTree> that follows the HTTPSamplerProxy\n");
        sb.append("   - Each extractor needs its own empty <hashTree/> after it\n");
        sb.append("   - Place extractors after the sampler that RETURNS the value\n\n");
        
        sb.append("3. VARIABLE NAMING:\n");
        sb.append("   - Use descriptive names: auth_token, session_id, csrf_token\n");
        sb.append("   - Avoid special characters except underscores\n");
        sb.append("   - Reference with ${variable_name} syntax\n\n");
        
        sb.append("4. EXTRACTOR SETTINGS:\n");
        sb.append("   - default value: NOT_FOUND (makes debugging easier)\n");
        sb.append("   - match_number: 1 (get first match)\n");
        sb.append("   - template: $1$ for regex (capture group 1)\n\n");
        
        sb.append("5. PERFORMANCE CONSIDERATIONS:\n");
        sb.append("   - JSON Path is faster than Regex for JSON\n");
        sb.append("   - CSS selectors are faster than XPath for HTML\n");
        sb.append("   - Avoid .* in regex when possible (use non-greedy .*?)\n\n");
        
        sb.append("═══════════════════════════════════════════════════════════════════\n");
        sb.append("CORRELATION CANDIDATES TO PROCESS\n");
        sb.append("═══════════════════════════════════════════════════════════════════\n\n");

        for (int i = 0; i < candidates.size(); i++) {
            CorrelationCandidate c = candidates.get(i);
            sb.append(String.format("CANDIDATE %d:\n", i + 1));
            sb.append(String.format("  Parameter Name: %s\n", c.getParameterName()));
            sb.append(String.format("  Used In Request: %s\n", c.getSampleLabel()));
            sb.append(String.format("  Location: %s\n", c.getLocation()));
            
            if (c.getSourceLabel() != null) {
                sb.append(String.format("  Extract From Response Of: %s\n", c.getSourceLabel()));
            }
            if (c.getSourceContentType() != null) {
                sb.append(String.format("  Source Content-Type: %s\n", c.getSourceContentType()));
            }
            if (c.getSourceResponseSnippet() != null) {
                String snippet = c.getSourceResponseSnippet();
                if (snippet.length() > 800) snippet = snippet.substring(0, 800) + "...";
                sb.append(String.format("  Response Context:\n```\n%s\n```\n", snippet));
            }
            
            String[] values = c.getValuesAcrossRuns();
            if (values != null && values.length > 0 && values[0] != null) {
                String sample = values[0].length() > 300 ? values[0].substring(0, 300) + "..." : values[0];
                sb.append(String.format("  Sample Value: %s\n", sample));
            }
            sb.append("\n");
        }

        // Include JMX content if available (truncated to avoid token limits)
        if (jmxContent != null) {
            sb.append("═══════════════════════════════════════════════════════════════════\n");
            sb.append("CURRENT JMX FILE CONTENT (FOR CONTEXT)\n");
            sb.append("═══════════════════════════════════════════════════════════════════\n\n");
            
            // Truncate if too long, but try to include sampler names and structure
            if (jmxContent.length() > 15000) {
                sb.append("Note: JMX file truncated for context. Showing structure...\n\n");
                sb.append(extractJmxStructure(jmxContent));
            } else {
                sb.append("```xml\n");
                sb.append(jmxContent);
                sb.append("\n```\n");
            }
            sb.append("\n");
        }

        sb.append("═══════════════════════════════════════════════════════════════════\n");
        sb.append("YOUR OUTPUT FORMAT (FOLLOW EXACTLY)\n");
        sb.append("═══════════════════════════════════════════════════════════════════\n\n");
        
        sb.append("For EACH candidate, provide:\n\n");
        
        sb.append("### STEP 1: Add Extractor\n");
        sb.append("Show the COMPLETE XML element to add. Use this format:\n\n");
        sb.append("```xml\n");
        sb.append("<!-- ADD AFTER SAMPLER: [exact sampler name] -->\n");
        sb.append("<JSONPostProcessor ...>\n");
        sb.append("  ...\n");
        sb.append("</JSONPostProcessor>\n");
        sb.append("<hashTree/>\n");
        sb.append("```\n\n");
        
        sb.append("### STEP 2: Update Variable Reference\n");
        sb.append("Show the OLD value and NEW value for the replacement:\n\n");
        sb.append("```\n");
        sb.append("SAMPLER: [exact sampler name]\n");
        sb.append("LOCATION: [header/parameter/body]\n");
        sb.append("FIELD: [exact field name]\n");
        sb.append("OLD VALUE: [the hardcoded value to replace]\n");
        sb.append("NEW VALUE: ${variable_name}\n");
        sb.append("```\n\n");
        
        sb.append("═══════════════════════════════════════════════════════════════════\n");
        sb.append("IMPORTANT REMINDERS\n");
        sb.append("═══════════════════════════════════════════════════════════════════\n\n");
        sb.append("- Preserve ALL existing content in the JMX - only ADD extractors and UPDATE references\n");
        sb.append("- Never remove or empty header/parameter values - REPLACE them with ${variable}\n");
        sb.append("- Be precise with sampler names - they must match EXACTLY\n");
        sb.append("- For headers, the variable reference goes in Header.value, not Header.name\n");
        sb.append("- For JSON body updates, show the exact JSON path where the value appears\n\n");
        
        sb.append("Now generate the correlation modifications:\n");

        return sb.toString();
    }

    /**
     * Extract a summary of JMX structure for large files.
     */
    private String extractJmxStructure(String jmxContent) {
        StringBuilder structure = new StringBuilder();
        
        // Extract sampler names
        Pattern samplerPattern = Pattern.compile("<HTTPSamplerProxy[^>]*testname=\"([^\"]+)\"");
        Matcher matcher = samplerPattern.matcher(jmxContent);
        
        structure.append("HTTP Samplers in order:\n");
        int count = 0;
        while (matcher.find() && count < 50) {
            structure.append("  ").append(++count).append(". ").append(matcher.group(1)).append("\n");
        }
        
        // Extract a sample of the structure around first few samplers
        structure.append("\nSample JMX structure (first sampler with hashTree):\n```xml\n");
        int firstSamplerStart = jmxContent.indexOf("<HTTPSamplerProxy");
        if (firstSamplerStart > 0) {
            int contextEnd = Math.min(firstSamplerStart + 3000, jmxContent.length());
            structure.append(jmxContent.substring(firstSamplerStart, contextEnd));
            structure.append("\n... (truncated)\n```\n");
        }
        
        return structure.toString();
    }

    private String buildExtractorPrompt(List<CorrelationCandidate> candidates, String currentJmx) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a JMeter expert. Generate JMeter post-processor extractors for these correlation candidates.\n\n");
        sb.append("For each candidate, generate the appropriate extractor:\n");
        sb.append("- For JSON responses: use JSON Extractor (JSONPostProcessor)\n");
        sb.append("- For HTML/XML: use XPath Extractor or CSS/jQuery Extractor\n");
        sb.append("- For other text: use Regular Expression Extractor\n\n");
        sb.append("Candidates to correlate:\n");

        for (int i = 0; i < candidates.size(); i++) {
            CorrelationCandidate c = candidates.get(i);
            sb.append(String.format("%d. Parameter: %s\n", i + 1, c.getParameterName()));
            sb.append(String.format("   Location: %s in request '%s'\n", c.getLocation(), c.getSampleLabel()));
            if (c.getSourceLabel() != null) {
                sb.append(String.format("   Extract from response of: '%s'\n", c.getSourceLabel()));
            }
            if (c.getSourceContentType() != null) {
                sb.append(String.format("   Content-Type: %s\n", c.getSourceContentType()));
            }
            String[] values = c.getValuesAcrossRuns();
            if (values != null && values.length > 0 && values[0] != null) {
                String sample = values[0].length() > 100 ? values[0].substring(0, 100) + "..." : values[0];
                sb.append(String.format("   Sample value: %s\n", sample));
            }
            sb.append("\n");
        }

        sb.append("\nProvide step-by-step instructions on how to add these extractors to the JMeter test plan.\n");
        sb.append("Include the complete XML elements for each extractor that can be added to the JMX file.\n");
        sb.append("Also explain how to update the requests to use the extracted variables (using ${variableName} syntax).\n");

        return sb.toString();
    }

    private String buildRequestBody(String prompt) {
        // Build JSON manually to avoid dependencies
        String escapedPrompt = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

        return "{" +
                "\"messages\":[{\"role\":\"user\",\"content\":\"" + escapedPrompt + "\"}]," +
                "\"model\":\"" + selectedModel + "\"," +
                "\"temperature\":0.3," +
                "\"max_tokens\":4000" +
                "}";
    }

    private String buildStreamingRequestBody(String prompt) {
        // Same as buildRequestBody but with stream:true
        String escapedPrompt = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

        return "{" +
                "\"messages\":[{\"role\":\"user\",\"content\":\"" + escapedPrompt + "\"}]," +
                "\"model\":\"" + selectedModel + "\"," +
                "\"temperature\":0.3," +
                "\"max_tokens\":4000," +
                "\"stream\":true" +
                "}";
    }

    private String readResponse(HttpURLConnection conn, boolean isError) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        isError ? conn.getErrorStream() : conn.getInputStream(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private String extractContent(String jsonResponse) {
        // Extract content from: {"choices":[{"message":{"content":"..."}}]}
        Pattern pattern = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher matcher = pattern.matcher(jsonResponse);
        if (matcher.find()) {
            String content = matcher.group(1);
            // Unescape
            return content
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return "Could not parse Copilot response";
    }
}
