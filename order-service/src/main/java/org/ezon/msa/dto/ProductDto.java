package org.ezon.msa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductDto {
	private String sellerAddressId;
	private String courierName;
	private String userId;
	private String name;
    private String image;
}
