# Unique data rows per thread

`Dummy-Unique Data Per thread.jmx` shows how each thread reads its own row of
`usercreds.csv`. It uses Dummy Samplers, so it needs no target.

`UniqueDataLoadTestResultPage.jmx` posts each row to the `track_request` RPC
function of a Supabase project. It reads the target from two JMeter properties. Neither value is stored
in the plan.

| Property | Value |
| --- | --- |
| `supabaseHost` | Your project host, for example `your-project.supabase.co` |
| `supabaseAnonKey` | The anon public key of that project |

```bash
jmeter -n -t UniqueDataLoadTestResultPage.jmx \
  -JsupabaseHost=your-project.supabase.co \
  -JsupabaseAnonKey="$SUPABASE_ANON_KEY"
```
