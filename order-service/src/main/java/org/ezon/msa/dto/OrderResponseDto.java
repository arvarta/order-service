package org.ezon.msa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponseDto {
	private String orderedNum;
    private Long paymentId;
    private Long addressId;
    private String orderedAt;  // 🔥 LocalDateTime → String
    private int totalAmount;
}