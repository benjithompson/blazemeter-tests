# BlazeMeter Taurus Cloud Container - Pre-installed Dependencies

This document outlines what dependencies are already available in the BlazeMeter Taurus cloud container and what you need to provide.

## What's Pre-installed in Taurus Container

Based on the Taurus Docker image and BlazeMeter cloud environment, the following are typically pre-installed:

### Java Runtime
- **Java 11** (or later) - JVM is pre-installed
- **javac** - Java compiler

### Core Taurus TestNG Support
- **TestNG 7.10.2** (or similar version) - The TestNG JAR
- **Hamcrest Core** - Testing matchers library
- **JSON library** - For JSON parsing
- **Taurus Java Helper** - BlazeMeter's custom helper classes for TestNG execution

### Selenium WebDriver (Base)
- **Selenium standalone server** (for grid/remote execution)
- **ChromeDriver** - Chrome browser driver
- **GeckoDriver** - Firefox browser driver

### Browsers
- **Chrome** (headless-capable)
- **Firefox** (headless-capable)

### System Libraries
- Standard Linux libraries
- Python 3.x (for Taurus itself)

## What You NEED to Provide

Based on your optimized project dependencies, you need to include these in your test package:

### ❌ NOT Pre-installed - YOU MUST INCLUDE:

**Selenium Chrome-specific JARs** (19 JARs):
- `selenium-api-4.15.0.jar`
- `selenium-chrome-driver-4.15.0.jar`
- `selenium-chromium-driver-4.15.0.jar`
- `selenium-http-4.15.0.jar`
- `selenium-json-4.15.0.jar`
- `selenium-manager-4.15.0.jar`
- `selenium-os-4.15.0.jar`
- `selenium-remote-driver-4.15.0.jar`
- `selenium-support-4.15.0.jar` ← **You need this for WebDriverWait**

**Google Guava & Dependencies** (9 JARs):
- `guava-32.1.2-jre.jar`
- `failureaccess-1.0.1.jar`
- `listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar`
- `jsr305-3.0.2.jar`
- `checker-qual-3.33.0.jar`
- `error_prone_annotations-2.18.0.jar`
- `j2objc-annotations-2.8.jar`

**OpenTelemetry** (13 JARs):
- All opentelemetry-*.jar files (for Selenium 4 observability)

**Other Required** (7 JARs):
- `byte-buddy-1.14.5.jar`
- `commons-exec-1.3.jar`
- `failsafe-3.3.2.jar`
- `slf4j-api-1.7.36.jar`
- `slf4j-simple-2.0.9.jar`
- `auto-service-annotations-1.1.1.jar`
- `jcommander-1.82.jar` ← **TestNG dependency**
- `jquery-3.6.1.jar` ← **TestNG reporting**

### ✅ Likely Pre-installed - But Safe to Include:

These MAY be in the container but versions might differ, so it's safer to include your specific versions:
- `testng-7.8.0.jar` (Container has TestNG, but your version might differ)

## Recommended Approach for BlazeMeter

### Option 1: Upload All Dependencies (Safest)
Upload ALL 38 dependency JARs + your test JAR to ensure version compatibility.

```yaml
execution:
- executor: selenium
  runner: testng
  scenario: testng-demo
  
scenarios:
  testng-demo:
    script: testng-simple-1.0.0-tests.jar
    additional-classpath:
    - lib/*.jar  # All your dependencies

modules:
  selenium:
    chromedriver:
      version: latest
```

### Option 2: Use Only Test JAR (Risky)
If you're confident the BlazeMeter container has compatible versions:

```yaml
execution:
- executor: selenium
  runner: testng
  scenario: testng-demo
  
scenarios:
  testng-demo:
    script: testng-simple-1.0.0-tests.jar
```

⚠️ **WARNING**: This will likely fail because:
- Selenium 4.15.0 specific JARs won't be available
- Your WebDriverWait/ExpectedConditions classes need `selenium-support-4.15.0.jar`
- Guava and OpenTelemetry dependencies required by Selenium 4

### Option 3: Minimal Dependencies (Recommended for BlazeMeter)
Include only the Selenium-specific JARs that are definitely not in the base Taurus container:

**Minimum Required** (~15 MB):
- All `selenium-*.jar` files (9 JARs) 
- All `opentelemetry-*.jar` files (13 JARs)
- `guava-32.1.2-jre.jar` and its dependencies (9 JARs)
- `byte-buddy-1.14.5.jar`
- `failsafe-3.3.2.jar`

## BlazeMeter YAML Configuration Example

```yaml
---
execution:
- concurrency: 1
  hold-for: 3m
  ramp-up: 1m
  scenario: bank-demo
  executor: selenium

scenarios:
  bank-demo:
    script: testng-simple-1.0.0-tests.jar
    additional-classpath:
    - selenium-api-4.15.0.jar
    - selenium-chrome-driver-4.15.0.jar
    - selenium-chromium-driver-4.15.0.jar
    - selenium-http-4.15.0.jar
    - selenium-json-4.15.0.jar
    - selenium-manager-4.15.0.jar
    - selenium-os-4.15.0.jar
    - selenium-remote-driver-4.15.0.jar
    - selenium-support-4.15.0.jar
    - guava-32.1.2-jre.jar
    - byte-buddy-1.14.5.jar
    - failsafe-3.3.2.jar
    # ... include all other dependencies
    
modules:
  selenium:
    chromedriver:
      version: latest
    options:
      args:
      - --no-sandbox
      - --disable-dev-shm-usage
      - --disable-gpu
      - --headless

reporting:
- module: blazemeter
```

## File Upload to BlazeMeter

When creating a test in BlazeMeter, upload:
1. Your test JAR: `testng-simple-1.0.0-tests.jar`
2. All dependency JARs from `target/dependencies/` (or at minimum, the Selenium-specific ones)
3. Your `testng.xml` configuration file (if using one)
4. Your Taurus YAML configuration file

## Testing Locally with Taurus

To test your configuration locally before uploading to BlazeMeter:

```bash
# Install Taurus
pip install bzt

# Run with your YAML
bzt testng_taurus.yaml
```

## Summary

**YOU MUST INCLUDE** all 38 dependency JARs (or at minimum the 31 Selenium/Guava/OpenTelemetry JARs) because:
- BlazeMeter's Taurus container has generic TestNG support
- It does NOT include Selenium 4.15.0 specific client libraries
- It does NOT include your specific versions of Guava, Byte Buddy, etc.

**Total size with dependencies**: ~19 MB (manageable for BlazeMeter upload)

**Best Practice**: Include ALL dependencies to ensure version compatibility and avoid runtime errors.
