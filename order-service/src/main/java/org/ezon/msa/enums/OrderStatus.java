package org.ezon.msa.enums;

public enum OrderStatus {
	PAID,        		// 주문 접수됨(결제완료),
	CANCELLED,      	// 주문 취소,
    READY_SHIPMENT,		// 배송 준비중
    SHIPPED,        	// 배송 중
    DELIVERED,      	// 배송 완료
    
    CANCELLED_EMPTY,			// 일시 품절
	CANCELLED_NO_DELIVERY,		// 배송 불가
    
    REFUND_REQUESTED,      	// 환불 요청됨 (배송완료 상태에서)
    EXCHANGE_REQUESTED,    	// 교환 요청됨 (배송완료 상태에서)
    
    REFUND_APPROVED,		// 환불 승인됨
    REFUND_REJECTED,		// 환불 반려됨
    EXCHANGE_APPROVED,		// 교환 승인됨
    EXCHANGE_REJECTED,		// 교환 반려됨
    
    CLAIM_APPROVED,        	// 클레임 승인됨
    CLAIM_REJECTED,        	// 클레임 반려됨
    
    PURCHASE_CONFIRMED	// 구매확정 (배송완료 상태에서)
}
