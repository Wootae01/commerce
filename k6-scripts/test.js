import http from "k6/http";
import {check, sleep} from "k6";
// 부하 패턴
export const options = {
    stages: [
        { duration: '15s', target: 100 },
        { duration: '30s', target: 1000 },
        { duration: '15s', target: 0 },
    ],
}

// 각 VU가 계속 반복 실행하는 “유저 행동”
export default function () {
    const res = http.get("http://localhost:8080/");
    check(res, {"status is 200" : (r) => r.status === 200});
    sleep(1);
}