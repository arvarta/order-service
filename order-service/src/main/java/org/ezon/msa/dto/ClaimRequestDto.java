package org.ezon.msa.dto;


import org.ezon.msa.enums.ClaimType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimRequestDto {
	// orderItemId 단위
    private Long orderItemId;         // 주문상세(상품) PK
    private ClaimType type;           // 취소, 반품, 교환 등 ENUM
    private String reason;            // 상세 사유 (텍스트)
}
