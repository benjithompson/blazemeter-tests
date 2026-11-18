# Demoblaze JMeter Test

Run the test plan in non-GUI mode from the repository root:

```bash
jmeter -n -t JMeter/demoblaze/demoblaze.jmx -l JMeter/demoblaze/results/demoblaze.jtl -j JMeter/demoblaze/results/demoblaze.log
```

## Command flags

| Flag   | Meaning                                                   |
| ------ | --------------------------------------------------------- |
| `-n` | Run JMeter in non-GUI, headless mode.                     |
| `-t` | Load the specified JMeter test plan.                      |
| `-l` | Write test sample results to the specified JTL file.      |
| `-j` | Write the JMeter execution log to the specified log file. |

The `results/` directory is local run output and is ignored by Git.

## Run Through BlazeMeter

Configure the BlazeMeter API token in `~/.bzt-rc`:

```yaml
modules:
	cloud:
		token: YOUR_API_KEY_ID:YOUR_API_KEY_SECRET
```

Run the same JMeter test plan through Taurus with BlazeMeter cloud reporting:

```bash
bzt -cloud JMeter/demoblaze/demoblaze-blazemeter.yml
```

The Taurus configuration targets account `291446`, workspace `2194183`, and
project `2508796`. The cloud test is named `Demoblaze JMeter`.
