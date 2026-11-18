# Perfecto Cloud Appium WebDriverIO Demo

This project demonstrates how to run Appium tests using WebDriverIO on Perfecto's demo cloud.

## Project Structure

```
├── tests/
│   └── demobank.js         # Main test file
├── .env.example            # Environment variable template
├── package.json            # Dependencies and scripts
├── taurus.yaml            # Taurus configuration
└── README.md              # This file
```

## Setup

1. Install dependencies:
   ```bash
   npm install
   ```

2. Set up your Perfecto security token:
   - Copy `.env.example` to `.env`
   - Replace `YOUR_SECURITY_TOKEN_HERE` with your actual Perfecto security token
   - Or set the environment variable: `export PERFECTO_SECURITY_TOKEN=your_token_here`

3. Update the `PERFECTO_HOST` in `tests/demobank.js` if you're using a different Perfecto cloud instance

## Running Tests

```bash
npm test              # Run the main test file
npm run test:all      # Run all tests in the tests folder
npm run test:watch    # Run tests in watch mode
```

Or run directly with Mocha:
```bash
npx mocha tests/demobank.js
npx mocha tests/**/*.js     # Run all tests
```

## Configuration

The test is configured to:
- Run on iOS device with ID `00008130-001839960C10001C`
- Use XCUITest automation
- Connect to Perfecto's demo cloud
- Test both BlazeDemo and DemoBlaze websites

## Capabilities

The current capabilities are set for iOS testing. You can modify them in `tests/demobank.js` to test on different platforms or devices available in your Perfecto cloud.

## Notes

- Make sure your Perfecto account has access to the specified device
- The timeout is set to 60 seconds to accommodate cloud execution delays
- Tests include basic assertions to verify page loading
