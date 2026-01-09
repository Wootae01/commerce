import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";
import {ensureLoggedIn} from "./lib/auth.js";
import {BASE_URL} from "./lib/config.js";

export const options = {
    noCookiesReset: true,
    stages: [
        { duration: '30s', target: 30 },
        { duration: '2m', target: 100 },
        { duration: '30s', target: 0 },
    ],
}
const state = { loggedIn: false };
const orderListDuration = new Trend('order_list_duration');
const orderListFail = new Rate('order_list_fail');

let printed = false;

export default function () {
    // 1. 로그인
    ensureLoggedIn(state)

    // 2. orders/list 요청
    const res = http.get(`${BASE_URL}/orders/list`, { tags: { name: "orders_list" } });
    const ok = check(res, { "orders/list 200": (r) => r.status === 200 });
    orderListDuration.add(res.timings.duration)
    orderListFail.add(!ok);
    if (!ok && !printed) {
        printed = true;

        const loc = res.headers["Location"] || "";
        const jar = http.cookieJar();
        console.log(
            `orders/list failed: status=${res.status}, location=${loc}, ` +
            `cookies=${JSON.stringify(jar.cookiesForURL(BASE_URL))}, ` +
            `body=${(res.body || "").slice(0, 200)}`
        );
    }

    sleep(1);
}