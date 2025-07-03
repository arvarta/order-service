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
public class OrderDetailDto {
	private OrderResponseDto order;
    private List<OrderItemDto> items;
    private AddressDto address;
    
    private int itemsTotal;     // 총 상품금액
    private int shippingTotal;  // 총 배송비
    
    private int cancelItemsTotal;
    private int cancelShippingTotal;
    private int cancelTotalAmount;
    private String cardType;
}
