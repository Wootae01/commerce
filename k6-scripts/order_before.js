import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";

const loginDuration = new Trend("login_duration", true);
const loginFail = new Rate("login_fail");

const prepareDuration = new Trend("prepare_duration", true);
const payPrepareFail = new Rate("pay_prepare_fail");

const payConfirmDuration = new Trend("pay_confirm_duration", true);
const payConfirmFail = new Rate("pay_confirm_fail");

export const options = {
    noCookiesReset: true,
    scenarios: {
        order_1k: {
            executor: "ramping-vus",
            startVUs: 10,
            stages: [
                { duration: "30s", target: 20 },
                { duration: "5m", target: 100 },
                { duration: "30s", target: 0}
            ],
            gracefulRampDown: "10s",
        },
    },
};

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/+$/, "");
const USER_COUNT = Number(__ENV.USER_COUNT || 400);

let loggedIn = false;

function ensureLoggedIn() {
    if (loggedIn) return;

    const idx = (__VU - 1) % USER_COUNT;
    const username = `user${idx}`;

    // cookieJar는 VU별로 자동 유지됨 (여기서 Set-Cookie 받은 JSESSIONID를 k6가 저장)
    const res = http.post(
        `${BASE_URL}/login`,
        { username, password: "password!" },
        {
            redirects: 0,
            tags: { name: "login" },
        }
    );

    const loc = res.headers["Location"] || "";
    const ok = (res.status === 302 && !loc.includes("error")) || res.status === 200;

    check(res, { "login ok (200/302)": () => ok });
    loginDuration.add(res.timings.duration);
    loginFail.add(!ok);

    if (!ok) {
        throw new Error(`login failed: ${username}, status=${res.status}, location=${loc}`);
    }

    // 디버깅: 현재 VU jar에 저장된 쿠키 확인
    const jar = http.cookieJar();
    console.log(`VU=${__VU} logged in. jar cookies=`, JSON.stringify(jar.cookiesForURL(BASE_URL)));

    loggedIn = true;
}

export default function () {
    // 0) 로그인 최초 1회만
    ensureLoggedIn();

    // 1) 결제 준비
    const prepareRes = http.post(
        `${BASE_URL}/pay/prepare`,
        {
            name: "kim",
            phone: "01012345678",
            address: "충북대학교",
            addressDetail: "전자정보대학",
            requestNote: "fdsaf",
            orderType: "BUY_NOW",
            productId: "1",
            quantity: "1",
        },
        {
            redirects: 0,
            tags: { name: "pay_prepare" },
        }
    );

    prepareDuration.add(prepareRes.timings.duration);

    check(prepareRes, { "prepare 200": (r) => r.status === 200 });
    if (prepareRes.status !== 200) {
        payPrepareFail.add(true);

        // 디버깅: 이 시점 jar 쿠키 확인
        const jar = http.cookieJar();
        console.log(`VU=${__VU} prepare failed status=${prepareRes.status} cookies=`, JSON.stringify(jar.cookiesForURL(BASE_URL)));
        return;
    }

    const body = prepareRes.json();
    if (!body || !body.orderId) {
        payPrepareFail.add(true);
        console.log(`VU=${__VU} prepare json invalid:`, prepareRes.body);
        return;
    }

    // 2) 결제 confirm
    const confirmRes = http.post(
        `${BASE_URL}/pay/confirm/before`,
        JSON.stringify({
            orderId: body.orderId,
            paymentKey: `test-payment-key-${__VU}-${Date.now()}`,
            amount: body.amount,
        }),
        {
            redirects: 0,
            tags: { name: "pay_confirm" },
            headers: { "Content-Type": "application/json" },
        }
    );

    payConfirmDuration.add(confirmRes.timings.duration);
    payConfirmFail.add(confirmRes.status !== 200);

    check(confirmRes, { "confirm 200": (r) => r.status === 200 });
    if (confirmRes.status !== 200) {
        console.log(`status: ${confirmRes.status}, ${confirmRes.body}`)
    }
    sleep(1);
}
