package org.ezon.msa.dto;

import org.ezon.msa.enums.DeliveryStatus;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;

@Data
public class DeliveryResponseDto {
	private int deliveryId;
	private int orderItemId;
	@Enumerated(EnumType.STRING)
	private DeliveryStatus status;
}
