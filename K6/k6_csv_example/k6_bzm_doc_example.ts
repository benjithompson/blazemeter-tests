import http from 'k6/http';
import {sleep} from 'k6';

export default function () {
    http.get('https://blazedemo.com/');
    // Injecting sleep
    // Sleep time is 500ms. Total iteration time is sleep + time to finish request.
    sleep(0.5);
}

export const options = {
    vus: 10,             // number of virtual users (concurrent)
    duration: '600s',    // total test duration
    stages: [
        {duration: '10.0s', target: 5},
        {duration: '20.0s', target: 10},
        {duration: '570.0s', target: 15}
    ],
};