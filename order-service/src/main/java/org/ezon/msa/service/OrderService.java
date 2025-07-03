package org.ezon.msa.service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.ezon.msa.dto.AddressDto;
import org.ezon.msa.dto.ClaimDto;
import org.ezon.msa.dto.ClaimRequestDto;
import org.ezon.msa.dto.DeliveryRequestDto;
import org.ezon.msa.dto.DeliveryResponseDto;
import org.ezon.msa.dto.OrderDetailDto;
import org.ezon.msa.dto.OrderItemDto;
import org.ezon.msa.dto.OrderRequestDto;
import org.ezon.msa.dto.OrderResponseDto;
import org.ezon.msa.dto.ProductDto;
import org.ezon.msa.dto.UserDto;
import org.ezon.msa.entity.Claim;
import org.ezon.msa.entity.Order;
import org.ezon.msa.entity.OrderItem;
import org.ezon.msa.enums.ClaimStatus;
import org.ezon.msa.enums.ClaimType;
import org.ezon.msa.enums.DeliveryStatus;
import org.ezon.msa.enums.OrderStatus;
import org.ezon.msa.repository.ClaimRepository;
import org.ezon.msa.repository.OrderItemRepository;
import org.ezon.msa.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.transaction.Transactional;

@Service
public class OrderService {
	
	@Autowired
	private RestTemplate restTemplate;
	
	@Autowired
	private OrderRepository orderRepository;
	
	@Autowired
	private OrderItemRepository orderItemRepository;
	
	@Autowired
	private ClaimRepository claimRepository; 
	
	// ✅ 주문/배송 상태만 필터링할 때 사용하는 상수 집합
    private static final Set<String> ORDER_STATUSES = Set.of(
        "PAID", "READY_SHIPMENT", "SHIPPED", "DELIVERED", "PURCHASE_CONFIRMED"
    );
    
    // ✅ 취소/환불/교환 상태만 필터링할 때 사용하는 상수 집합
    private static final Set<String> CLAIM_STATUSES = Set.of(
        "CANCELLED", "REFUND_REQUESTED", "REFUND_APPROVED", "REFUND_REJECTED", 
        "EXCHANGE_REQUESTED", "EXCHANGE_APPROVED", "EXCHANGE_REJECTED"
    );
	
	@Transactional
	public OrderResponseDto createOrder(OrderRequestDto req) {
		try {
			// 이미 주문된 paymentId인지 확인
			boolean exists = orderRepository.existsByPaymentId(req.getPaymentId());
			if (exists) {
				throw new IllegalStateException("이미 처리된 결제입니다.");
			}
			
			String orderNum = generateOrderNum();
			
			List<OrderItemDto> itemDtos = req.getItems().stream()
					.map(item -> {
						int appliedPrice = item.getDiscountPrice() > 0 ? item.getDiscountPrice() : item.getPrice();
						int totalAmount = appliedPrice * item.getQuantity() + item.getShippingFee();
						item.setOrderedNum(orderNum);
						item.setTotalAmount(totalAmount);
						return item;
					}).collect(Collectors.toList());
			
			int totalAmount = itemDtos.stream().mapToInt(OrderItemDto::getTotalAmount).sum();
			
			System.out.println("orderNum: " + orderNum); // null 아니어야 함
			Order order = Order.builder()
					.orderedNum(orderNum)
					.userId(req.getUserId())
					.paymentId(req.getPaymentId())
					.addressId(req.getAddressId())
					.orderedAt(LocalDateTime.now())
					.totalAmount(totalAmount)
					.build();
			System.out.println("Order 저장 전: " + order.getOrderedNum()); // null 아니어야 함
			try {
				order = orderRepository.save(order);
			} catch (DataIntegrityViolationException e) {
				throw new IllegalStateException("주문에 실패했습니다.");
			}
			System.out.println("Order 저장됨: " + order);
			
			for (OrderItemDto itemDto : itemDtos) {
				OrderItem item = OrderItem.builder()
						.orderedNum(orderNum)
						.userId(req.getUserId())
						.productId(itemDto.getProductId())
						.productName(itemDto.getProductName())
						.quantity(itemDto.getQuantity())
						.price(itemDto.getPrice())
						.discountPrice(itemDto.getDiscountPrice())
						.shippingFee(itemDto.getShippingFee())
						.totalAmount(itemDto.getTotalAmount())
						.status(OrderStatus.PAID)
						.build(); 
				System.out.println("OrderItem 저장 전: " + item);
				orderItemRepository.save(item);
			}
			
			// ★ 결제서비스에 주문번호 update API 호출 (RestTemplate)
			updatePaymentOrderNum(req.getPaymentId(), orderNum);
			
			OrderResponseDto dto = OrderResponseDto.builder()
					.orderedNum(orderNum)
					.build();
			System.out.println("OrderResponseDto: " + dto);
			
			return dto;
		}catch(Exception e) {
			compensateExternal(req.getAddressId(), req.getPaymentId());
			throw new RuntimeException("주문 생성 중 예외 발생", e);
		}
	}
	
	public void compensateExternal(Long addressId, Long paymentId) {
        try {
            restTemplate.delete("http://localhost:8080/api/delivery/user/" + addressId);
        } catch (Exception e) {
        	System.out.println("[삭제 실패](151번째 줄) : " + e.getMessage());
            //log.warn("배송지 삭제 실패 (addressId={})", addressId, e);
        }
        try {
        	restTemplate.delete("http://localhost:8080/api/payment/" + paymentId);
        } catch (Exception e) {
        	System.out.println("[삭제 실패](157번째 줄) : " + e.getMessage());
            //log.warn("결제 삭제 실패 (paymentId={})", paymentId, e);
        }
    }
	
    public OrderDetailDto getOrderDetail(String orderedNum) {
        Order order = orderRepository.findById(orderedNum)
            .orElseThrow(() -> new RuntimeException("주문 정보 없음"));
        List<OrderItem> items = orderItemRepository.findByOrderedNum(orderedNum);
        
        // 1. 주소 조회 (배송 서비스)
        AddressDto address;
        String addrUrl = "http://localhost:10500/api/delivery/user/" + order.getAddressId();
        try {
        	ResponseEntity<AddressDto> addrRes = restTemplate.getForEntity(addrUrl, AddressDto.class);
        	address = addrRes.getBody();
        } catch(Exception e) {
        	address = AddressDto.builder()
        			.recipientName("정혜성")
                    .recipientTel("010-1234-5678")
                    .recipientAddr1("서울시 강남구")
                    .recipientAddr2("테스트타워 101호")
                    .recipientZipcode("12345")
                    .recipientReq("문 앞에 놔주세요")
        			.build();
        	System.out.println("[배송서비스 주소 API 연결 실패] 기본 주소로 대체됨");
        }
        
        // 2. 결제정보에서 cardType 가져오기
        String cardType = getCardType(order.getPaymentId());
        
        // 3. 주문 아이템
        List<OrderItemDto> itemDtos = items.stream().map(item -> {
        	// 상품 정보 조회
        	ProductDto product;
            String prodUrl = "http://localhost:10100/api/products/" + item.getProductId();
            
        	ResponseEntity<ProductDto> prodRes = restTemplate.getForEntity(prodUrl, ProductDto.class);
        	product = prodRes.getBody();
        	System.out.println("상품API: " + prodRes);
        	
            // 판매자 상호명 조회
            String userUrl = "http://localhost:10000/api/users/" + item.getUserId() + "/company-name";
            String companyName;
            try {            	
            	ResponseEntity<String> companyRes = restTemplate.getForEntity(userUrl, String.class);
            	companyName = companyRes.getBody();
            } catch(Exception e) {
            	companyName = "주식회사 베스트커머스";
            	System.out.println("[회원서비스 주소 API 연결 실패] 임시 회사 이름 주입(221번쨰 줄)");
            }
            
            return OrderItemDto.builder()
            	.orderItemId(item.getOrderItemId())
                .productId(item.getProductId())
                .orderedNum(item.getOrderedNum())
                .productName(product.getName())
                .image(product.getImage())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .discountPrice(item.getDiscountPrice())
                .shippingFee(item.getShippingFee())
                .totalAmount(item.getTotalAmount())
                .status(item.getStatus().name())
                .userId(item.getUserId())
                .companyName(companyName)
                .build();
        }).toList();
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");

        OrderResponseDto orderDto = OrderResponseDto.builder()
            .orderedNum(order.getOrderedNum())
            .paymentId(order.getPaymentId())
            .addressId(order.getAddressId())
            .orderedAt(order.getOrderedAt().format(formatter)) // 🔥 포맷된 문자열
            .totalAmount(order.getTotalAmount())
            .build();
        
        // 총합 계산은 스트림 따로 돌리기
        int itemsTotal = itemDtos.stream()
                .mapToInt(dto -> {
                    int applied = dto.getDiscountPrice() > 0 ? dto.getDiscountPrice() : dto.getPrice();
                    return applied * dto.getQuantity();
                }).sum();

        int shippingTotal = itemDtos.stream()
                .mapToInt(OrderItemDto::getShippingFee)
                .sum();
        
        List<OrderItemDto> cancelledWithRefundedItems = itemDtos.stream()
        	.filter(i -> i.getStatus().equals("CANCELLED") || i.getStatus().equals("REFUND_APPROVED")).toList();
        
        int cancelItemsTotal = cancelledWithRefundedItems.stream()
        	.mapToInt(i -> (i.getDiscountPrice() > 0 ? i.getDiscountPrice() : i.getPrice()) * i.getQuantity()).sum();
        
        int cancelShippingTotal = cancelledWithRefundedItems.stream()
    		.mapToInt(OrderItemDto::getShippingFee)
            .sum();
        
        int cancelTotalAmount = cancelItemsTotal + cancelShippingTotal;
        return OrderDetailDto.builder()
        	    .order(orderDto)
        	    .items(itemDtos)
        	    .address(address)
        	    .itemsTotal(itemsTotal)
        	    .shippingTotal(shippingTotal)
        	    .cancelItemsTotal(cancelItemsTotal)
        	    .cancelShippingTotal(cancelShippingTotal)
        	    .cancelTotalAmount(cancelTotalAmount)
        	    .cardType(cardType)
        	    .build();
    }
    private String getCardType(Long id) {
    	String paymentUrl = "http://localhost:10400/api/payment/" + id;
        String cardType;
        try {
            ResponseEntity<Map<String, Object>> payRes = restTemplate.exchange(
                paymentUrl,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> paymentMap = payRes.getBody();
            cardType = (String) paymentMap.getOrDefault("cardType", "정보없음");
        } catch (Exception e) {
            cardType = "정보없음";
            System.out.println("[결제서비스 연결 실패] 기본 cardType으로 대체됨");
        }
        return cardType;
    }
    public List<OrderDetailDto> getUserOrders(Long userId, LocalDate startDate, LocalDate endDate, String status, String keyword) {
        List<Order> orders = orderRepository.findByUserId(userId);
        
        if(startDate != null && endDate != null) {
        	orders = orders.stream().filter(o -> {
        		LocalDate orderedDate = o.getOrderedAt().toLocalDate();
        		return !orderedDate.isBefore(startDate) && !orderedDate.isAfter(endDate);
        	}).toList();
        }
        
        List<OrderDetailDto> result = new ArrayList<>();

        for (Order order : orders) {
            List<OrderItem> items = orderItemRepository.findByOrderedNum(order.getOrderedNum());

            // 상품/판매자정보 조합 (상세 참고)
            List<OrderItemDto> itemDtos = items.stream().map(item -> {
                ProductDto product;
                String prodUrl = "http://localhost:10100/api/products/" + item.getProductId();
                try {
                    ResponseEntity<ProductDto> prodRes = restTemplate.getForEntity(prodUrl, ProductDto.class);
                    product = prodRes.getBody();
                } catch (Exception e) {
                    product = ProductDto.builder().name("테스트상품").image("").build();
                }
                // 판매자 상호명 조회
                String userUrl = "http://localhost:10000/api/users/company-name";
                String companyName;
                
                try {            	
                	ResponseEntity<String> companyRes = restTemplate.exchange(
                			userUrl,
                			HttpMethod.GET,
                			new HttpEntity<String>(product.getUserId()),
                			String.class
                	);
                	companyName = companyRes.getBody();
                } catch(Exception e) {
                	System.out.println("[RestTemplate 오류 발생] => 임시 회사 이름 주입(325번째 줄)");
                	companyName = "주식회사 베스트커머스";
                }
                
                String sellerName = getUserName(Long.parseLong(product.getUserId()));
                String buyerName = getUserName(order.getUserId());
                String cardType = getCardType(order.getPaymentId());
                return OrderItemDto.builder()
                		.orderItemId(item.getOrderItemId())
                        .productId(item.getProductId())
                        .orderedNum(item.getOrderedNum())
                        .productName(product.getName())
                        .image(product.getImage())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .discountPrice(item.getDiscountPrice())
                        .shippingFee(item.getShippingFee())
                        .totalAmount(item.getTotalAmount())
                        .status(item.getStatus().name())
                        .sellerName(sellerName)
                        .buyerName(buyerName)
                        .userId(item.getUserId())
                        .cardType(cardType)
                        .companyName(companyName)
                        .build();
            }).toList();
            
            itemDtos = itemDtos.stream().filter(i -> ORDER_STATUSES.contains(i.getStatus())).toList();
            
            if (status != null && !status.isBlank()) {
            	itemDtos = itemDtos.stream().filter(i -> i.getStatus().equals(status)).toList();
            }

            if (keyword != null && !keyword.isBlank()) {
            	itemDtos = itemDtos.stream().filter(i -> i.getProductName().contains(keyword)
            		|| i.getCompanyName().contains(keyword)
            	).toList();
            }

            if (itemDtos.isEmpty()) continue;

            OrderResponseDto orderDto = OrderResponseDto.builder()
                    .orderedNum(order.getOrderedNum())
                    .paymentId(order.getPaymentId())
                    .addressId(order.getAddressId())
                    .orderedAt(order.getOrderedAt().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")))
                    .totalAmount(order.getTotalAmount())
                    .build();

            int itemsTotal = itemDtos.stream()
                    .mapToInt(dto -> (dto.getDiscountPrice() > 0 ? dto.getDiscountPrice() : dto.getPrice()) * dto.getQuantity())
                    .sum();
            int shippingTotal = itemDtos.stream().mapToInt(OrderItemDto::getShippingFee).sum();
            result.add(OrderDetailDto.builder()
                    .order(orderDto)
                    .items(itemDtos)
                    .itemsTotal(itemsTotal)
                    .shippingTotal(shippingTotal)
                    .cardType(keyword)
                    .build()
            );
        }
        return result;
    }
    private String getUserName(Long id) {
    	String getUserIdUrl = "http://localhost:10000/api/users/" + id;
        String userName = null;
        try {            	
        	ResponseEntity<UserDto> user = restTemplate.exchange(
        			getUserIdUrl,
        			HttpMethod.GET,
        			null,
        			new ParameterizedTypeReference<UserDto> (){}
        	);
        	userName = user.getBody().getName();
        } catch(Exception e) {
        	System.out.println("[RestTemplate 오류 발생] => 임시 이름 주입(400번째 줄)");
        	userName = "익명";
        }
        return userName;
    }
    public List<OrderDetailDto> getUserOrdersByClaims(Long userId, LocalDate startDate, LocalDate endDate, String status, String keyword) {
        List<Order> orders = orderRepository.findByUserId(userId);
        
        if(startDate != null && endDate != null) {
        	orders = orders.stream().filter(o -> {
        		LocalDate orderedDate = o.getOrderedAt().toLocalDate();
        		return !orderedDate.isBefore(startDate) && !orderedDate.isAfter(endDate);
        	}).toList();
        }
        
        List<OrderDetailDto> result = new ArrayList<>();

        for (Order order : orders) {
            List<OrderItem> items = orderItemRepository.findByOrderedNum(order.getOrderedNum());

            // 상품/판매자정보 조합 (상세 참고)
            List<OrderItemDto> itemDtos = items.stream().map(item -> {
                ProductDto product;
                String prodUrl = "http://localhost:10100/api/products/" + item.getProductId();
                try {
                    ResponseEntity<ProductDto> prodRes = restTemplate.getForEntity(prodUrl, ProductDto.class);
                    product = prodRes.getBody();
                } catch (Exception e) {
                    product = ProductDto.builder().name("테스트상품").image("").build();
                }
                // 판매자 상호명 조회
                String userUrl = "http://localhost:10000/api/users/" + item.getUserId() + "/company-name";
                String companyName;
                try {            	
                	ResponseEntity<String> companyRes = restTemplate.getForEntity(userUrl, String.class);
                	companyName = companyRes.getBody();
                } catch(Exception e) {
                	System.out.println("[RestTemplate 오류 발생] => 임시 회사 이름 주입(416번쨰 줄)");
                	companyName = "주식회사 베스트커머스";
                }
                
                return OrderItemDto.builder()
                		.orderItemId(item.getOrderItemId())
                        .productId(item.getProductId())
                        .orderedNum(item.getOrderedNum())
                        .productName(product.getName())
                        .image(product.getImage())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .discountPrice(item.getDiscountPrice())
                        .shippingFee(item.getShippingFee())
                        .totalAmount(item.getTotalAmount())
                        .status(item.getStatus().name())
                        .userId(item.getUserId())
                        .companyName(companyName)
                        .build();
            }).toList();
            
            itemDtos = itemDtos.stream().filter(i -> CLAIM_STATUSES.contains(i.getStatus())).toList();
            
            if (status != null && !status.isBlank()) {
            	itemDtos = itemDtos.stream().filter(i -> i.getStatus().equals(status)).toList();
            }

            if (keyword != null && !keyword.isBlank()) {
            	itemDtos = itemDtos.stream().filter(i -> i.getProductName().contains(keyword)
            		|| i.getCompanyName().contains(keyword)
            	).toList();
            }

            if (itemDtos.isEmpty()) continue;

            OrderResponseDto orderDto = OrderResponseDto.builder()
                    .orderedNum(order.getOrderedNum())
                    .paymentId(order.getPaymentId())
                    .addressId(order.getAddressId())
                    .orderedAt(order.getOrderedAt().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")))
                    .totalAmount(order.getTotalAmount())
                    .build();

            int itemsTotal = itemDtos.stream()
                    .mapToInt(dto -> (dto.getDiscountPrice() > 0 ? dto.getDiscountPrice() : dto.getPrice()) * dto.getQuantity())
                    .sum();
            int shippingTotal = itemDtos.stream().mapToInt(OrderItemDto::getShippingFee).sum();

            result.add(OrderDetailDto.builder()
                    .order(orderDto)
                    .items(itemDtos)
                    .itemsTotal(itemsTotal)
                    .shippingTotal(shippingTotal)
                    .build()
            );
        }
        return result;
    }

    @Transactional
    public void claimOrder(Long orderItemId, ClaimRequestDto req) {
        OrderItem item = orderItemRepository.findById(orderItemId)
            .orElseThrow(() -> new RuntimeException("주문 항목 없음"));

        OrderStatus newStatus = switch (req.getType()) {
	        case CANCEL -> OrderStatus.CANCELLED;
	        case REFUND -> OrderStatus.REFUND_REQUESTED;
	        case EXCHANGE -> OrderStatus.EXCHANGE_REQUESTED;
	    };
	    
	    item.setStatus(newStatus);
	    orderItemRepository.save(item);

	    // Claim 기록
        Order order = orderRepository.findById(item.getOrderedNum())
            .orElseThrow(() -> new RuntimeException("주문 정보 없음"));

        Claim claim = Claim.builder()
        		.orderItemId(orderItemId)
        		.userId(order.getUserId())
        		.type(req.getType())
        		.reason(req.getReason())
        		.status(ClaimStatus.REQUESTED)
        		.claimedAt(LocalDateTime.now())
        		.build();
        
        claimRepository.save(claim);
    }

	public void updatePaymentOrderNum(Long paymentId, String orderNum) {
		System.out.println("orderNum: " + orderNum);
	    String url = "http://localhost:10400/api/payment/" + paymentId + "/order-num"; // 결제서비스 주소/포트에 맞게!
	    RestTemplate restTemplate = new RestTemplate();
	    HttpHeaders headers = new HttpHeaders();
	    headers.setContentType(MediaType.APPLICATION_JSON);

	    // 요청 파라미터 (orderNum만 전달)
	    // { "orderNum": "202406131234" }
	    String body = "{\"orderNum\":\"" + orderNum + "\"}";
	    HttpEntity<String> entity = new HttpEntity<>(body, headers);

	    try {
	        restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
	    } catch (Exception e) {
	    	System.out.println("payment 수정 실패(520번째 줄) : " + e.getMessage());
	        // RestTemplate 예외 발생 시 반드시 콘솔에 남게 한다
	        throw e; // 다시 던지면 트랜잭션 롤백
	    }
	}
	
	public String generateOrderNum() {
		String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
		int rand = (int)(Math.random() * 9000) + 1000; // 1000~9999
		
		return date + rand;
	}
	
	/* added rogic */
	private List<Long> getUserIds() {
    	String url = "http://localhost:10000/api/userIds";
    	RestTemplate restTemplate = new RestTemplate();
	    List<Long> result = null;
	    try {
	    	ResponseEntity<List<Long>> response = restTemplate.exchange(
	    		    url,
	    		    HttpMethod.GET,
	    		    null,
	    		    new ParameterizedTypeReference<List<Long>>() {}
	    	);
	    	result = response.getBody();
	    } catch (Exception e) {
//	        e.printStackTrace();
//	        // RestTemplate 예외 발생 시 반드시 콘솔에 남게 한다
//	        throw e; // 다시 던지면 트랜잭션 롤백
//			임시 userIdList 반환
	    	System.out.println("임시 데이터 들어감(551번째 줄) : " + e.getMessage());
	    	result = new ArrayList<>();
	    	result.add(1L);
	    	result.add(2L);
	    }
	    return result;
    }
    private List<Long> getProductIdsByUserId(Long userId) {
    	String url = "http://localhost:10100/api/products/user/" + userId + "/pIdList";
    	RestTemplate restTemplate = new RestTemplate();
	    List<Long> result = null;
	    try {
	    	ResponseEntity<List<Long>> response = restTemplate.exchange(
	    		    url,
	    		    HttpMethod.GET,
	    		    null,
	    		    new ParameterizedTypeReference<List<Long>>() {}
	    	);
	    	result = response.getBody();
	    } catch (Exception e) {
//	        e.printStackTrace();
//	        // RestTemplate 예외 발생 시 반드시 콘솔에 남게 한다
//	        throw e; // 다시 던지면 트랜잭션 롤백
//			임시 userIdList 반환
	    	result = new ArrayList<>();
	    	result.add(1L);
	    	result.add(2L);
	    	result.add(3L);
	    }
	    return result;
    }
    
    public List<OrderItemDto> getListByProductId(Long productId) {
        List<OrderItem> oiList = orderItemRepository.findByProductId(productId);
        List<OrderItemDto> result = new ArrayList<>();

        for (OrderItem oi : oiList) {
        	OrderItemDto temp = new OrderItemDto();
        	temp.setOrderItemId(oi.getOrderItemId());
        	temp.setOrderedNum(oi.getOrderedNum());
        	temp.setProductName(oi.getProductName());
        	temp.setTotalAmount(oi.getTotalAmount());
        	temp.setStatus(oi.getStatus().toString());
        	result.add(temp);
        }
        return result;
    }
    
	public List<OrderDetailDto> getUserOrderList() {
		List<Long> uIdList = getUserIds();
		List<OrderDetailDto> result = new ArrayList<>();
		for(Long uId : uIdList) {
			List<OrderDetailDto> temp = getUserOrders(uId, null, null, null, null);
			if(temp != null) {
				for(OrderDetailDto odd : temp) {
					result.add(odd);
				}
			}
		}
		return result;
	}

	public int updateOrderItemStatus(Long trackingNumber, Long oiId, String... strArr) {
		int result = -1;
		OrderItem item = orderItemRepository.findById(oiId).orElse(null);
		if(item == null) {
			System.out.println("[" + LocalDateTime.now() + "] 상세 내역 없음");
			return result;
		}
		Order order = orderRepository.findById(item.getOrderedNum()).orElse(null);
		if(order == null) {
			System.out.println("[" + LocalDateTime.now() + "] 주문 내역 없음");
			return result;
		}
		DeliveryResponseDto responDto = null;
		if(trackingNumber != null) {
			try {
				String url = "http://localhost:10500/api/delivery/user/"+ order.getAddressId();
				ResponseEntity<AddressDto> addr = restTemplate.getForEntity(url, AddressDto.class);
				String purl = "http://localhost:10100/api/products/"+ item.getProductId();
				ResponseEntity<ProductDto> pDto = restTemplate.getForEntity(purl, ProductDto.class);
				if(addr == null || pDto == null) throw new Exception("no address");
				DeliveryRequestDto drd = makeDRD(pDto.getBody(),addr.getBody(), trackingNumber, item);
				url = "http://localhost:10500/api/delivery";
				responDto = restTemplate.postForEntity(url, drd ,DeliveryResponseDto.class).getBody();
				if(responDto == null) throw new Exception("delivery save fail");
				changeOIStatus(oiId,strArr);
			} catch(Exception e) {
				System.out.println("[배송서비스 주소 API 연결 실패](645번째 줄) : " + e.getMessage());
//				임시 통과
//				return  result;
				
			}
			
			return 1;
		}else {
			responDto = findByDeliveryId(oiId);
			
			if(changeOIStatus(oiId,strArr) == null || responDto == null){
				System.out.println("[" + LocalDateTime.now() + "] 바꿀 데이터가 없음");
				return result;
			}
			if(requestUpdateDeliveryStatus(responDto) != null) {
				System.out.println("[" + LocalDateTime.now() + "] 바꾸기 성공");
				return 1;
			}
			System.out.println("[" + LocalDateTime.now() + "] 바꾸기 실패");
			return result;
		}
	}
	
	private DeliveryResponseDto requestUpdateDeliveryStatus(DeliveryResponseDto drd) {
		DeliveryStatus dStatus = drd.getStatus();
		ResponseEntity<DeliveryResponseDto> result = null;
		String url = "http://localhost:10500/api/delivery/"+ drd.getDeliveryId() + "/status";
		if(dStatus.equals(DeliveryStatus.READY)) {
			dStatus = DeliveryStatus.IN_TRANSIT;
		}else {
			dStatus = DeliveryStatus.DELIVERED;
		}
		drd.setStatus(dStatus);
		result = restTemplate.exchange(
			url,
			HttpMethod.PUT,
			new HttpEntity<>(drd),
			DeliveryResponseDto.class
		);
		return result.getBody();
	}

	private DeliveryResponseDto findByDeliveryId(Long oiId) {
		String url = "http://localhost:10500/api/delivery";
		ResponseEntity<List<DeliveryResponseDto>> temp = restTemplate.exchange(
		        url,
		        HttpMethod.GET,
		        null,
		        new ParameterizedTypeReference<List<DeliveryResponseDto>>() {}
		);
		List<DeliveryResponseDto> idList = temp.getBody();
		for(DeliveryResponseDto drd : idList) {
			if(drd.getOrderItemId() == oiId){
				return drd;
			}
		}
		return null;
	}

	public OrderItem changeOIStatus(Long oiId, String... strArr) {
		OrderItem target = orderItemRepository.findById(oiId).orElse(null);
		OrderStatus os = null;
		if(target != null && !"DELETE".equals(strArr[0])) {
			os = target.getStatus();
			switch (os) {
			case PAID:
				os = OrderStatus.READY_SHIPMENT;
				break;
			case READY_SHIPMENT:
				os = OrderStatus.SHIPPED;
				break;
			case SHIPPED:
				os = OrderStatus.DELIVERED;
				break;
			case DELIVERED: 
				os = OrderStatus.PURCHASE_CONFIRMED;
				break;
			default:
				os = null;
			}
		}else if("DELETE".equals(strArr[0])) {
			if(strArr[1].equals("일시 품절")) {
				os = OrderStatus.CANCELLED_EMPTY;
			}else {
				os = OrderStatus.CANCELLED_NO_DELIVERY;
			}
		}
		if(os !=null) {
			target.setStatus(os);
			orderItemRepository.save(target);
		}else {
			target = null;
		}
		return target;
	}
	private DeliveryRequestDto makeDRD(ProductDto pDto,AddressDto ad, Long trackingNumber, OrderItem item) {
		DeliveryRequestDto result = new DeliveryRequestDto();
		result.setUserId(Integer.parseInt(item.getUserId().toString()));
	    result.setRecipientName(ad.getRecipientName());
	    result.setRecipientTel(ad.getRecipientTel());
	    result.setRecipientAddr1(ad.getRecipientAddr1());
	    result.setRecipientAddr2(ad.getRecipientAddr2());
	    result.setRecipientZipcode(ad.getRecipientZipcode());
	    result.setRecipientReq(ad.getRecipientReq());
	    
	    // 배송 관련 정보
	    result.setOrderItemId(Integer.parseInt(item.getOrderItemId().toString()));
	    result.setSellerAddressId(Integer.parseInt(pDto.getSellerAddressId()));
	    result.setProductId(Integer.parseInt(item.getProductId().toString()));

	    result.setTrackingNum(trackingNumber.toString());
	    result.setCourierName(pDto.getCourierName());
	    result.setEstimatedDeliveryDate(LocalDateTime.now().plusDays(7L));		//7일이내 배송으로 잡아놓음
	    result.setShippingFee(item.getShippingFee());
		
		return result;
	}
	
	private List<OrderItem> getOrderItemByUserId(Long userId){
		List<Long> pIds = getProductIdsByUserId(userId);
		List<OrderItem> oiArr = new ArrayList<>();
		for(Long pId : pIds) {
			List<OrderItemDto> temp = getListByProductId(pId);
			if(temp != null) {
				for(OrderItemDto dto : temp){
					OrderItem oi = orderItemRepository.findById(dto.getOrderItemId()).orElse(null);
					if(oi != null) {
						oiArr.add(oi);
					}
				}
			}
		}
		return oiArr;
	}
	
	// 주문 상세 내역 중 환불 요청된 목록 조회
	public List<ClaimDto> claimFindBy(Long userId, ClaimType type) {
		List<OrderItem> oiArr = getOrderItemByUserId(userId);
		List<ClaimDto> result = new ArrayList<>();
		if(oiArr != null && oiArr.size()>0) {
			for(OrderItem oi : oiArr) {
				try {
					List<Claim> claims = claimRepository.findByOrderItemIdAndType(oi.getOrderItemId(),type);
					if(claims != null) {
						for(Claim c : claims) {
							ClaimDto dto = new ClaimDto();
							dto.setClaimId(c.getClaimId());
							dto.setOrderNumber(oi.getOrderedNum());
							dto.setCreatedAt(c.getClaimedAt());
							dto.setReason(c.getReason());
							dto.setStatus(c.getStatus());
							dto.setAmount(oi.getTotalAmount());
							result.add(dto);
						}
					}
				}catch (Exception e) {
					System.out.println("아마 orderItemId가 같은 claim객체가 2개 있는 거일 거임 해당 문제는 다음 줄에서 확인 => 일단 그냥 통과[801번째 줄]");
				}
			}
		}
		return result;
	}

	public Claim processRefund(Long refundId, String method,Long SellerAddressId) {
		Claim claim = claimRepository.findById(refundId).get();
		if(claim != null) {
			if("DELETE".equals(method)) {
				claim.setStatus(ClaimStatus.REJECTED);
			}else {
				claim.setStatus(ClaimStatus.APPROVED);
			}
			claim = claimRepository.save(claim);
			if(claim == null) {
				System.out.println("["+LocalDateTime.now()+"] 저장 실패 : System exception -> didn't save DB");
			}
		}
		return claim;
	}
	
	//판매완료(구매확정)된 주문목록 찾기(필터링)
	public List<OrderItem> getSaledOrderItemList(OrderItem... oiArr) {
		List<OrderItem> result = new ArrayList<>();
		for(OrderItem oi : oiArr) {
			if(OrderStatus.PURCHASE_CONFIRMED.equals(oi.getStatus())) {
				result.add(oi);
			}
		}
		return result;
	}
	
	public List<OrderItemDto> getSellerOrders(Long sellerId) {
        List<Long> pIdList = getProductIdsByUserId(sellerId);
        List<OrderItemDto> result = new ArrayList<>();
        for(Long pId : pIdList) {
        	List<OrderItemDto> tempList = getListByProductId(pId);
        	if(tempList != null && tempList.size() > 0) {
        		for(OrderItemDto odid : tempList) {
        			try {
	        			String url = "http://localhost:10400/api/payment/order/" + odid.getOrderedNum(); 
	        			Map<String, Object> temp = restTemplate.exchange(
	        				url, 
	        				HttpMethod.GET,
	        				null,
	        				new ParameterizedTypeReference<Map<String, Object>>() {}
	        			).getBody();
	        			odid.setCardType((String)temp.get("cardType"));
        			}catch(Exception e) {
        				System.out.print("연결 실패 : [" + e.getMessage() + "] => 852번째 줄");
        				odid.setCardType("임시");
        			}
        			result.add(odid);
        		}
        	}
        }
        for(OrderItemDto oi : result) {
        	System.out.println(oi);
        }
        return result;
    }
	public List<OrderItemDto> getSellerOrdersProcessing(Long sellerId) {
        List<Long> pIdList = getProductIdsByUserId(sellerId);
        List<OrderItemDto> result = new ArrayList<>();
        for(Long pId : pIdList) {
        	List<OrderItemDto> tempList = getListByProductId(pId);
        	if(tempList != null && tempList.size() > 0) {
        		for(OrderItemDto odid : tempList) {
        			String status = odid.getStatus();
        			 boolean flag = status.equals(OrderStatus.PAID.name()) ||
    						status.equals(OrderStatus.READY_SHIPMENT.name()) ||
    						status.equals(OrderStatus.SHIPPED.name())||
    						status.equals(OrderStatus.DELIVERED.name())||
    						status.equals(OrderStatus.PURCHASE_CONFIRMED.name());
    				if(flag) {
	        			try {
		        			String url = "http://localhost:10400/api/payment/order/" + odid.getOrderedNum(); 
		        			Map<String, Object> temp = restTemplate.exchange(
		        				url, 
		        				HttpMethod.GET,
		        				null,
		        				new ParameterizedTypeReference<Map<String, Object>>() {}
		        			).getBody();
		        			odid.setCardType((String)temp.get("cardType"));
	        			}catch(Exception e) {
	        				System.out.print("연결 실패 : [" + e.getMessage() + "] => 888번째 줄");
	        				odid.setCardType("임시");
	        			}
	        			if(status.equals(OrderStatus.PURCHASE_CONFIRMED.toString())||
	        				status.equals(OrderStatus.DELIVERED.toString())) {
	        				result.add(odid);
	        			}else {
	        				result.add(0, odid);
	        			}
    				}
        		}
        	}
        }
        return result;
    }
	public Map<String, Integer> orderItemGetAllStatus(List<OrderItemDto> orderItemList) {
		Map<String, Integer> result = new HashMap<>();
		int paidCount = 0;
	    int readyCount = 0;
	    int completeCount = 0;
	    int processingCount = 0;
	    for(OrderItemDto dto : orderItemList) {
	    	String status = dto.getStatus();
	    	try {
	    		processingCount++;
	    		switch (status) {
	    		case "PAID": 
	    			paidCount++;
	    			break;
	    		case "READY_SHIPMENT":
	    		case "SHIPPED": 
	    			readyCount++;
	    			break;
	    		case "DELIVERED":
	    		case "PURCHASE_CONFIRMED": 
	    			completeCount++;
	    			processingCount--;
	    			break;
	    		default:
	    			throw new IllegalArgumentException("Unexpected value: " + status);
	    		}
			} catch (IllegalArgumentException e) {
				System.out.println("주문 상태가 범주 안에 존재하지 않음 : 해당 orderItemId => " + dto.getOrderItemId());
			}
	    }
	    result.put("paidCount", paidCount);
	    result.put("readyCount", readyCount);
	    result.put("completeCount", completeCount);
	    result.put("processingCount", processingCount);
		return result;
	}
	
	public Map<String, Integer> claimGetAllStatus(List<ClaimDto> claimList) {
		Map<String, Integer> result = new Hashtable<>();
		int rejectCount = 0;
	    int readyCount = 0;
	    int completeCount = 0;
	    int processingCount = 0;
	    for(ClaimDto c : claimList) {
	    	ClaimStatus status = c.getStatus();
	    	try {
	    		processingCount++;
	    		switch (status) {
	    		case REQUESTED: 
	    			readyCount++;
	    			break;
	    		case APPROVED: 
	    			completeCount++;
	    			processingCount--;
	    			break;
	    		case REJECTED: 
	    			rejectCount++;
	    			break;
	    		default:
	    			throw new IllegalArgumentException("Unexpected value: " + status);
	    		}
			} catch (IllegalArgumentException e) {
				System.out.println("환불/교환 신청 상태가 범주 안에 존재하지 않음 : 해당 claimId => " + c.getClaimId());
			}
	    }
	    result.put("rejectCount", rejectCount);
	    result.put("readyCount", readyCount);
	    result.put("completeCount", completeCount);
	    result.put("processingCount", processingCount);
		return result;
	}	
	
	// 리뷰관련 로직
	@Transactional
	public void confirmPurchase(Long orderItemId) {
	    OrderItem item = orderItemRepository.findById(orderItemId)
	        .orElseThrow(() -> new RuntimeException("주문 항목 없음"));

	    item.setStatus(OrderStatus.PURCHASE_CONFIRMED);
	    orderItemRepository.save(item);
	}
	
	public List<OrderItemDto> getConfirmedItemsByUser(Long userId) {
	    List<OrderItem> items = orderItemRepository.findByUserIdAndStatus(userId, OrderStatus.PURCHASE_CONFIRMED);
	    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
	    
	    return items.stream().map(item -> {
	        // 1. 상품 정보 조회
	        String prodUrl = "http://localhost:10100/api/products/" + item.getProductId();
	        ProductDto product = null;
	        try {
	            ResponseEntity<ProductDto> prodRes = restTemplate.getForEntity(prodUrl, ProductDto.class);
	            product = prodRes.getBody();
	        } catch (Exception e) {
	            product = ProductDto.builder()
	                    .name("상품명 정보 없음")
	                    .image("/img/default.png")
	                    .build();
	            System.out.println("[상품 API 실패] 기본값 대체");
	        }

	        // 2. 판매자 상호명 조회
	        String userUrl = "http://localhost:10000/api/users/" + item.getUserId() + "/company-name";
	        String companyName;
	        try {
	            ResponseEntity<String> companyRes = restTemplate.getForEntity(userUrl, String.class);
	            companyName = companyRes.getBody();
	        } catch (Exception e) {
	            companyName = "상호명 정보 없음";
	            System.out.println("[회원 API 실패] 기본 상호명 대체");
	        }
	        
	        // 3. 주문 날짜 조회 및 포맷
	        String orderedNum = item.getOrderedNum();
	        Order order = orderRepository.findById(orderedNum)
	                .orElseThrow(() -> new RuntimeException("주문 정보 없음"));
	        String orderedAt = order.getOrderedAt().format(formatter);

	        // 3. DTO 조립
	        return OrderItemDto.builder()
	                .orderItemId(item.getOrderItemId())
	                .productId(item.getProductId())
	                .orderedNum(item.getOrderedNum())
	                .productName(product.getName())
	                .image(product.getImage())
	                .quantity(item.getQuantity())
	                .price(item.getPrice())
	                .discountPrice(item.getDiscountPrice())
	                .shippingFee(item.getShippingFee())
	                .totalAmount(item.getTotalAmount())
	                .status(item.getStatus().name())
	                .userId(item.getUserId())
	                .companyName(companyName)
	                .orderedAt(orderedAt)
	                .build();
	    }).collect(Collectors.toList());
	}

	public Map<String, Integer> getTotalCount(List<OrderDetailDto> odList) {
		int paidOrReadyCount = 0;
		int shippedCount = 0;
		int deliveredCount = 0;
		for(OrderDetailDto odDto : odList) {
			List<OrderItemDto> oiList = odDto.getItems();
			for(OrderItemDto oiDto : oiList) {
				switch(oiDto.getStatus()) {
				case "PAID":
					paidOrReadyCount++;
					break;
				case "READY_SHIPMENT":
				case "SHIPPED":
					shippedCount++;
					break;
				case "DELIVERED":
					deliveredCount++;
					break;
				}
			}
		}
		Map<String, Integer> result = new HashMap<>();
		result.put("paidOrReadyCount", paidOrReadyCount);
		result.put("shippedCount", shippedCount);
		result.put("deliveredCount", deliveredCount);
		return result;
	}

	public List<OrderDetailDto> getAll() {
		List<Order> orderList = orderRepository.findAll();
		List<OrderDetailDto> result = new ArrayList<>();
		for (Order order : orderList) {
            List<OrderItem> items = orderItemRepository.findByOrderedNum(order.getOrderedNum());

            // 상품/판매자정보 조합 (상세 참고)
            List<OrderItemDto> itemDtos = items.stream().map(item -> {
                ProductDto product;
                String prodUrl = "http://localhost:10100/api/products/" + item.getProductId();
                try {
                    ResponseEntity<ProductDto> prodRes = restTemplate.getForEntity(prodUrl, ProductDto.class);
                    product = prodRes.getBody();
                } catch (Exception e) {
                    product = ProductDto.builder().name("테스트상품").image("").build();
                }
                // 판매자 상호명 조회
                String userUrl = "http://localhost:10000/api/users/company-name";
                String companyName;
                
                try {            	
                	ResponseEntity<String> companyRes = restTemplate.exchange(
                			userUrl,
                			HttpMethod.GET,
                			new HttpEntity<String>(product.getUserId()),
                			String.class
                	);
                	companyName = companyRes.getBody();
                } catch(Exception e) {
                	System.out.println("[RestTemplate 오류 발생] => 임시 회사 이름 주입(325번째 줄)");
                	companyName = "주식회사 베스트커머스";
                }
                
                String sellerName = getUserName(Long.parseLong(product.getUserId()));
                String buyerName = getUserName(order.getUserId());
                String cardType = getCardType(order.getPaymentId());
                return OrderItemDto.builder()
                		.orderItemId(item.getOrderItemId())
                        .productId(item.getProductId())
                        .orderedNum(item.getOrderedNum())
                        .productName(product.getName())
                        .image(product.getImage())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .discountPrice(item.getDiscountPrice())
                        .shippingFee(item.getShippingFee())
                        .totalAmount(item.getTotalAmount())
                        .status(item.getStatus().name())
                        .sellerName(sellerName)
                        .buyerName(buyerName)
                        .userId(item.getUserId())
                        .cardType(cardType)
                        .companyName(companyName)
                        .build();
            }).toList();
            
            itemDtos = itemDtos.stream().filter(i -> ORDER_STATUSES.contains(i.getStatus())).toList();

            if (itemDtos.isEmpty()) continue;

            OrderResponseDto orderDto = OrderResponseDto.builder()
                    .orderedNum(order.getOrderedNum())
                    .paymentId(order.getPaymentId())
                    .addressId(order.getAddressId())
                    .orderedAt(order.getOrderedAt().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")))
                    .totalAmount(order.getTotalAmount())
                    .build();

            int itemsTotal = itemDtos.stream()
                    .mapToInt(dto -> (dto.getDiscountPrice() > 0 ? dto.getDiscountPrice() : dto.getPrice()) * dto.getQuantity())
                    .sum();
            int shippingTotal = itemDtos.stream().mapToInt(OrderItemDto::getShippingFee).sum();
            result.add(OrderDetailDto.builder()
                    .order(orderDto)
                    .items(itemDtos)
                    .itemsTotal(itemsTotal)
                    .shippingTotal(shippingTotal)
                    .build()
            );
        }
		return result;
	}

}

