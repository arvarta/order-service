package org.ezon.msa.dto;

import java.time.LocalDateTime;

import org.ezon.msa.entity.Claim;
import org.ezon.msa.enums.ClaimStatus;
import org.ezon.msa.enums.ClaimType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimDto {
	private Long claimId;
    private Long orderItemId;
    private String orderNumber;
    private Long userId;
    private ClaimType type;
    private String reason;
    private Integer amount;
    private ClaimStatus status;
    private LocalDateTime createdAt;

    // Entity → DTO 변환
    public static ClaimDto fromClaim(Claim claim) {
        ClaimDto dto = new ClaimDto();
        dto.setClaimId(claim.getClaimId());
        dto.setOrderItemId(claim.getOrderItemId());
        dto.setUserId(claim.getUserId());
        dto.setType(claim.getType());
        dto.setReason(claim.getReason());
        dto.setStatus(claim.getStatus());
        dto.setCreatedAt(claim.getClaimedAt());
        return dto;
    }
}
