package org.ezon.msa.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequestDto {
	private Long userId;
    private Long paymentId;
    private Long addressId;
    private List<OrderItemDto> items;
}