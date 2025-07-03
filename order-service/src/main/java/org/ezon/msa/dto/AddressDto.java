package org.ezon.msa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AddressDto {
    private String recipientName;
    private String recipientTel;
    private String recipientAddr1;
    private String recipientAddr2;
    private String recipientZipcode;
    private String recipientReq;
}
