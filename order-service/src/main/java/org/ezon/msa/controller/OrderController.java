package org.ezon.msa.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ezon.msa.Pagination;
import org.ezon.msa.dto.ClaimDto;
import org.ezon.msa.dto.ClaimRequestDto;
import org.ezon.msa.dto.OrderDetailDto;
import org.ezon.msa.dto.OrderItemDto;
import org.ezon.msa.dto.OrderRequestDto;
import org.ezon.msa.dto.OrderResponseDto;
import org.ezon.msa.entity.Claim;
import org.ezon.msa.enums.ClaimType;
import org.ezon.msa.service.OrderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}
	
	// 1. 주문 생성
	@PostMapping
	public ResponseEntity<OrderResponseDto> createOrder(@RequestBody OrderRequestDto req) {
		try {
	        OrderResponseDto resp = orderService.createOrder(req);
	        return ResponseEntity.ok(resp);
	    } catch (Exception e) {
	        e.printStackTrace();
	        // 로그 파일이나 콘솔에 반드시 에러 메시지가 남게 함
	        return ResponseEntity.status(500).body(null);
	    }
	} 
	
	// 2. 주문 단일 상세 조회 (주문번호로)
    @GetMapping("/{orderedNum}")
    public ResponseEntity<OrderDetailDto> getOrderDetail(@PathVariable String orderedNum) {
        return ResponseEntity.ok(orderService.getOrderDetail(orderedNum));
    }

    // 3. (구매자) 내 주문 목록 조회(주문/배송)
    @GetMapping("/users")
    public ResponseEntity<List<OrderDetailDto>> getUserOrders(
    		@RequestParam Long userId,
    		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
    		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
    		@RequestParam(required = false) String status,
    		@RequestParam(required = false) String keyword
    		) {
        return ResponseEntity.ok(orderService.getUserOrders(userId, startDate, endDate, status, keyword));
    }
    // 3. (구매자) 내 주문 목록 조회(주문/배송)
    @GetMapping("/users/count")
    public ResponseEntity<Map<String, Integer>> getUserOrdersCount(
    		@RequestParam Long userId) {
    	List<OrderDetailDto> temp = orderService.getUserOrders(userId, null,null,null,null);
    	Map<String, Integer> result = orderService.getTotalCount(temp);
    	
        return ResponseEntity.ok(result);
    }
    
    // (구매자) 내 주문 목록 조회(취소/환불/교환)
    @GetMapping("/users/claims")
    public ResponseEntity<List<OrderDetailDto>> getUserOrdersByClaims(
    		@RequestParam Long userId,
    		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
    		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
    		@RequestParam(required = false) String status,
    		@RequestParam(required = false) String keyword
    		) {
        return ResponseEntity.ok(orderService.getUserOrdersByClaims(userId, startDate, endDate, status, keyword));
    }

    // 6. (구매자) 주문 취소/환불/반품 요청 (orderItemId 단위)
    @PutMapping("/items/{orderItemId}/claims")
    public ResponseEntity<Void> claimOrderItem(@PathVariable Long orderItemId, @RequestBody ClaimRequestDto req) {
        orderService.claimOrder(orderItemId, req);
        return ResponseEntity.ok().build();
    }


    /*	added logic	*/
    
    // 7. (판매자) 내 판매 주문 목록 조회
    @GetMapping("/sellers")
    public ResponseEntity<List<OrderItemDto>> getSellerOrders(@RequestParam Long sellerId) {
        return ResponseEntity.ok(orderService.getSellerOrders(sellerId));
    }

    // 8. (판매자) 판매자별 판매 내역
    @GetMapping("/sellers/{sellerId}")
    public ResponseEntity<Map<String, Object>> getSellerOrderHistory(@PathVariable Long sellerId,
    	@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int perPage) {
    	Map<String, Object> result = new HashMap<>();
    	List<OrderItemDto> orderItemList = orderService.getSellerOrdersProcessing(sellerId);
    	if(orderItemList == null) {
    		result.put("message", "load fail");
    		return ResponseEntity.status(500).body(result);
    	}
    	result.put("message", "load success");
    	result.put("orderList", Pagination.paging(orderItemList, perPage, page));
    	result.put("statusCount", orderService.orderItemGetAllStatus(orderItemList));
    	result.put("totalPage", Pagination.totalPage(orderItemList,perPage));
    	return ResponseEntity.ok(result);
    }
 // 13. (관리자) 거래내역 내역
    @GetMapping("/users/orderList")
    public ResponseEntity<Map<String,Object>> getUserOrderListAdmin(
    	@RequestParam int page) {
    	Map<String, Object> result = new HashMap<>();
    	List<OrderDetailDto> temp = orderService.getAll();
    	result.put("orderList",Pagination.paging(temp, page));
    	result.put("totalPage", Pagination.totalPage(temp));
        return ResponseEntity.ok(result);
    }
 // 14. (판매자) 거래 상세 내역 상태 변경 및 배송 추가
    @PostMapping("/orderItem/{oiId}/seller")
    public ResponseEntity<Map<String, String>> updateOrderItemStatus(
    	@RequestBody Map<String, String> json, @PathVariable Long oiId) {
    	Map<String,String> result = new HashMap<>();
    	String trackingNum = json.get("trackingNumber");
    	if(trackingNum != null && oiId != null) {
	    	int process = orderService.updateOrderItemStatus(Long.parseLong(trackingNum), oiId, "POST");
	    	if(process == 1) {
				result.put("message", "process success");
				return ResponseEntity.ok(result);
			}
    	}
    	result.put("message", "process fail");
    	return ResponseEntity.status(500).body(result);
    }
    
    // 14. (판매자) 거래 상세 내역 상태 변경 및 배송 추가
    @PutMapping("/orderItem/{oiId}/seller")
    public ResponseEntity<Map<String, String>> changeOrderItemStatus(@PathVariable Long oiId) {
    	Map<String,String> result = new HashMap<>();
    	int process = orderService.updateOrderItemStatus(null, oiId, "PUT");
		if(process == 1) {
			result.put("message", "process success");
			return ResponseEntity.ok(result);
		}else {
			result.put("message", "process fail");
			return ResponseEntity.status(500).body(result);
		}
    }
    // 15. 주문 내역 상태 변경
    @DeleteMapping("/orderItem/{oiId}/seller")
    public ResponseEntity<Map<String, String>> OrderItemCancel(@PathVariable Long oiId,
    	@RequestBody Map<String, String> json) {
    	Map<String,String> result = new HashMap<>();
		if(orderService.changeOIStatus(oiId, "DELETE", (String)json.get("reason"))!=null) {
			result.put("message", "process success");
			return ResponseEntity.ok(result);
		}else {
			result.put("message", "process fail");
			return ResponseEntity.status(500).body(result);
		}
    }
    
  // 16. 환불 내역 목록 불러오기
  	@GetMapping("/claims/seller/{userId}")
  	public ResponseEntity<Map<String, Object>> getRefunds(@PathVariable Long userId,
  		@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int perPage,
  		@RequestParam String type){
  		Map<String, Object> result = new HashMap<String, Object>();
  		try {
  			List<ClaimDto> claimList =
  				orderService.claimFindBy(userId,ClaimType.valueOf(type));
  			if(claimList != null) {
  				result.put("claimList", Pagination.paging(claimList, perPage, page));
  		    	result.put("statusCount", orderService.claimGetAllStatus(claimList));
  				result.put("totalPage", Pagination.totalPage(claimList, perPage));
  				result.put("message", "load success");
  			}else {
  				result.put("message", "load fail");
  			}
  		} catch (Exception e) {
  			System.out.print(e.getMessage() + " 예외 발생\n자세한 원인 : ");
  			e.printStackTrace();
  			result.put("message", "exception");
  			return ResponseEntity.status(500).body(result);
  		}
  		return ResponseEntity.ok(result);
  	}
  	
  	// 17. 환불 승인/거부
  	@RequestMapping(value = "/claims/{refundId}/seller",
  		method = {RequestMethod.DELETE,RequestMethod.PUT})
  	public ResponseEntity<Map<String, Object>> updateRefund(@PathVariable Long refundId,
  		HttpServletRequest request, @RequestParam Long SellerAddressId){
  		Map<String, Object> result = new HashMap<String, Object>();
  		if(SellerAddressId !=null) {
  			try {
  				Claim claim = orderService.processRefund(refundId, request.getMethod(),SellerAddressId);
  				if(claim != null) {
  					result.put("message", "process success");
  					return ResponseEntity.ok(result);
  				}else {
  					result.put("message", "process fail");
  				}
  			} catch (Exception e) {
  				System.out.print(e.getMessage() + " 예외 발생 발생\n자세한 원인 : ");
  				e.printStackTrace();
  				result.put("message", "exception");
  			}
  		}else{
  			result.put("message", "no AddressId");
  		}
  		return ResponseEntity.status(500).body(result);
  	}
    
  	
  	// Review관련 로직
    @PutMapping("/items/{orderItemId}/confirm")
    public ResponseEntity<Void> confirmPurchase(@PathVariable Long orderItemId) {
        orderService.confirmPurchase(orderItemId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/items/purchase-confirmed/{userId}")
    public ResponseEntity<List<OrderItemDto>> getConfirmedOrderItems(@PathVariable Long userId) {
        List<OrderItemDto> confirmedItems = orderService.getConfirmedItemsByUser(userId);
        System.out.println("confirmedItems: " + confirmedItems);
        return ResponseEntity.ok(confirmedItems);
    }


}

