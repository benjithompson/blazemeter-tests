# BlazeMeter Example Tests

Example tests that run on [BlazeMeter](https://www.blazemeter.com). Each folder
holds scripts for one test tool, with the Taurus configuration that BlazeMeter
uses to run them.

Use these examples to see how a script is written, how
[Taurus](https://gettaurus.org) wraps it, and how it runs on BlazeMeter. Copy
one as a starting point for your own test.

## Contents

| Folder | Tool | Test type |
| --- | --- | --- |
| [`JMeter/`](JMeter) | Apache JMeter | Load and API tests, recordings, CSV test data, BlazeMeter secrets, plugins |
| [`Gatling/`](Gatling) | Gatling | Load tests in Scala and Java, open and closed workload models |
| [`K6/`](K6) | Grafana k6 | Load tests in JavaScript and TypeScript |
| [`Locust/`](Locust) | Locust | Load tests in Python |
| [`Playwright/`](Playwright) | Playwright | Browser tests and Core Web Vitals under load |
| [`Selenium/`](Selenium) | Selenium | Browser functional tests in Java and Python |
| [`Mocha/`](Mocha) | Mocha, WebdriverIO, Appium | Mobile app tests on Perfecto |
| [`Taurus/`](Taurus) | Taurus | YAML configurations, Postman (Newman) API tests, templates |

Each example is independent. Some folders have their own README with details.

## Target applications

The examples test public demo sites only:

- [blazedemo.com](https://blazedemo.com)
- [demoblaze.com](https://www.demoblaze.com)
- [dbankdemo.com](http://dbankdemo.com/bank/login) (Digital Bank)

The test accounts in this repository are public demo accounts. Do not point a
test at a system you do not own or have permission to test.

## Run a test

### Locally

Install Taurus, and the tool for the example you want to run.

```bash
pip install bzt
bzt Locust/demoblaze-browse-cart/demoblaze-browse-cart.yml
```

Taurus downloads JMeter, Gatling and some other tools on first use.

### On BlazeMeter

1. Create a performance or functional test in BlazeMeter.
2. Upload the Taurus YAML and the files it uses (script, CSV data).
3. Run the test.

You can also start a cloud run from your machine. Put your BlazeMeter API key
in `~/.bzt-rc`, then add `-cloud`:

```yaml
modules:
  blazemeter:
    token: <api key id>:<api key secret>
```

```bash
bzt Locust/demoblaze-browse-cart/demoblaze-browse-cart.yml -cloud
```

## Credentials

No real credential is stored in this repository. Examples that need a key read
it from an environment variable, a `.env` file, or a BlazeMeter secret. Copy the
`.env.example` or `api-key.example.json` file next to the example, and fill in
your own values. Do not commit the copy.

For how a JMeter script reads a BlazeMeter secret, see
[`JMeter/blazemeter-secrets/`](JMeter/blazemeter-secrets).

## Feedback

To report a problem with an example, open an issue.

## Disclaimer

These examples are provided as is, for learning. They are not an official
BlazeMeter product and are not covered by BlazeMeter support.

## License

[MIT](LICENSE)

## Resources

- [BlazeMeter documentation](https://help.blazemeter.com)
- [Taurus documentation](https://gettaurus.org/docs/)
