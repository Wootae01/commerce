package com.commerce.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러(결제 보정 등)를 켠다.
 * 테스트에서는 스케줄러가 테스트 데이터를 건드리지 않도록 끄고, 실행 메서드를 직접 호출한다.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
