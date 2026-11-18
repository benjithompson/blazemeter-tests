package com.example.jmeter.copilot.core;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.visualizers.ViewResultsFullVisualizer;
import org.apache.jmeter.visualizers.Visualizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts sample data from existing View Results Tree listeners in the test plan.
 * Uses reflection to access the private buffer in ViewResultsFullVisualizer.
 */
public class ViewResultsTreeExtractor {
    private static final Logger log = LoggerFactory.getLogger(ViewResultsTreeExtractor.class);

    /**
     * Find all View Results Tree listeners in the current test plan and extract their buffered samples.
     * 
     * @return List of SampleResults from all View Results Tree listeners, or empty list if none found
     */
    public List<SampleResult> extractFromAllViewResultsTrees() {
        List<SampleResult> allResults = new ArrayList<>();
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null) {
            log.warn("GuiPackage is null, cannot extract View Results Tree data");
            return allResults;
        }

        JMeterTreeModel treeModel = guiPackage.getTreeModel();
        JMeterTreeNode root = (JMeterTreeNode) treeModel.getRoot();
        
        // Find all ResultCollector nodes that have ViewResultsFullVisualizer
        findViewResultsTrees(root, allResults);
        
        log.info("Extracted {} samples from View Results Tree listeners", allResults.size());
        return allResults;
    }

    /**
     * Recursively search the tree for View Results Tree listeners.
     */
    private void findViewResultsTrees(JMeterTreeNode node, List<SampleResult> results) {
        if (node == null) return;

        TestElement element = node.getTestElement();
        if (element instanceof ResultCollector) {
            ResultCollector rc = (ResultCollector) element;
            
            // Use reflection to access the protected getVisualizer() method
            Visualizer visualizer = getVisualizerViaReflection(rc);
            
            if (visualizer instanceof ViewResultsFullVisualizer) {
                log.info("Found View Results Tree: {}", element.getName());
                List<SampleResult> extracted = extractFromVisualizer((ViewResultsFullVisualizer) visualizer);
                results.addAll(extracted);
            }
        }

        // Recursively check children
        for (int i = 0; i < node.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) node.getChildAt(i);
            findViewResultsTrees(child, results);
        }
    }

    /**
     * Access the protected getVisualizer() method via reflection.
     */
    private Visualizer getVisualizerViaReflection(ResultCollector rc) {
        try {
            java.lang.reflect.Method method = org.apache.jmeter.reporters.AbstractListenerElement.class
                .getDeclaredMethod("getVisualizer");
            method.setAccessible(true);
            return (Visualizer) method.invoke(rc);
        } catch (Exception e) {
            log.debug("Could not get visualizer via reflection: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract samples from a ViewResultsFullVisualizer using reflection.
     * Try multiple approaches since different JMeter versions may store data differently.
     */
    @SuppressWarnings("unchecked")
    private List<SampleResult> extractFromVisualizer(ViewResultsFullVisualizer visualizer) {
        List<SampleResult> results = new ArrayList<>();
        
        // First, try to get data from the JTree (more reliable across JMeter versions)
        results.addAll(extractFromJTree(visualizer));
        
        if (!results.isEmpty()) {
            log.info("Extracted {} samples from ViewResultsFullVisualizer JTree", results.size());
            // Log sample details for debugging
            for (SampleResult sr : results) {
                String respData = sr.getResponseDataAsString();
                log.info("  Sample: {} - ResponseData length: {}, ContentType: {}", 
                    sr.getSampleLabel(),
                    respData != null ? respData.length() : 0,
                    sr.getContentType());
            }
            return results;
        }
        
        // Fallback: try the buffer field
        try {
            Field bufferField = ViewResultsFullVisualizer.class.getDeclaredField("buffer");
            bufferField.setAccessible(true);
            Queue<SampleResult> buffer = (Queue<SampleResult>) bufferField.get(visualizer);
            
            if (buffer != null) {
                synchronized (buffer) {
                    results.addAll(buffer);
                }
                log.info("Extracted {} samples from ViewResultsFullVisualizer buffer", results.size());
            }
        } catch (NoSuchFieldException e) {
            log.warn("Could not find 'buffer' field in ViewResultsFullVisualizer");
            // Try to list all fields for debugging
            logAvailableFields(visualizer);
        } catch (IllegalAccessException e) {
            log.warn("Could not access 'buffer' field: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error extracting samples from ViewResultsFullVisualizer: {}", e.getMessage(), e);
        }
        
        return results;
    }
    
    /**
     * Log all available fields in the visualizer for debugging.
     */
    private void logAvailableFields(ViewResultsFullVisualizer visualizer) {
        log.info("Available fields in ViewResultsFullVisualizer:");
        Class<?> clazz = visualizer.getClass();
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                log.info("  Field: {} (type: {})", f.getName(), f.getType().getSimpleName());
            }
            clazz = clazz.getSuperclass();
            if (clazz == Object.class) break;
        }
    }

    /**
     * Alternative approach: extract from the JTree model if reflection fails.
     * The JTree in ViewResultsFullVisualizer stores SampleResults as user objects in tree nodes.
     */
    private List<SampleResult> extractFromJTree(ViewResultsFullVisualizer visualizer) {
        List<SampleResult> results = new ArrayList<>();
        
        try {
            // Try to find the JTree component - it might be named 'jTree' or stored differently
            Field jTreeField = findField(visualizer.getClass(), "jTree");
            if (jTreeField == null) {
                // Try alternative field names
                for (String fieldName : new String[]{"tree", "resultsTree", "sampleTree"}) {
                    jTreeField = findField(visualizer.getClass(), fieldName);
                    if (jTreeField != null) break;
                }
            }
            
            if (jTreeField != null) {
                jTreeField.setAccessible(true);
                JTree jTree = (JTree) jTreeField.get(visualizer);
                
                if (jTree != null) {
                    TreeModel model = jTree.getModel();
                    if (model != null) {
                        Object root = model.getRoot();
                        log.info("JTree root type: {}", root != null ? root.getClass().getName() : "null");
                        if (root instanceof DefaultMutableTreeNode) {
                            extractFromTreeNode((DefaultMutableTreeNode) root, results);
                            log.info("Extracted {} samples from JTree nodes", results.size());
                        }
                    }
                }
            } else {
                log.warn("Could not find JTree field in ViewResultsFullVisualizer");
            }
        } catch (Exception e) {
            log.error("Error extracting from JTree: {}", e.getMessage(), e);
        }
        
        return results;
    }

    /**
     * Recursively extract SampleResults from tree nodes.
     * The View Results Tree stores each sample as the userObject of a tree node.
     */
    private void extractFromTreeNode(DefaultMutableTreeNode node, List<SampleResult> results) {
        Object userObject = node.getUserObject();
        
        // Log what we find for debugging
        if (userObject != null) {
            log.debug("Tree node userObject type: {}", userObject.getClass().getName());
        }
        
        if (userObject instanceof SampleResult) {
            SampleResult sr = (SampleResult) userObject;
            results.add(sr);
            
            // Log the data available in this sample
            log.info("Found SampleResult: {} (type: {})", 
                sr.getSampleLabel(), 
                sr.getClass().getSimpleName());
            log.info("  - Response data length: {}", 
                sr.getResponseDataAsString() != null ? sr.getResponseDataAsString().length() : 0);
            log.info("  - Response headers: {}", 
                sr.getResponseHeaders() != null ? sr.getResponseHeaders().length() : 0);
            
            if (sr instanceof HTTPSampleResult) {
                HTTPSampleResult httpSr = (HTTPSampleResult) sr;
                log.info("  - Request headers: {}", 
                    httpSr.getRequestHeaders() != null ? httpSr.getRequestHeaders().length() : 0);
                log.info("  - Query string: {}", 
                    httpSr.getQueryString() != null ? httpSr.getQueryString().length() : 0);
            }
        }
        
        // Process children
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            Object child = children.nextElement();
            if (child instanceof DefaultMutableTreeNode) {
                extractFromTreeNode((DefaultMutableTreeNode) child, results);
            }
        }
    }

    /**
     * Find a field by name, searching up the class hierarchy.
     */
    private Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Convert a list of SampleResults into a RunCapture for correlation analysis.
     */
    public RunCapture convertToRunCapture(List<SampleResult> samples, int runNumber) {
        RunCapture capture = new RunCapture(runNumber);
        int index = 0;
        
        for (SampleResult result : samples) {
            SampleCapture sampleCapture = createSampleCapture(result, index++);
            capture.addSample(sampleCapture);
        }
        
        return capture;
    }

    /**
     * Create a SampleCapture from a SampleResult with full request/response data.
     */
    private SampleCapture createSampleCapture(SampleResult result, int index) {
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
                // If it looks like form data, parse it into params
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

        // Get response body
        String responseBody = result.getResponseDataAsString();

        // Parse response code
        int responseCode;
        try {
            responseCode = Integer.parseInt(result.getResponseCode());
        } catch (NumberFormatException e) {
            responseCode = result.isSuccessful() ? 200 : 500;
        }

        return new SampleCapture(
            result.getSampleLabel(),
            index,
            url != null ? url : "",
            method,
            requestHeaders,
            requestParams,
            requestBody,
            responseCode,
            responseHeaders,
            responseBody,
            result.getContentType(),
            result.getTimeStamp()
        );
    }

    private Map<String, String> parseHeaders(String headerText) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (headerText == null || headerText.isEmpty()) {
            return headers;
        }
        
        String[] lines = headerText.split("\r?\n");
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
        if (query == null || query.isEmpty()) {
            return params;
        }
        
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
                    // Should never happen with UTF-8
                }
            }
        }
        return params;
    }

    /**
     * Check if there are any View Results Tree listeners with data in the test plan.
     */
    public boolean hasViewResultsTreeWithData() {
        List<SampleResult> results = extractFromAllViewResultsTrees();
        return !results.isEmpty();
    }
}
