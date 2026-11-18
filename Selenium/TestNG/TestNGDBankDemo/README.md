# TestNG Simple (Standard Maven Layout)

Minimal project demonstrating a Selenium + TestNG test (`DemoBankTest`) using a standard Maven directory structure.

## Key Features
* One test class: `com.example.tests.DemoBankTest` under `src/test/java`
* Placeholder main class `com.example.app.App`
* Uses Selenium + WebDriverManager (transitive) for driver management
* (Optional) Shaded JAR includes dependencies when needed for external runners
* Run anywhere with Java 11+
* BlazeMeter/Taurus usage still possible if packaging test classes into shaded JAR (see Packaging section)

## Project Layout
```
TestNGSimple/
├── pom.xml                      # Maven build (shade plugin still present)
├── testng.xml                   # TestNG suite (references DemoBankTest)
├── testng_taurus.yaml           # Taurus execution file (adjust if packaging changes)
├── src/
│   ├── main/
│   │   └── java/com/example/app/App.java
│   └── test/
│       └── java/com/example/tests/DemoBankTest.java
└── README.md
```

## Build (Standard)
```bash
mvn clean package -DskipTests
```
Produces:
* Normal artifact: `target/testng-simple-1.0.0.jar` (may be replaced by fat jar — see below)
* Test classes jar: `target/testng-simple-1.0.0-tests.jar`
* Fat executable (main + tests + deps): `target/testng-simple-1.0.0-tests-with-dependencies.jar`

## Run Tests
Via Maven (preferred with standard layout):
```bash
mvn -DsuiteXmlFile=testng.xml test
```
Increase verbosity:
```bash
mvn -DsuiteXmlFile=testng.xml -Dtestng.verbose=2 test
```

### Executable Fat JAR (with Tests)
The repository now uses the Maven Assembly Plugin (descriptor: `src/assembly/tests-with-dependencies.xml`) to build an all-in-one JAR containing:
* Main classes (currently only `App`)
* Test classes (`DemoBankTest` etc.)
* Runtime dependency contents unpacked
* `testng.xml`

Run it directly:
```bash
java -jar target/testng-simple-1.0.0-tests-with-dependencies.jar testng.xml
```
Add verbosity:
```bash
java -jar target/testng-simple-1.0.0-tests-with-dependencies.jar -verbose 2 testng.xml
```

## What the Test Does
1. Launches Chrome
2. Navigates to http://www.dbankdemo.com/bank
3. Prints page title + current URL
4. Asserts title is non-empty
5. Closes browser

## Dependencies (shaded)
* TestNG 7.8.0
* Selenium 4.15.0
* WebDriverManager 5.6.2
* SLF4J Simple 2.0.9 (basic logging)

## Prerequisites
* Java 11+
* Chrome installed & internet access

## Clean Rebuild
```bash
mvn clean package -DskipTests
```

## Direct Class Execution (IDE)
You can also right-click `DemoBankTest` in your IDE and run it; `testng.xml` currently lists only that class.
---
---
Fat jar replaces the base artifact file name because `appendAssemblyId` is `false`. Set it to `true` if you want to retain the slim jar separately.

