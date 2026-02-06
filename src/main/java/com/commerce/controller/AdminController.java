package com.commerce.controller;

import java.util.Set;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.commerce.support.ProductCachePolicy;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

	private final RedisTemplate<String, String> redisTemplate;

	@GetMapping("/dashboard")
	public String dashboard() {
		return "admin/dashboard";
	}

	@DeleteMapping("/cache/products")
	@ResponseBody
	public ResponseEntity<String> clearProductCache() {
		int deleted = 0;

		// Delete featured cache
		Boolean featuredDeleted = redisTemplate.delete(ProductCachePolicy.FEATURED_KEY);
		if (Boolean.TRUE.equals(featuredDeleted)) {
			deleted++;
		}

		// Delete all popular caches (pattern: commerce:product:home:popular*)
		Set<String> popularKeys = redisTemplate.keys(ProductCachePolicy.PREFIX_POPULAR_KEY + "*");
		if (popularKeys != null && !popularKeys.isEmpty()) {
			Long count = redisTemplate.delete(popularKeys);
			deleted += count != null ? count.intValue() : 0;
		}

		return ResponseEntity.ok("Deleted " + deleted + " cache entries");
	}
}
