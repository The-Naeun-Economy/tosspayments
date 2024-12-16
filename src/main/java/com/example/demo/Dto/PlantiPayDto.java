package com.example.demo.Dto;

import lombok.Data;

@Data
public class PlantiPayDto {
    private Long userId = 2L;
    private Long sellerId = 2L;
    private String orderName;
    private int amount;
    private String status = "PAYMENT";
    private String redirectUri = "https://repick.site";

    public PlantiPayDto(String orderName, int amount){
        this.orderName = orderName;
        this.amount = amount;
    }
}
