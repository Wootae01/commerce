import http from "k6/http";
import { check, sleep } from "k6";
import exec from "k6/execution";

export const options = {
    scenarios: {
        login_1k: {
            executor: "ramping-vus",
            startVUs: 0,
            stages: [
                { duration: "30s", target: 100 },
                { duration: "60s", target: 1000 },
                { duration: "60s", target: 1000 },
                { duration: "30s", target: 0 },
            ],
            gracefulRampDown: "30s",
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<1000"],
    },
};

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/+$/, "");
const PASSWORD = __ENV.PASSWORD || "password!";

// 계정이 1000개라면: user1 ~ user1000을 순환 사용
const USER_POOL = Number(__ENV.USER_POOL || 1000);

export default function () {
    const id = exec.vu.idInTest;
    const username = `user${((id - 1) % USER_POOL) + 1}`;

    // formLogin 기본 파라미터: username/password
    const payload = { username, password: PASSWORD };

    const loginRes = http.post(`${BASE_URL}/login`, payload, { redirects: 0 });

    check(loginRes, {
        "login status is 302/200": (r) => r.status === 302 || r.status === 200,
        "session cookie set": (r) =>
            (r.headers["Set-Cookie"] || "").includes("JSESSIONID"),
    });

    sleep(1);
}
