package com.commerce;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.commerce.config.IntegrationTest;
import com.commerce.repository.OrderProductRepository;

@IntegrationTest
public class StudyTest {

	@Autowired
	private OrderProductRepository orderProductRepository;

	@Test
	void test() {

	}
}
