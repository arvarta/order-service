package org.ezon.msa.entity;

import java.time.LocalDateTime;

import org.ezon.msa.enums.OrderStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "`order`")
public class Order {
	
	@Id
	@Column(name = "ordered_num", nullable = false, length = 50)
    private String orderedNum; // 주문번호, 조회나 추적에 사용 & 송잔번호도 동일사용
	
	@Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(unique = true, name = "payment_id", nullable = false)
    private Long paymentId;
    
    @Column(name = "address_id", nullable = false)
    private Long addressId;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;
}
