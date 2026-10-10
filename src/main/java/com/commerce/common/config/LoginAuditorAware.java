package com.commerce.common.config;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import com.commerce.common.util.SecurityUtil;

/**
 * createdBy, updatedBy에 로그인 username을 채운다.
 * SecurityContext에서만 꺼내므로 저장할 때 사용자 조회 쿼리가 추가로 나가지 않는다.
 * 로그인 전(회원가입 처리 등)에는 비워 둔다.
 */
@Component
public class LoginAuditorAware implements AuditorAware<String> {

	@Override
	public Optional<String> getCurrentAuditor() {
		return SecurityUtil.findCurrentUsername();
	}
}
