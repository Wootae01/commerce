import http from "k6/http";
import { check } from "k6";
import { Trend, Rate } from "k6/metrics";
import { BASE_URL, USER_COUNT, PASSWORD } from "./config.js";
import { CSRF_HEADER, fetchCsrfToken } from "./csrf.js";

export const loginDuration = new Trend("login_duration");
export const loginFail = new Rate("login_fail");

/**
 * @param {{loggedIn:boolean, csrfToken?:string}} state  - VU별 로그인 상태 객체
 */
export function ensureLoggedIn(state) {

    if (state.loggedIn) return;

    const idx = (__VU - 1) % USER_COUNT;
    const username = `user${idx}`;

    const res = http.post(
        `${BASE_URL}/login`,
        { username: username, password: PASSWORD, _csrf: fetchCsrfToken("/login") },
        { redirects: 0, tags: { name: "login" } }
    );

    const loc = res.headers["Location"] || "";
    const ok = (res.status === 302 && !loc.includes("error")) || res.status === 200;

    check(res, { "login ok (200/302)": () => ok });
    loginDuration.add(res.timings.duration);
    loginFail.add(!ok);

    if (!ok) {
        throw new Error(`login failed: ${username}, status=${res.status}, location=${loc}`);
    }

    // 로그인하면 토큰이 바뀐다. 이후 POST에 쓸 토큰을 다시 받아 둔다
    state.csrfToken = fetchCsrfToken("/pay/loading");
    state.loggedIn = true;
}

/** 로그인 후 POST 요청에 붙일 헤더 */
export function csrfHeaders(state) {
    return { [CSRF_HEADER]: state.csrfToken };
}
