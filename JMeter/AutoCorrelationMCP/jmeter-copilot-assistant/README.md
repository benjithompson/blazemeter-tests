# JMeter Copilot Assistant

## Overview
The JMeter Copilot Assistant is a plugin designed to enhance the JMeter testing experience by integrating AI assistance through GitHub Copilot. This plugin provides a user-friendly GUI for running tests, analyzing correlation candidates, and automatically applying extractors and parameterization to JMX files.

## Features
- **Correlation Analysis**: Automatically detect dynamic values that need correlation
- **AI-Powered Suggestions**: Use Copilot to generate extractor expressions
- **Auto-Apply Correlations**: Directly modify JMX files with extractors and variable references
- **GUI Interface**: Easy-to-use dialog within JMeter
- **CLI Interface**: Command-line tool for automation and CI/CD pipelines

## Installation

### GUI Plugin
1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/jmeter-copilot-assistant.git
   ```
2. Navigate to the plugin directory:
   ```bash
   cd jmeter-copilot-assistant/plugin
   ```
3. Build the plugin using Maven:
   ```bash
   mvn clean package
   ```
4. Copy the generated JAR file to JMeter's lib/ext directory:
   ```bash
   cp target/jmeter-copilot-assistant-0.1.0.jar $JMETER_HOME/lib/ext/
   # Or for Homebrew on macOS:
   cp target/jmeter-copilot-assistant-0.1.0.jar /opt/homebrew/opt/jmeter/libexec/lib/ext/
   ```

### CLI Tool
The CLI allows you to run correlation analysis from the command line:

1. Build the plugin (if not already done):
   ```bash
   cd jmeter-copilot-assistant/plugin
   mvn clean package
   ```

2. Add the CLI to your PATH (optional):
   ```bash
   # Create symlink
   sudo ln -s $(pwd)/../bin/jmeter-copilot /usr/local/bin/jmeter-copilot
   
   # Or add to PATH in your shell profile (.bashrc, .zshrc, etc.)
   export PATH="$PATH:/path/to/jmeter-copilot-assistant/bin"
   ```

## Usage

### GUI Usage
1. Launch JMeter
2. Open your test plan
3. Go to **Tools → JMeter Copilot Assistant**
4. Sign in with GitHub Copilot
5. Click **Run Analysis** to execute your test and detect correlation candidates
6. Click **Apply Correlations to JMX** to automatically add extractors

### CLI Usage
```bash
# Basic usage - analyze and apply correlations
jmeter-copilot -jmx mytest.jmx

# Or without the -jmx flag
jmeter-copilot mytest.jmx

# Output to a different file
jmeter-copilot -jmx mytest.jmx -output mytest-correlated.jmx

# Dry run - show what would be changed without modifying
jmeter-copilot -jmx mytest.jmx -dry-run

# Interactive mode - prompt for each candidate
jmeter-copilot -jmx mytest.jmx -no-auto-accept

# Multiple runs for better analysis
jmeter-copilot -jmx mytest.jmx -runs 2

# Verbose output
jmeter-copilot -jmx mytest.jmx -verbose
```

### CLI Options
| Option | Description |
|--------|-------------|
| `-jmx <file>` | Path to JMX file (required) |
| `-output, -o <file>` | Output JMX file (default: overwrites input) |
| `-auto-accept` | Auto-accept recommended candidates (default) |
| `-no-auto-accept` | Interactive mode - prompt for each candidate |
| `-runs, -n <n>` | Number of test runs for analysis (default: 1) |
| `-model, -m <name>` | AI model to use (default: claude-sonnet-4.5) |
| `-dry-run` | Show changes without modifying files |
| `-verbose, -v` | Show detailed output |
| `-help, -h` | Show help message |
| `-version` | Show version |

## Authentication
The plugin uses GitHub's Device Flow for Copilot authentication:
1. On first use, you'll be prompted to visit a URL and enter a code
2. Once authenticated, your token is stored securely
3. Authentication persists across sessions

## Contributing
Contributions are welcome! Please submit a pull request or open an issue for any enhancements or bug fixes.

## License
This project is licensed under the MIT License. See the LICENSE file for more details.