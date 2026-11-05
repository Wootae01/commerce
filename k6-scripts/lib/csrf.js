import http from "k6/http";
import { BASE_URL } from "./config.js";

export const CSRF_HEADER = "X-CSRF-TOKEN";

const CSRF_META = /<meta name="_csrf" content="([^"]+)"/;

/**
 * 화면 HTML의 <meta name="_csrf">에서 CSRF 토큰을 꺼낸다.
 * 토큰은 세션에 묶여 있고 로그인하면 바뀌므로, 로그인 전(/login)과 후(/pay/loading)에 각각 받는다.
 */
export function fetchCsrfToken(path, baseUrl = BASE_URL) {
    const res = http.get(`${baseUrl}${path}`, { tags: { name: "csrf_token" } });
    const match = CSRF_META.exec(res.body || "");
    if (!match) {
        throw new Error(`csrf token not found: path=${path}, status=${res.status}`);
    }
    return match[1];
}
