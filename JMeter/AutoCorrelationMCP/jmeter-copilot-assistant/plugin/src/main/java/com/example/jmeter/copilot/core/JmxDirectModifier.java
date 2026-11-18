package com.example.jmeter.copilot.core;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Direct JMX file modifier that applies AI-generated correlation changes.
 * Uses string-based manipulation to preserve XML structure exactly.
 */
public class JmxDirectModifier {

    private String jmxContent;
    private final File jmxFile;
    private final List<String> appliedChanges = new ArrayList<>();
    private final List<String> failedChanges = new ArrayList<>();

    public JmxDirectModifier(File jmxFile) throws IOException {
        this.jmxFile = jmxFile;
        this.jmxContent = new String(Files.readAllBytes(jmxFile.toPath()), StandardCharsets.UTF_8);
    }

    public String getContent() {
        return jmxContent;
    }

    /**
     * Add an extractor after a specific sampler.
     * @param samplerName The exact name of the sampler to add the extractor after
     * @param extractorXml The complete extractor XML (including the trailing hashTree)
     * @return true if successful
     */
    public boolean addExtractorAfterSampler(String samplerName, String extractorXml) {
        // Find the sampler by name
        String samplerPattern = "<HTTPSamplerProxy[^>]*testname=\"" + Pattern.quote(samplerName) + "\"";
        Pattern pattern = Pattern.compile(samplerPattern);
        Matcher matcher = pattern.matcher(jmxContent);
        
        if (!matcher.find()) {
            failedChanges.add("Could not find sampler: " + samplerName);
            return false;
        }
        
        int samplerStart = matcher.start();
        
        // Find the closing </HTTPSamplerProxy> tag
        int samplerEnd = jmxContent.indexOf("</HTTPSamplerProxy>", samplerStart);
        if (samplerEnd == -1) {
            // Try self-closing tag
            int selfClose = jmxContent.indexOf("/>", samplerStart);
            if (selfClose != -1 && selfClose < samplerStart + 5000) {
                samplerEnd = selfClose + 2;
            } else {
                failedChanges.add("Could not find end of sampler: " + samplerName);
                return false;
            }
        } else {
            samplerEnd = samplerEnd + "</HTTPSamplerProxy>".length();
        }
        
        // Find the <hashTree> that follows the sampler
        int hashTreeStart = jmxContent.indexOf("<hashTree>", samplerEnd);
        int emptyHashTree = jmxContent.indexOf("<hashTree/>", samplerEnd);
        
        // Determine which hashTree format is used and find insertion point
        int insertionPoint = -1;
        
        if (hashTreeStart != -1 && (emptyHashTree == -1 || hashTreeStart < emptyHashTree)) {
            // <hashTree> ... </hashTree> format
            // Insert right after <hashTree>
            insertionPoint = hashTreeStart + "<hashTree>".length();
        } else if (emptyHashTree != -1 && emptyHashTree < samplerEnd + 200) {
            // <hashTree/> format - need to expand it
            // Replace <hashTree/> with <hashTree>\n{extractor}\n</hashTree>
            String before = jmxContent.substring(0, emptyHashTree);
            String after = jmxContent.substring(emptyHashTree + "<hashTree/>".length());
            
            // Get indentation
            String indent = getIndentation(jmxContent, emptyHashTree);
            
            jmxContent = before + "<hashTree>\n" + indent + "  " + extractorXml.trim() + "\n" + indent + "</hashTree>" + after;
            appliedChanges.add("Added extractor after sampler: " + samplerName);
            return true;
        }
        
        if (insertionPoint == -1) {
            // Look harder - maybe the hashTree is further away
            hashTreeStart = jmxContent.indexOf("<hashTree", samplerEnd);
            if (hashTreeStart != -1 && hashTreeStart < samplerEnd + 500) {
                if (jmxContent.charAt(hashTreeStart + 9) == '>') {
                    insertionPoint = hashTreeStart + "<hashTree>".length();
                } else if (jmxContent.charAt(hashTreeStart + 9) == '/') {
                    // Self-closing
                    String before = jmxContent.substring(0, hashTreeStart);
                    String after = jmxContent.substring(hashTreeStart + "<hashTree/>".length());
                    String indent = getIndentation(jmxContent, hashTreeStart);
                    jmxContent = before + "<hashTree>\n" + indent + "  " + extractorXml.trim() + "\n" + indent + "</hashTree>" + after;
                    appliedChanges.add("Added extractor after sampler: " + samplerName);
                    return true;
                }
            }
        }
        
        if (insertionPoint == -1) {
            failedChanges.add("Could not find hashTree after sampler: " + samplerName);
            return false;
        }
        
        // Insert the extractor XML
        String indent = getIndentation(jmxContent, insertionPoint);
        String formattedExtractor = "\n" + indent + "  " + extractorXml.trim().replace("\n", "\n" + indent + "  ");
        
        String before = jmxContent.substring(0, insertionPoint);
        String after = jmxContent.substring(insertionPoint);
        jmxContent = before + formattedExtractor + after;
        
        appliedChanges.add("Added extractor after sampler: " + samplerName);
        return true;
    }

    /**
     * Replace a hardcoded value with a variable reference in a specific sampler's parameter.
     */
    public boolean replaceParameterValue(String samplerName, String paramName, String oldValue, String newValue) {
        // Find the sampler
        String samplerPattern = "<HTTPSamplerProxy[^>]*testname=\"" + Pattern.quote(samplerName) + "\"";
        Pattern pattern = Pattern.compile(samplerPattern);
        Matcher matcher = pattern.matcher(jmxContent);
        
        if (!matcher.find()) {
            failedChanges.add("Could not find sampler for parameter replacement: " + samplerName);
            return false;
        }
        
        int samplerStart = matcher.start();
        int samplerEnd = findSamplerEnd(samplerStart);
        
        if (samplerEnd == -1) {
            failedChanges.add("Could not find end of sampler: " + samplerName);
            return false;
        }
        
        String samplerContent = jmxContent.substring(samplerStart, samplerEnd);
        
        // Find the parameter by name
        String paramPattern = "<elementProp[^>]*name=\"" + Pattern.quote(paramName) + "\"";
        Pattern pPattern = Pattern.compile(paramPattern);
        Matcher pMatcher = pPattern.matcher(samplerContent);
        
        if (!pMatcher.find()) {
            // Try alternate pattern for HTTPArgument
            paramPattern = "<stringProp name=\"Argument\\.name\">" + Pattern.quote(paramName) + "</stringProp>";
            if (!samplerContent.contains(paramName)) {
                failedChanges.add("Could not find parameter '" + paramName + "' in sampler: " + samplerName);
                return false;
            }
        }
        
        // Find and replace the value
        // Pattern: <stringProp name="Argument.value">VALUE</stringProp>
        String valuePattern = "(<stringProp name=\"Argument\\.value\">)" + Pattern.quote(oldValue) + "(</stringProp>)";
        Pattern vPattern = Pattern.compile(valuePattern);
        Matcher vMatcher = vPattern.matcher(samplerContent);
        
        if (vMatcher.find()) {
            String newSamplerContent = samplerContent.substring(0, vMatcher.start()) +
                    vMatcher.group(1) + newValue + vMatcher.group(2) +
                    samplerContent.substring(vMatcher.end());
            
            jmxContent = jmxContent.substring(0, samplerStart) + newSamplerContent + jmxContent.substring(samplerEnd);
            appliedChanges.add("Replaced parameter '" + paramName + "' value in sampler: " + samplerName);
            return true;
        }
        
        // Try simpler replacement within the sampler section
        if (samplerContent.contains(oldValue)) {
            String newSamplerContent = samplerContent.replace(oldValue, newValue);
            jmxContent = jmxContent.substring(0, samplerStart) + newSamplerContent + jmxContent.substring(samplerEnd);
            appliedChanges.add("Replaced value in sampler: " + samplerName);
            return true;
        }
        
        failedChanges.add("Could not find value to replace in parameter '" + paramName + "' of sampler: " + samplerName);
        return false;
    }

    /**
     * Replace a hardcoded value with a variable reference in a specific sampler's header.
     */
    public boolean replaceHeaderValue(String samplerName, String headerName, String oldValue, String newValue) {
        // Find the sampler
        String samplerPattern = "<HTTPSamplerProxy[^>]*testname=\"" + Pattern.quote(samplerName) + "\"";
        Pattern pattern = Pattern.compile(samplerPattern);
        Matcher matcher = pattern.matcher(jmxContent);
        
        if (!matcher.find()) {
            failedChanges.add("Could not find sampler for header replacement: " + samplerName);
            return false;
        }
        
        int samplerStart = matcher.start();
        
        // Find the HeaderManager that belongs to this sampler (in the hashTree after the sampler)
        int samplerEnd = findSamplerEnd(samplerStart);
        int hashTreeStart = jmxContent.indexOf("<hashTree>", samplerEnd);
        
        if (hashTreeStart == -1 || hashTreeStart > samplerEnd + 200) {
            hashTreeStart = jmxContent.indexOf("<hashTree/>", samplerEnd);
        }
        
        // Look for HeaderManager in the parent hashTree or within sampler's hashTree
        int headerMgrStart = jmxContent.indexOf("<HeaderManager", samplerStart);
        if (headerMgrStart == -1) {
            // Maybe it's before the sampler in the same thread group
            headerMgrStart = jmxContent.lastIndexOf("<HeaderManager", samplerStart);
        }
        
        if (headerMgrStart == -1) {
            failedChanges.add("Could not find HeaderManager for sampler: " + samplerName);
            return false;
        }
        
        int headerMgrEnd = jmxContent.indexOf("</HeaderManager>", headerMgrStart);
        if (headerMgrEnd == -1) {
            failedChanges.add("Could not find end of HeaderManager");
            return false;
        }
        headerMgrEnd += "</HeaderManager>".length();
        
        String headerMgrContent = jmxContent.substring(headerMgrStart, headerMgrEnd);
        
        // Find the specific header
        String headerPattern = "<elementProp[^>]*name=\"" + Pattern.quote(headerName) + "\"[^>]*>";
        Pattern hPattern = Pattern.compile(headerPattern);
        Matcher hMatcher = hPattern.matcher(headerMgrContent);
        
        if (!hMatcher.find()) {
            // Try with Header. prefix
            headerPattern = "<stringProp name=\"Header\\.name\">" + Pattern.quote(headerName) + "</stringProp>";
            if (!headerMgrContent.contains(headerName)) {
                failedChanges.add("Could not find header '" + headerName + "' in HeaderManager");
                return false;
            }
        }
        
        // Replace the value
        // Pattern: <stringProp name="Header.value">VALUE</stringProp>
        String valuePattern = "(<stringProp name=\"Header\\.value\">)" + Pattern.quote(oldValue) + "(</stringProp>)";
        Pattern vPattern = Pattern.compile(valuePattern);
        Matcher vMatcher = vPattern.matcher(headerMgrContent);
        
        if (vMatcher.find()) {
            String newHeaderMgrContent = headerMgrContent.substring(0, vMatcher.start()) +
                    vMatcher.group(1) + newValue + vMatcher.group(2) +
                    headerMgrContent.substring(vMatcher.end());
            
            jmxContent = jmxContent.substring(0, headerMgrStart) + newHeaderMgrContent + jmxContent.substring(headerMgrEnd);
            appliedChanges.add("Replaced header '" + headerName + "' value with: " + newValue);
            return true;
        }
        
        // Try simpler replacement
        if (headerMgrContent.contains(oldValue)) {
            String newHeaderMgrContent = headerMgrContent.replace(oldValue, newValue);
            jmxContent = jmxContent.substring(0, headerMgrStart) + newHeaderMgrContent + jmxContent.substring(headerMgrEnd);
            appliedChanges.add("Replaced header value in HeaderManager");
            return true;
        }
        
        failedChanges.add("Could not find value to replace in header '" + headerName + "'");
        return false;
    }

    /**
     * Replace a value anywhere it appears in a sampler's body.
     */
    public boolean replaceBodyValue(String samplerName, String oldValue, String newValue) {
        String samplerPattern = "<HTTPSamplerProxy[^>]*testname=\"" + Pattern.quote(samplerName) + "\"";
        Pattern pattern = Pattern.compile(samplerPattern);
        Matcher matcher = pattern.matcher(jmxContent);
        
        if (!matcher.find()) {
            failedChanges.add("Could not find sampler for body replacement: " + samplerName);
            return false;
        }
        
        int samplerStart = matcher.start();
        int samplerEnd = findSamplerEnd(samplerStart);
        
        if (samplerEnd == -1) {
            failedChanges.add("Could not find end of sampler: " + samplerName);
            return false;
        }
        
        String samplerContent = jmxContent.substring(samplerStart, samplerEnd);
        
        if (samplerContent.contains(oldValue)) {
            String newSamplerContent = samplerContent.replace(oldValue, newValue);
            jmxContent = jmxContent.substring(0, samplerStart) + newSamplerContent + jmxContent.substring(samplerEnd);
            appliedChanges.add("Replaced body value in sampler: " + samplerName);
            return true;
        }
        
        failedChanges.add("Could not find value to replace in body of sampler: " + samplerName);
        return false;
    }

    /**
     * Generic value replacement anywhere in the JMX (use with caution).
     */
    public boolean replaceGlobalValue(String oldValue, String newValue) {
        if (jmxContent.contains(oldValue)) {
            jmxContent = jmxContent.replace(oldValue, newValue);
            appliedChanges.add("Replaced global value: " + truncate(oldValue, 50) + " -> " + newValue);
            return true;
        }
        failedChanges.add("Could not find value to replace globally: " + truncate(oldValue, 50));
        return false;
    }

    private int findSamplerEnd(int samplerStart) {
        int closingTag = jmxContent.indexOf("</HTTPSamplerProxy>", samplerStart);
        if (closingTag != -1) {
            return closingTag + "</HTTPSamplerProxy>".length();
        }
        // Try self-closing
        int selfClose = jmxContent.indexOf("/>", samplerStart);
        if (selfClose != -1 && selfClose < samplerStart + 10000) {
            // Make sure this /> belongs to the sampler
            int nextOpen = jmxContent.indexOf("<", samplerStart + 1);
            if (nextOpen == -1 || selfClose < nextOpen) {
                return selfClose + 2;
            }
        }
        return -1;
    }

    private String getIndentation(String content, int position) {
        int lineStart = content.lastIndexOf('\n', position);
        if (lineStart == -1) return "";
        
        StringBuilder indent = new StringBuilder();
        for (int i = lineStart + 1; i < position && i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == ' ' || c == '\t') {
                indent.append(c);
            } else {
                break;
            }
        }
        return indent.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /**
     * Save the modified JMX file.
     * Creates a backup first.
     */
    public void save() throws IOException {
        // Create backup
        File backup = new File(jmxFile.getAbsolutePath() + ".backup");
        if (backup.exists()) {
            // Keep the original backup, create numbered backup
            int i = 1;
            while (backup.exists()) {
                backup = new File(jmxFile.getAbsolutePath() + ".backup." + i++);
            }
        }
        Files.copy(jmxFile.toPath(), backup.toPath());
        
        // Write the modified content
        try (FileWriter writer = new FileWriter(jmxFile, StandardCharsets.UTF_8)) {
            writer.write(jmxContent);
        }
    }

    /**
     * Save to a specific file.
     */
    public void saveTo(File outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
            writer.write(jmxContent);
        }
    }

    public List<String> getAppliedChanges() {
        return appliedChanges;
    }

    public List<String> getFailedChanges() {
        return failedChanges;
    }
}
