package org.ezon.msa.enums;

public enum ClaimType {
	REFUND,			// 환불
	EXCHANGE,		// 교환
	CANCEL;			// 취소
	
	public OrderStatus toOrderStatus() {
		return switch (this) {
			case REFUND -> OrderStatus.REFUND_REQUESTED;
			case EXCHANGE -> OrderStatus.EXCHANGE_REQUESTED;
			case CANCEL -> OrderStatus.CANCELLED;
		};
	}
}
