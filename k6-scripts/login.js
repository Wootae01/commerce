import http from "k6/http";
import { check, sleep } from "k6";
import exec from "k6/execution";
import { fetchCsrfToken } from "./lib/csrf.js";

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
        // 토큰을 받는 GET /login(csrf_token)은 빼고 로그인 POST만 본다
        "http_req_duration{name:login}": ["p(95)<1000"],
    },
};

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/+$/, "");
const PASSWORD = __ENV.PASSWORD || "password!";

// 계정이 1000개라면: user1 ~ user1000을 순환 사용
const USER_POOL = Number(__ENV.USER_POOL || 1000);

export default function () {
    const id = exec.vu.idInTest;
    const username = `user${((id - 1) % USER_POOL) + 1}`;

    // formLogin 기본 파라미터: username/password + CSRF 토큰(_csrf)
    // 반복마다 쿠키가 초기화되므로 매번 로그인 화면에서 토큰을 새로 받는다
    const payload = { username, password: PASSWORD, _csrf: fetchCsrfToken("/login", BASE_URL) };

    const loginRes = http.post(`${BASE_URL}/login`, payload, { redirects: 0, tags: { name: "login" } });

    check(loginRes, {
        "login status is 302/200": (r) => r.status === 302 || r.status === 200,
        "session cookie set": (r) =>
            (r.headers["Set-Cookie"] || "").includes("JSESSIONID"),
    });

    sleep(1);
}
