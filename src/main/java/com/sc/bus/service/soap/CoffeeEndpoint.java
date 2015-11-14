package com.sc.bus.service.soap;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.tempurl.Esalesitem;
import org.tempurl.Esalestotal;
import org.tempurl.Postesalescreate;
import org.tempurl.PostesalescreateResponse;
import org.tempurl.PostesalescreateResult;
import org.tempurl.ResponseHeader;

import com.sc.bus.service.MemoryService;
import com.sc.model.Menu;
import com.sc.model.Order;
import com.sc.util.Constants;
import com.sc.util.DateUtil;

@Endpoint
public class CoffeeEndpoint {
	
	//@Autowired
    //private OrderService orderService;
	@Autowired
    private MemoryService memoryService;
	
	SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
	SimpleDateFormat formatter2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  
	
	private static final String NAMESPACE_URI = "http://tempurl.org";
 
	/*
	 * 1, CardId, it's better transport from this API, or mapping to the recent NFC info, require waiter operate successfully.
	 * 2, Name, it's better transport from this API, or download menu list to store in our DB
	 * 
	 */
	@PayloadRoot(namespace = NAMESPACE_URI, localPart = "postesalescreate")
	@ResponsePayload
	public PostesalescreateResponse getPostSales(@RequestPayload Postesalescreate request) {
		System.out.println(request.toString());
		
		int retCode = 0;
		String retMessage = "Success";
		
		// 1) Get total sales info
		Esalestotal eSalesTotal = request.getAstrRequest().getEsalestotal();
		String orderId = eSalesTotal.getTxdocno();
		
		// If card ID is 0, then means POS don't send card ID to me, also save the order, but mark it as abnormal.
		//String cardId = String.valueOf(eSalesTotal.getTableno());
		/*if(cardId.equals("0")) {
			//add this order or not?
			retCode = -1;
			retMessage = "Order ID is empty";
			return getResponse(retCode, retMessage);
		}*/
		
		String txDate = eSalesTotal.getTxdateYyyymmdd();
		String txTime = eSalesTotal.getTxtimeHhmmss();
		Date date = null;
		try {
			date = formatter.parse(txDate + txTime);
		} catch (ParseException e) {
			System.out.println("Parse sales time error, use current time instead. " + e.getMessage());
			date = new Date();
		}
		String dateStr = formatter2.format(date);
		Long orderDate = DateUtil.getMilliseconds(dateStr);
		
		BigDecimal netAmount = eSalesTotal.getNetamount();
		Double totalPrice = netAmount.doubleValue();
		
		Boolean finish = false;
		
		String cardId = Constants.EmptyCardFlag;
		// 2) Get every item sales info
		List<Esalesitem> eSalesItems = request.getAstrRequest().getEsalesitems().getEsalesitem();
		List<Menu> menus = new ArrayList<Menu>();
		for(Esalesitem eSalesItem: eSalesItems) {
			String productId = eSalesItem.getItemcode();
			String name = eSalesItem.getItemname();
			Double price = eSalesItem.getNetamount().doubleValue();
			Integer amount = eSalesItem.getQty().intValue();
			
			String mappingCardId = MemoryService.getMappingCardId(name);
			if(mappingCardId != null) {
				cardId = mappingCardId;
			}
			Menu menu = new Menu(productId, name, price, amount, amount);
			menus.add(menu);
		}
		
		Order order = new Order(orderId, cardId, menus, orderDate, totalPrice, finish);
		System.out.println(order);
		//orderService.add(order);
		memoryService.receiveOrder(order);
		
		// 3) Send response
		return getResponse(retCode, retMessage);
	}
	
	private PostesalescreateResponse getResponse(int retCode, String retMessage) {
		ResponseHeader header = new ResponseHeader();
		header.setResponsecode(retCode);
		header.setResponsemessage(retMessage);
		header.setVersion("1.0");
		
		PostesalescreateResult post = new PostesalescreateResult();
		post.setHeader(header);
		
		PostesalescreateResponse response = new PostesalescreateResponse();
		response.setPostesalescreateResult(post);
		
		return response;
	}
}
