import http from "k6/http";
import { check } from "k6";
import { Trend, Rate } from "k6/metrics";
import { BASE_URL, USER_COUNT, PASSWORD } from "./config.js";

export const loginDuration = new Trend("login_duration");
export const loginFail = new Rate("login_fail");

/**
 * @param {{loggedIn:boolean}} state  - VU별 로그인 상태 객체
 */
export function ensureLoggedIn(state) {

    if (state.loggedIn) return;

    const idx = (__VU - 1) % USER_COUNT;
    const username = `user${idx}`;

    const res = http.post(
        `${BASE_URL}/login`,
        { username: username, password: PASSWORD },
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

    state.loggedIn = true;
}