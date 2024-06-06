package com.rai.order_service.service;

import com.rai.order_service.dto.InventoryResponse;
import com.rai.order_service.dto.OrderLinesItemsDto;
import com.rai.order_service.dto.OrderRequest;
import com.rai.order_service.event.OrderPlacedEvent;
import com.rai.order_service.model.Order;
import com.rai.order_service.model.OrderLineItems;
import com.rai.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.tools.Trace;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;


    public String placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLinesItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();

        order.setOrderLineItemsList(orderLineItems);

        List<String> skuCodes = order.getOrderLineItemsList().stream().map(OrderLineItems::getSkuCode).toList();

        log.info("Calling inventory service");

        //call Inventory Service, and place order id product is available in stock
        InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                .uri("http://inventory-service/api/inventory", uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();
        boolean allProductsInStock = Arrays.stream(inventoryResponseArray).allMatch(InventoryResponse::isInStock);

        if (allProductsInStock) {
            orderRepository.save(order);
            kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber()));
            return "Order placed successfully!";
        } else {
            throw new IllegalArgumentException("Product is not in stock , please try again later");
        }
    }

        private OrderLineItems mapToDto(OrderLinesItemsDto orderLinesItemsDto){
            OrderLineItems orderLineItems = new OrderLineItems();
            orderLineItems.setQuantity(orderLinesItemsDto.getQuantity());
            orderLineItems.setSkuCode(orderLinesItemsDto.getSkuCode());
            orderLineItems.setPrice(orderLinesItemsDto.getPrice());
            return orderLineItems;
        }
    }
