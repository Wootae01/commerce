import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";

const payConfirmDuration = new Trend("pay_confirm_duration", true); // ms
const payConfirmFail = new Rate("pay_confirm_fail");               // true면 실패로 카운트

const loginDuration = new Trend("login_duration", true);
const loginFail = new Rate("login_fail");

const prepareDuration = new Trend("prepare_duration", true);
const payPrepareFail = new Rate("pay_prepare_fail");

export const options = {
    scenarios: {
        order_1k: {
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
};

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/+$/, "");
const USER_COUNT = 1000; // DevAuthUsers가 만든 user0~user999

function getLoginPayload() {
    const idx = (__VU - 1) % USER_COUNT;   // 0..999
    const username = `user${idx}`;         // user0..user999
    return { username, password: "password!" };
}

export default function () {

    // 0) 로그인
    const form = getLoginPayload();
    const loginRes = http.post(`${BASE_URL}/login`, form, { redirects: 0 });
    const loc = loginRes.headers["Location"] || "";

    check(loginRes, { "login 200/302": (r) => r.status === 200 || r.status === 302 });

    if ((loginRes.status !== 200 && loginRes.status !== 302) || loc.includes("error")) {
        loginFail.add(true);
        return;
    }

    loginDuration.add(loginRes.timings.duration);

    // 1) 결제 준비
    const prepareRes = http.post(
        `${BASE_URL}/pay/prepare`,
        {
            name: "kim",
            phone: "01012345678",
            address: "충북대학교",
            addressDetail: "전자정보대학",
            requestNote: "fdsaf",
            orderType: "BUY_NOW", // enum 문자열
            productId: "1",
            quantity: "1",
        },
        {
            redirects: 0,
            tags: { name: "pay_prepare" },
        }
    );

    check(prepareRes, { "prepare 200": (r) => r.status === 200 });

    if (prepareRes.status !== 200) {
        payPrepareFail.add(true);
        return;
    }
    prepareDuration.add(prepareRes.timings.duration);



    const prepareBody = prepareRes.json();
    const orderId = prepareBody.orderId;
    const amount = prepareBody.amount;

    // 2) confirm 요청
    const confirmRes = http.post(
        `${BASE_URL}/pay/confirm`,
        JSON.stringify({
            orderId,
            paymentKey: `test-payment-key-${crypto.randomUUID()}`,
            amount,
        }),
        {
            headers: { "Content-Type": "application/json" },
            redirects: 0,
            tags: { name: "pay_confirm" },
        }
    );


    payConfirmDuration.add(confirmRes.timings.duration);
    payConfirmFail.add(confirmRes.status !== 200);

    check(confirmRes, { "confirm 200": (r) => r.status === 200 });

    sleep(1);
}
