# BlazeMeter secrets in JMeter

These plans show how a JMeter script reads a value from a BlazeMeter secret.
No secret value is stored in this folder.

| File | What it shows |
| --- | --- |
| `dbank-login-secrets.jmx` | Logs in to the Digital Bank demo. The password comes from the secret `dbank_password`. |
| `dummy-sampler-secret.jmx` | Puts the secret `secretname` in a sampler name and in the request data, so you can see that BlazeMeter masks it in reports and logs. |
| `blazemeter-secrets.yml` | Taurus config that runs the login plan and lists the secret it needs. |

## How it works

A JMeter script reads a secret as a JMeter property with the `BZM_SECRET_`
prefix:

```
${__P(BZM_SECRET_dbank_password)}
```

When you upload a JMX, BlazeMeter finds the `BZM_SECRET_` references and turns
on the Secrets section of the test. If a secret value shows up in a report or a
log, BlazeMeter replaces it with asterisks.

## Run it on BlazeMeter

1. In your workspace settings, create the secret `dbank_password`. The Digital
   Bank demo password is `Demo123!`. It is a public demo account.
2. Create a test and upload `dbank-login-secrets.jmx`, or upload
   `blazemeter-secrets.yml` with the JMX.
3. In the Secrets section of the test, check that `dbank_password` is selected.
4. Run the test.

## Run it locally

There is no secret store on your machine, so pass the value as a property:

```bash
jmeter -n -t dbank-login-secrets.jmx -JBZM_SECRET_dbank_password='Demo123!'
```

Without the property, `__P` returns `1` and the login fails.

Reference: [Managing Sensitive Data Securely with Secrets](https://help.blazemeter.com/docs/guide/performance-advanced-secrets.htm)
