import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";
import {ensureLoggedIn} from "./lib/auth.js";
import {BASE_URL} from "./lib/config.js";

export const options = {
    noCookiesReset: true,
    stages: [
        { duration: '30s', target: 30 },
        { duration: '2m', target: 300 },
        { duration: '30s', target: 0 },
    ],
}
const state = { loggedIn: false };
const orderListDuration = new Trend('cart_duration');
const orderListFail = new Rate('cart_fail');

export default function () {
    // 1. 로그인
    ensureLoggedIn(state)

    // 2. orders/list 요청
    const res = http.get(`${BASE_URL}/cart`, { tags: { name: "cart" } });
    const ok = check(res, { "cart 200": (r) => r.status === 200 });
    orderListDuration.add(res.timings.duration)
    orderListFail.add(!ok);

    sleep(1);
}