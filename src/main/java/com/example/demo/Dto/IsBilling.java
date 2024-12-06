package com.example.demo.Dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IsBilling {
    private Long userId;
    private boolean isBilling;
}
