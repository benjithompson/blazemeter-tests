// Test account for the Digital Bank demo app (http://dbankdemo.com).
//
// This is a public demo account. It is NOT a secret, so it is committed and
// uploaded to BlazeMeter with the spec. Do not put real credentials here.
//
// Override at run time with DBANK_USER / DBANK_PASSWORD if you need another
// account (for example from a GitHub Actions secret).
export const dbank = {
  baseURL: process.env.DBANK_BASE_URL ?? 'http://dbankdemo.com',
  username: process.env.DBANK_USER ?? 'jsmith@demo.io',
  password: process.env.DBANK_PASSWORD ?? 'Demo123!',
};
