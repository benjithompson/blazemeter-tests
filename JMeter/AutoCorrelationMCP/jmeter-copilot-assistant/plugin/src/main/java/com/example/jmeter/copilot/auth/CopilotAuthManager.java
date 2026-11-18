package com.example.jmeter.copilot.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles GitHub OAuth Device Flow authentication for Copilot access.
 * 
 * Flow:
 * 1. Request device code from GitHub
 * 2. User enters code at github.com/login/device
 * 3. Poll for access token
 * 4. Exchange for Copilot token
 * 
 * The GitHub access token is persisted securely using Java Preferences API
 * (stored in OS-specific locations: macOS ~/Library/Preferences, Windows Registry).
 */
public class CopilotAuthManager {

    private static final String GITHUB_DEVICE_CODE_URL = "https://github.com/login/device/code";
    private static final String GITHUB_ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String COPILOT_TOKEN_URL = "https://api.github.com/copilot_internal/v2/token";
    
    // GitHub Copilot CLI client ID (public, used by official Copilot tools)
    private static final String CLIENT_ID = "Iv1.b507a08c87ecfe98";
    private static final String SCOPE = "read:user";
    
    // Preferences key for storing the access token
    private static final String PREF_ACCESS_TOKEN = "github_access_token";
    private final Preferences prefs;

    private String accessToken;
    private String copilotToken;
    private long tokenExpiry;

    // Singleton instance
    private static CopilotAuthManager instance;

    /**
     * Get the singleton instance of CopilotAuthManager.
     * This ensures all components share the same authentication state.
     */
    public static synchronized CopilotAuthManager getInstance() {
        if (instance == null) {
            instance = new CopilotAuthManager();
        }
        return instance;
    }

    private CopilotAuthManager() {
        this.prefs = Preferences.userNodeForPackage(CopilotAuthManager.class);
        loadSavedToken();
    }

    /**
     * Load any saved access token from preferences.
     * Validates the token is still usable.
     */
    private void loadSavedToken() {
        String savedToken = prefs.get(PREF_ACCESS_TOKEN, null);
        if (savedToken != null && !savedToken.isEmpty()) {
            // Verify the token is still valid by trying to get a Copilot token
            this.accessToken = savedToken;
            try {
                getCopilotToken();
                // Token is valid
            } catch (IOException e) {
                // Token is invalid or expired, clear it
                this.accessToken = null;
                prefs.remove(PREF_ACCESS_TOKEN);
            }
        }
    }

    /**
     * Save the access token to preferences for persistence.
     */
    private void saveToken(String token) {
        if (token != null && !token.isEmpty()) {
            prefs.put(PREF_ACCESS_TOKEN, token);
        } else {
            prefs.remove(PREF_ACCESS_TOKEN);
        }
    }

    public static class DeviceCodeResponse {
        public final String deviceCode;
        public final String userCode;
        public final String verificationUri;
        public final int expiresIn;
        public final int interval;

        public DeviceCodeResponse(String deviceCode, String userCode, String verificationUri, 
                                   int expiresIn, int interval) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.expiresIn = expiresIn;
            this.interval = interval;
        }
    }

    /**
     * Start the device authentication flow.
     * Returns device code info that user needs to complete authentication.
     */
    public DeviceCodeResponse requestDeviceCode() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(GITHUB_DEVICE_CODE_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);

        String body = "client_id=" + CLIENT_ID + "&scope=" + SCOPE;
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        String response = readResponse(conn);
        
        // Parse JSON response (simple parsing without external library)
        String deviceCode = extractJsonString(response, "device_code");
        String userCode = extractJsonString(response, "user_code");
        String verificationUri = extractJsonString(response, "verification_uri");
        int expiresIn = extractJsonInt(response, "expires_in", 900);
        int interval = extractJsonInt(response, "interval", 5);

        return new DeviceCodeResponse(deviceCode, userCode, verificationUri, expiresIn, interval);
    }

    /**
     * Poll for access token after user authorizes the device.
     * 
     * @param deviceCode The device code from requestDeviceCode()
     * @param interval Polling interval in seconds
     * @param statusCallback Called with status updates
     * @return CompletableFuture that completes when token is obtained
     */
    public CompletableFuture<Boolean> pollForAccessToken(String deviceCode, int interval, 
                                                          Consumer<String> statusCallback) {
        final int initialInterval = interval;
        return CompletableFuture.supplyAsync(() -> {
            long deadline = System.currentTimeMillis() + 900_000; // 15 minute timeout
            int currentInterval = initialInterval;
            
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(currentInterval * 1000L);
                    
                    HttpURLConnection conn = (HttpURLConnection) new URL(GITHUB_ACCESS_TOKEN_URL).openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    conn.setDoOutput(true);

                    String body = "client_id=" + CLIENT_ID + 
                                  "&device_code=" + deviceCode + 
                                  "&grant_type=urn:ietf:params:oauth:grant-type:device_code";
                    
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }

                    String response = readResponse(conn);
                    
                    // Check for error
                    String error = extractJsonString(response, "error");
                    if ("authorization_pending".equals(error)) {
                        statusCallback.accept("Waiting for authorization...");
                        continue;
                    } else if ("slow_down".equals(error)) {
                        currentInterval += 5;
                        statusCallback.accept("Slowing down polling...");
                        continue;
                    } else if (error != null && !error.isEmpty()) {
                        statusCallback.accept("Error: " + error);
                        return false;
                    }

                    // Success - extract token
                    String token = extractJsonString(response, "access_token");
                    if (token != null && !token.isEmpty()) {
                        CopilotAuthManager.this.accessToken = token;
                        saveToken(token);  // Persist for future sessions
                        statusCallback.accept("GitHub authentication successful!");
                        return true;
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                } catch (IOException e) {
                    statusCallback.accept("Network error: " + e.getMessage());
                }
            }
            
            statusCallback.accept("Authentication timed out");
            return false;
        });
    }

    /**
     * Get Copilot API token using GitHub access token.
     */
    public String getCopilotToken() throws IOException {
        if (accessToken == null) {
            throw new IllegalStateException("Not authenticated with GitHub");
        }

        // Check if we have a valid cached token
        if (copilotToken != null && System.currentTimeMillis() < tokenExpiry) {
            return copilotToken;
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(COPILOT_TOKEN_URL).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "token " + accessToken);
        conn.setRequestProperty("Accept", "application/json");

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Failed to get Copilot token: HTTP " + responseCode);
        }

        String response = readResponse(conn);
        copilotToken = extractJsonString(response, "token");
        int expiresIn = extractJsonInt(response, "expires_at", 1800);
        tokenExpiry = System.currentTimeMillis() + (expiresIn * 1000L) - 60000; // Refresh 1 min early

        return copilotToken;
    }

    public boolean isAuthenticated() {
        return accessToken != null;
    }

    public void logout() {
        accessToken = null;
        copilotToken = null;
        tokenExpiry = 0;
        saveToken(null);  // Clear persisted token
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private String extractJsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private int extractJsonInt(String json, String key, int defaultValue) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
