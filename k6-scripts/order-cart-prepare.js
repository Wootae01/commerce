import http from "k6/http";
import {check, sleep} from "k6";
import {ensureLoggedIn} from "./lib/auth.js";
import {BASE_URL} from "./lib/config.js";
// 부하 패턴
export const options = {
    noCookiesReset: true,
    stages: [
        { duration: '15s', target: 100 },
        { duration: '30s', target: 1000 },
        { duration: '15s', target: 0 },
    ],
}

const state = {loggedIn: false}
export default function () {

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
    const ids = idsRes.json();

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

    check(prepareRes, {"status is 200" : (r) => r.status === 200});
    if (prepareRes.status !== 200) {
        console.log(`VU=${__VU} prepare failed status=${prepareRes.status}`);
    }

    sleep(1);
}