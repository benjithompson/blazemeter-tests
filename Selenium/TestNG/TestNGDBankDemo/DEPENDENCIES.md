# TestNG Test Dependencies

This document lists all the JAR files required to run the `testng-simple-1.0.0-tests.jar` file.

## Required JAR Files (38 total)

When deploying to a Docker container, ensure all these JAR files are available in the root directory (or specify the correct classpath).

### TestNG Framework
- `testng-7.8.0.jar`
- `jcommander-1.82.jar`
- `jquery-3.6.1.jar`

### Selenium WebDriver
- `selenium-api-4.15.0.jar`
- `selenium-chrome-driver-4.15.0.jar`
- `selenium-chromium-driver-4.15.0.jar`
- `selenium-http-4.15.0.jar`
- `selenium-json-4.15.0.jar`
- `selenium-manager-4.15.0.jar`
- `selenium-os-4.15.0.jar`
- `selenium-remote-driver-4.15.0.jar`
- `selenium-support-4.15.0.jar`

### Google Guava (Selenium dependency)
- `guava-32.1.2-jre.jar`
- `failureaccess-1.0.1.jar`
- `listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar`
- `jsr305-3.0.2.jar`
- `checker-qual-3.33.0.jar`
- `error_prone_annotations-2.18.0.jar`
- `j2objc-annotations-2.8.jar`

### OpenTelemetry (Selenium observability)
- `opentelemetry-api-1.28.0.jar`
- `opentelemetry-api-events-1.28.0-alpha.jar`
- `opentelemetry-context-1.28.0.jar`
- `opentelemetry-exporter-logging-1.28.0.jar`
- `opentelemetry-extension-incubator-1.28.0-alpha.jar`
- `opentelemetry-sdk-1.28.0.jar`
- `opentelemetry-sdk-common-1.28.0.jar`
- `opentelemetry-sdk-extension-autoconfigure-1.28.0.jar`
- `opentelemetry-sdk-extension-autoconfigure-spi-1.28.0.jar`
- `opentelemetry-sdk-logs-1.28.0.jar`
- `opentelemetry-sdk-metrics-1.28.0.jar`
- `opentelemetry-sdk-trace-1.28.0.jar`
- `opentelemetry-semconv-1.28.0-alpha.jar`

### Other Dependencies
- `auto-service-annotations-1.1.1.jar`
- `byte-buddy-1.14.5.jar`
- `commons-exec-1.3.jar`
- `failsafe-3.3.2.jar`
- `slf4j-api-1.7.36.jar`
- `slf4j-simple-2.0.9.jar`

## Total Size
All dependency JARs combined: ~19 MB

## Docker Setup Example

### Option 1: Copy all JARs to root directory
```dockerfile
# Copy test JAR
COPY target/testng-simple-1.0.0-tests.jar /

# Copy all dependencies
COPY target/dependencies/*.jar /
```

### Option 2: Use separate lib directory (recommended)
```dockerfile
# Copy test JAR
COPY target/testng-simple-1.0.0-tests.jar /app/

# Copy all dependencies to lib directory
COPY target/dependencies/*.jar /app/lib/

# Set working directory
WORKDIR /app

# Run with classpath including all JARs
CMD java -cp "testng-simple-1.0.0-tests.jar:lib/*" org.testng.TestNG testng.xml
```

## Running Tests with Dependencies

### Command to run tests (Linux/Mac):
```bash
java -cp "testng-simple-1.0.0-tests.jar:lib/*" org.testng.TestNG testng.xml
```

### Command to run tests (Windows):
```cmd
java -cp "testng-simple-1.0.0-tests.jar;lib/*" org.testng.TestNG testng.xml
```

## Maven Command to Copy Dependencies

To regenerate the dependency JARs, run:
```bash
mvn dependency:copy-dependencies -DoutputDirectory=target/dependencies
```

## Classpath File (Alternative)

You can also create a manifest file or use a classpath file listing all dependencies.
