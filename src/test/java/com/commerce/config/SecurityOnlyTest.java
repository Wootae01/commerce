package com.commerce.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.commerce.auth.CustomClientRegistrationRepo;
import com.commerce.auth.DevAuthUsers;
import com.commerce.auth.SocialClientRegistration;
import com.commerce.auth.service.CustomOauth2UserService;
import com.commerce.auth.service.CustomUserDetailService;
import com.commerce.common.config.SecurityConfig;
import com.commerce.common.exception.ApiAuthenticationEntryPoint;

/**
 * DB 없이 보안 설정(SecurityConfig)만 올린다. 컨트롤러가 없어서 보안 필터를 통과한 요청은 404가 된다.
 * <ul>
 *   <li>공용 MySQL 커넥션을 쓰지 않아 컨텍스트가 하나 늘어도 Too many connections가 나지 않는다
 *   <li>@IntegrationTest와 컨텍스트를 같이 쓰지 않으므로, csrf()가 CsrfFilter 저장소를 바꿔 놓는 일 없이 실제 설정으로 확인한다
 *   <li>프로필은 테스트마다 @ActiveProfiles로 고른다(폼 로그인은 local 체인에만 있다)
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringJUnitWebConfig(classes = {SecurityOnlyTest.MvcConfig.class, SecurityConfig.class, DevAuthUsers.class,
	CustomClientRegistrationRepo.class, SocialClientRegistration.class})
@TestPropertySource(properties = {"naver.client-id=test-client", "naver.client-secret=test-secret"})
@MockitoBean(types = {CustomOauth2UserService.class, CustomUserDetailService.class, ApiAuthenticationEntryPoint.class})
public @interface SecurityOnlyTest {

	// 보안 설정의 경로 매칭(requestMatchers)이 Spring MVC를 쓴다
	@Configuration
	@EnableWebMvc
	class MvcConfig {
	}
}
