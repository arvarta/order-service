package org.ezon.msa.entity;

import java.time.LocalDateTime;

import org.ezon.msa.enums.ClaimStatus;
import org.ezon.msa.enums.ClaimType;

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
@Table(name = "claim")
public class Claim {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "claim_id")
	private Long claimId;
	
	@Column(name = "order_item_id", nullable = false)
    private Long orderItemId;
	
	@Column(name = "user_id", nullable = false)
    private Long userId;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 20)
    private ClaimType type;
	
	@Column(name = "reason", nullable = false, length = 255)
    private String reason;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
    private ClaimStatus status;
	
	@Column(name = "claimed_at", nullable = false)
    private LocalDateTime claimedAt;
	
	@Column(name = "processed_at")
    private LocalDateTime processedAt;
	
	@Column(name = "processed_by", length = 50)
    private String processedBy;
	
	@Column(name = "memo", length = 500)
    private String memo;
}
