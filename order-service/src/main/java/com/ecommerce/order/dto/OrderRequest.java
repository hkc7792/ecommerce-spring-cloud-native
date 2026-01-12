package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;

 public record OrderRequest(

         @NotBlank
         Long customerId,
         List<OrderItemDto> orderLineItemsDtoList
) {
     public OrderRequest{
         if(customerId <= 0){
             throw new IllegalArgumentException("Customer ID must be greater than zero");
         }
         if(CollectionUtils.isEmpty(orderLineItemsDtoList)){
             throw new IllegalArgumentException("Order line items list cannot be empty");
         }
     }
 }

