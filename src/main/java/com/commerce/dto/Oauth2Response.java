package com.commerce.dto;

public interface Oauth2Response {

	String getProvider(); // 제공자 이름 (naver, google 등)

	String getProviderId(); // user 번호

	String getEmail();

	String getName(); // 사용자 실명

	String getPhone();
}
