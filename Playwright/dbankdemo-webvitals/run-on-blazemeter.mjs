#!/usr/bin/env node
// Upload this example to its BlazeMeter test, start a run, wait for the end,
// and print the master id. Used by the GitHub Actions workflow
// (.github/workflows/dbankdemo-webvitals.yml) and usable locally.
//
// Credentials, same rules as bzm-vitals-dashboard:
//   BLAZEMETER_API_KEY_ID + BLAZEMETER_API_KEY_SECRET   (CI)
//   BLAZEMETER_API_KEY=<path to api-key.json {id, secret}> (local)
// Options (environment):
//   BZM_TEST_ID       BlazeMeter test id (default: dbankdemo-webvitals test)
//   BZM_TIMEOUT_MIN   minutes to wait for the run to end (default 30)
//
// Output: the master id on stdout, and `master_id=<id>` in $GITHUB_OUTPUT.
// Exit code 1 when the run fails or does not end in time.
import { readFile, appendFile } from 'node:fs/promises';
import { basename, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const API = 'https://a.blazemeter.com/api/v4';
const HERE = dirname(fileURLToPath(import.meta.url));
const TEST_ID = process.env.BZM_TEST_ID ?? '16072257';
const TIMEOUT_MS = Number(process.env.BZM_TIMEOUT_MIN ?? 30) * 60_000;
// config.yml is the main script. package-lock.json is not uploaded: the
// Engine runs its own `npm install` after Taurus edits package.json.
const FILES = ['config.yml', 'dbank-login.spec.ts', 'credentials.ts', 'playwright.config.ts', 'package.json'];

const log = (msg) => console.error(`[bzm] ${msg}`);

async function authHeader() {
  let { BLAZEMETER_API_KEY_ID: id, BLAZEMETER_API_KEY_SECRET: secret } = process.env;
  if (process.env.BLAZEMETER_API_KEY) {
    ({ id, secret } = JSON.parse(await readFile(process.env.BLAZEMETER_API_KEY, 'utf8')));
  }
  if (!id || !secret) {
    throw new Error('Set BLAZEMETER_API_KEY_ID and BLAZEMETER_API_KEY_SECRET, or BLAZEMETER_API_KEY to an api-key.json path.');
  }
  return 'Basic ' + Buffer.from(`${id}:${secret}`).toString('base64');
}

async function call(auth, method, path, body) {
  const res = await fetch(API + path, { method, headers: { Authorization: auth }, body });
  const json = await res.json().catch(() => ({}));
  if (!res.ok || json.error) {
    throw new Error(`${method} ${path} -> HTTP ${res.status}: ${JSON.stringify(json.error ?? json)}`);
  }
  return json.result;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const auth = await authHeader();

for (const name of FILES) {
  const form = new FormData();
  form.append('file', new Blob([await readFile(join(HERE, name))]), basename(name));
  await call(auth, 'POST', `/tests/${TEST_ID}/files`, form);
  log(`uploaded ${name}`);
}

const master = await call(auth, 'POST', `/tests/${TEST_ID}/start`);
const masterId = master.id;
log(`started master ${masterId}: https://a.blazemeter.com/app/#/masters/${masterId}`);
if (process.env.GITHUB_OUTPUT) await appendFile(process.env.GITHUB_OUTPUT, `master_id=${masterId}\n`);

const deadline = Date.now() + TIMEOUT_MS;
let status;
do {
  await sleep(20_000);
  status = await call(auth, 'GET', `/masters/${masterId}/status`);
  log(`status ${status.status}`);
} while (status.status !== 'ENDED' && Date.now() < deadline);

const result = await call(auth, 'GET', `/masters/${masterId}`);
console.log(masterId);
if (status.status !== 'ENDED') {
  log(`run did not end within ${TIMEOUT_MS / 60_000} min`);
  process.exit(1);
}
// reportStatus is "passed", "failed" or "unset" (no failure criteria).
log(`ended, reportStatus=${result.reportStatus ?? 'unset'}`);
if (result.reportStatus === 'failed') process.exit(1);
