package com.commerce.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class FeaturedUpdateForm {

	private List<FeaturedItem> items = new ArrayList<>();

}
