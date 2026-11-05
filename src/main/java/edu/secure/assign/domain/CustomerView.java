package edu.secure.assign.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 응답 전용 DTO -> 응답에 들어갈 필수 항목만 마스킹해서 담는 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerView {
	private String customerId;
	private String customerName;
	private String rrnMasked;
	private String phoneMasked;
	
}
