package org.ezon.msa.entity;

import org.ezon.msa.enums.OrderStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "order_item")
public class OrderItem {
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long orderItemId;

	@Column(name = "ordered_num", nullable = false, length = 50)
    private String orderedNum; // 주문번호, 조회나 추적에 사용 & 송잔번호도 동일사용
	
    @Column(name = "product_id", nullable = false)
    private Long productId;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "price", nullable = false)
    private int price;

    @Column(name = "discount_price", nullable = false)
    private int discountPrice;

    @Column(name = "shipping_fee", nullable = false)
    private int shippingFee;
    
    @Column(name = "product_name", nullable = false, length = 50)
    private String productName;
    
    @Column(name = "total_amount", nullable = false)
    private int totalAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private OrderStatus status;
}

