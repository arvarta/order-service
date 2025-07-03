package org.ezon.msa.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DeliveryRequestDto {

    // 배송지 정보 (전체 BuyerAddress 객체처럼 전달받음)
    private int userId;
    private String recipientName;
    private String recipientTel;
    private String recipientAddr1;
    private String recipientAddr2;
    private String recipientZipcode;
    private String recipientReq;

    // 배송 관련 정보
    private int orderItemId;
    private int sellerAddressId;
    private int productId;

    private String trackingNum;
    private String courierName;
    private LocalDateTime estimatedDeliveryDate;
    private int shippingFee;
}
