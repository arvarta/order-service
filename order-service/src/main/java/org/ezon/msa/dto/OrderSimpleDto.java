package org.ezon.msa.dto;

import java.time.LocalDateTime;

import org.ezon.msa.entity.Order;
import org.ezon.msa.enums.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderSimpleDto {
	private String orderedNum;
	private LocalDateTime orderedAt;
	private int totalAmount;
	public static OrderSimpleDto fromOrder(Order order) {
	    return new OrderSimpleDto(order.getOrderedNum(), order.getOrderedAt(), order.getTotalAmount());
	}
}