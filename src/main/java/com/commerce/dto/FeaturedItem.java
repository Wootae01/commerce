package com.commerce.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeaturedItem {
	private Long productId;
	private Boolean featured;      // 체크박스는 null일 수 있어서 Boolean 추천
	private Integer featuredRank;
}
