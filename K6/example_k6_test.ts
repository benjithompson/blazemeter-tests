import http from 'k6/http';
import { check, sleep } from 'k6';


export const options = {
    vus: 10,              // Number of virtual users
    duration: '30s',      // Duration of the test
};

export default function () {
    const res = http.get(__ENV.BZM_SECRET_secret_url);
    check(res, {
        'status is 200': (r) => r.status === 200,
    });
    sleep(1);
}
