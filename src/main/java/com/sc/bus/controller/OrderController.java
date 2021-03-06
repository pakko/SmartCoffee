package com.sc.bus.controller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.sc.bus.service.LocationService;
import com.sc.bus.service.MemoryService;
import com.sc.bus.service.OrderService;
import com.sc.model.Location;
import com.sc.model.Menu;
import com.sc.model.Order;
import com.sc.model.OrderLocation;
import com.sc.util.Constants.OrderUpdateStatus;


@Controller
@RequestMapping(value = "/order")
public class OrderController {

	private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
	
    @Autowired
    private OrderService orderService;
    @Autowired
    private LocationService locationService;
    @Autowired
    private MemoryService memoryService;
    
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    String lastModified = formatter.format(new Date());

    /*
     * Client will call it at startup to get all orders' location
     * Different Card Id could map to the same location ID, Client should warn waiter to the Card.
     */
    @RequestMapping(value = "/all", method = RequestMethod.GET)
    public @ResponseBody List<OrderLocation> getAllOrdersLocation(final HttpServletResponse response) {
    	
    	response.setHeader("Last-Modified", lastModified);
    	
    	List<OrderLocation> orderLocationList = new ArrayList<OrderLocation>();
    	
    	List<Location> locationList = locationService.findAll();
    	
    	for(Location location: locationList) {
    		OrderUpdateStatus status = OrderUpdateStatus.NOTUSED;
    		//1, check duplicate location
    		if(!location.getLocationId().equals("0")) {
    			List<Location> dupLocations = locationService.findByLocationId(location.getLocationId());
        		if(dupLocations.size() > 1) {
        			//logger.warn("4 - Abnormal status(Get All Orders), CardId with multi same LocationId, Maybe multi user stay in one place!");
        			status = OrderUpdateStatus.CARD_WITH_MULTI_LOCATION;
        		}
    		}
    		
    		//2, check duplicate order with same cardId
    		List<Order> orders = orderService.findByCardIdAndFinishAndDate(location.getCardId(), false, new Date());
    		if(orders.size() > 1) {
    			//logger.warn("4 - Abnormal status(Get All Orders), This Card ID already used or received wrong Card ID!");
    			status = OrderUpdateStatus.ORDER_WITH_SAME_CARD;
    		}
    		location.setLocationDesc(MemoryService.getMappingColor(location.getCardId()));
    		if(orders.size() <= 0) {
    			OrderLocation orderLocation = new OrderLocation(null, location, status);
    			orderLocationList.add(orderLocation);
    		}
    		else {
    			for(Order order: orders) {
    				OrderLocation orderLocation = new OrderLocation(order, location, status);
        			orderLocationList.add(orderLocation);
    			}
    		}
    		
    	}

    	/*
    	// Find not finish and today's orders.
    	List<Order> orders = orderService.findByFinishAndDate(false, new Date());
    	
    	Map<String, Order> distiguishOrder = new HashMap<String, Order>();
    	Map<String, Integer> dupIdOrder = new HashMap<String, Integer>();
    	
    	for(Order order: orders) {
    		String cardId = order.getCardId();
    		Order exOrder = distiguishOrder.get(cardId);
    		if(exOrder == null) {
    			distiguishOrder.put(cardId, order);
    		}
    		else {
    			dupIdOrder.put(exOrder.getOrderId(), 1);
    			dupIdOrder.put(order.getOrderId(), 1);
    		}
    	}
    	
    	for(Order order: orders) {
    		String cardId = order.getCardId();
    		List<Location> locations = locationService.findByCardId(cardId);
    		
    		OrderUpdateStatus status = OrderUpdateStatus.NOTUSED;
    		int locationSize = locations.size();
    		
    		//it's ok that location is null
    		Location location = null;
    		if(locationSize > 0) {
    			location = locations.get(0);
    		}
    		if(locationSize > 1) {
    			//Still add to list, client should warn waiter to check this abnormal status.
    			logger.warn("Abnormal status, One Card ID should not mapping to multi locations!");
    			status = OrderUpdateStatus.ABNORMAL;
    		}
    		
    		Integer dupFlag = dupIdOrder.get(order.getOrderId());
    		if(dupFlag != null) {
    			logger.warn("Abnormal status, This Card ID already used or received wrong Card ID!");
    			status = OrderUpdateStatus.ABNORMAL;
    		}
    		
			OrderLocation orderLocation = new OrderLocation(order, location, status);
			orderLocationList.add(orderLocation);
		}
    	*/
    	
    	Collections.sort(orderLocationList,new Comparator<OrderLocation>(){
            public int compare(OrderLocation arg0, OrderLocation arg1) {
            	int card1 = Integer.valueOf(arg0.getLocation().getCardId());
            	int card2 = Integer.valueOf(arg1.getLocation().getCardId());
            	if(card1 < card2)
            		return -1;
                return 1;
            }
        });
    	return orderLocationList;
    }
    
    /*
     * Client will call it every x seconds, just return modified data.
     */
    @RequestMapping(value = "/newly", method = RequestMethod.GET)
    public @ResponseBody List<OrderLocation> getNewlyOrdersLocation(
    		final HttpServletRequest request, 
    		final HttpServletResponse response) {
    	
    	List<OrderLocation> newlyOrderLocations = memoryService.getNewlyUpdatedLocations();
    	if(newlyOrderLocations.size() > 0) {
    		lastModified = formatter.format(new Date());
    	}
    	
    	String ifModifiedSince = request.getHeader("If-Modified-Since");
    	if(ifModifiedSince != null && ifModifiedSince.equals(lastModified)) {
    		System.out.println(ifModifiedSince);
        	response.setStatus(304);
        	return null;
    	}
    	
    	response.setHeader("Last-Modified", lastModified);
    	
    	List<OrderLocation> orderLocations = new ArrayList<OrderLocation>(newlyOrderLocations);
    	memoryService.clearUpdatedLocations();
    	return orderLocations;
    }
    
    /*
     * Update order
     * Precondition, order status is not finished
     */
    @RequestMapping(value = "/{orderId}", method = RequestMethod.PUT, produces = "application/json;charset=UTF-8")
    public @ResponseBody Map<String, String> updateOrder(@PathVariable String orderId, @RequestBody List<Menu> menus) {
    	
    	Map<String, String> res = new HashMap<String, String>();
    	res.put("code", "0");
    	res.put("msg", "Success");
    	
    	List<Order> orders = orderService.findByOrderId(orderId);
    	if(orders.size() <= 0) {
    		logger.error("Order ID " + orderId + " is not exist!");
    		res.put("code", "1");
        	res.put("msg", "Order is not exist!");
    		return res;
    	}
    	if(orders.size() > 1) {
    		logger.error("There're multi order exist for order " + orderId + " !");
    		res.put("code", "2");
        	res.put("msg", "Multi order ID!");
        	return res;
    	}
    	
    	Order order = orders.get(0);
    	List<Menu> existMenuList = order.getMenus();
    	for(Menu existMenu: existMenuList) {
    		// if exist menu not in requested update menus, continue 
    		List<Menu> updateMenuList = orderService.getMenuByMenuId(existMenu.getProductId(), menus);
    		if(updateMenuList.size() <= 0)
    			continue;
    		if(updateMenuList.size() > 1) {
    			res.put("code", "3");
            	res.put("msg", "Abnormal status, One updated order should not exist multi same product ID!");
            	return res;
    		}
    		int currentAmount = updateMenuList.get(0).getCurrentAmount();
    		int amount = updateMenuList.get(0).getAmount();
    		if(currentAmount < 0 || currentAmount > amount) {
    			res.put("code", "4");
            	res.put("msg", "Update amount is not correct, should not less than 0 or greater than " + amount);
    			return res;
    		}
    		// update exist menu's amount
    		existMenu.setCurrentAmount(currentAmount);
    		//logger.info(existMenu.toString());
    	}
    	//logger.info(existMenuList.toString());
    	order.setMenus(existMenuList);
    	// if all exist menus' amount are 0, then mark it as finish
    	if(orderService.checkMenuFinish(existMenuList)) {
    		order.setFinish(true);
    		orderService.saveToHistory(order);
    	}
    	else {
    		order.setFinish(false);
    		orderService.deleteFromHistory(order);
    	}
    	orderService.update(order);
    	
    	return res;
    }
	
    /*
     * Used for test
     */
    @RequestMapping(value = "/{id}/{isFinish}", method = RequestMethod.POST)
    public @ResponseBody void addOrder(@PathVariable String id, @PathVariable Boolean isFinish) {
    	List<Menu> list = new ArrayList<Menu>();
		
		Menu menu1 = new Menu("11", "Coffee", 22.0, 1, 1);
		list.add(menu1);
		
		Order order = new Order(UUID.randomUUID().toString(), id, list, new Date().getTime(), 58.0, isFinish, null);
		orderService.add(order);
    	Location location = new Location(id, id, MemoryService.getMappingColor(id));
		locationService.add(location);
    }
    
    /*
     * Used for test
     */
    @RequestMapping(value = "", method = RequestMethod.DELETE)
    public @ResponseBody void deleteOrder() {
    	orderService.drop();
    }
}
