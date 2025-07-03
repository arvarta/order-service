package org.ezon.msa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemDto {
	private Long orderItemId;
    private Long productId;
    private String orderedNum;
    private String cardType;
    private String productName;
    private String image;
    private int quantity;    
    private int price;
    private int discountPrice;
    private int shippingFee;
    private int totalAmount; 
    private String status;
    private Long userId;
    private String sellerName;
    private String buyerName;
    private String companyName;
    private String orderedAt;
}
