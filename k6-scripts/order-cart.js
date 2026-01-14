import http from "k6/http";
import {check, sleep} from "k6";
import {ensureLoggedIn} from "./lib/auth.js";
import {BASE_URL} from "./lib/config.js";
import { Trend, Rate } from "k6/metrics";

// 부하 패턴
export const options = {
    scenarios: {
        buy_once: {
            executor: "per-vu-iterations",
            vus: 300,
            iterations: 1,      // VU당 딱 1번만 실행
            maxDuration: "5m",
        },
    },
    noCookiesReset: true,
};

const state = {loggedIn: false}
const prepareDuration = new Trend('prepare_duration');
const prepareFail = new Rate('prepare_fail');
const payConfirmDuration = new Trend('confirm_duration');
const payConfirmFail = new Rate('confirm_fail');

export default function () {
    sleep(Math.random() * 3); // 0~3초 랜덤 지연으로 접속 분산
    // 1. 로그인
    ensureLoggedIn(state);

    // 2. cartProductIds 요청
    const idsRes = http.get(`${BASE_URL}/test/api/cart/cart-product-ids`, {
        tags: { name: "cart-product-ids" },
    });

    if (idsRes.status !== 200) {
        console.log(`cart-product-ids failed: status=${idsRes.status} body=${idsRes.body}`);
        return;
    }

    const number = 30; // 카트 상품 개수 설정
    const ids = idsRes.json().slice(0, number);

    // 2. 카트를 통한 주문 준비 요청
    const prepareRes = http.post(
        `${BASE_URL}/pay/prepare`,
        {
            name: "kim",
            phone: "01012345678",
            address: "충북대학교",
            addressDetail: "전자정보대학",
            requestNote: "fdsaf",
            orderType: "CART",
            cartProductIds: ids
        },
        {
            redirects: 0,
            tags: { name: "pay_prepare" },
        }
    );

    check(prepareRes, {"prepare status is 200" : (r) => r.status === 200});
    if (prepareRes.status !== 200) {
        console.log(`VU=${__VU} prepare failed status=${prepareRes.status} ${prepareRes.body}`);
        prepareFail.add(true);
    }
    prepareDuration.add(prepareRes.timings.duration);

    // 3. confirm 요청
    const body = prepareRes.json();
    const confirmRes = http.post(
        `${BASE_URL}/pay/confirm`,
        JSON.stringify({
            orderId: body.orderId,
            paymentKey: `test-payment-key-${__VU}-${Date.now()}`,
            amount: body.amount,
        }),
        {
            redirects: 0,
            tags: { name: "pay_confirm" },
            headers: { /*...apiHeaders,*/ "Content-Type": "application/json" },
        }
    );

    payConfirmDuration.add(confirmRes.timings.duration);
    payConfirmFail.add(confirmRes.status !== 200);

    check(confirmRes, { "confirm 200": (r) => r.status === 200 });
    sleep(1);
}