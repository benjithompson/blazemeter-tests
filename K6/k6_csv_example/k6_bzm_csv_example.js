import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { scenario } from 'k6/execution';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

// Load and parse CSV file once, shared across all VUs
const csvData = new SharedArray('csv-data', function () {
  return papaparse.parse(open('./data.csv'), { header: true }).data;
});

export const options = {
  vus: 10,
  duration: '30s',
};

export default function () {
  // Pick a random user from CSV
  
  const user = csvData[scenario.iterationInTest];
  console.log(`Using: ${user.username}`);
  // Use CSV data in your request
  const payload = JSON.stringify({
    username: user.username,
    password: user.password,
  });
  
  const params = {
    headers: { 'Content-Type': 'application/json' },
  };
  
  const res = http.post('https://test.k6.io/login.php', payload, params);
  
  check(res, {
    'login successful': (r) => r.status === 200,
  });
  
  sleep(1);
}