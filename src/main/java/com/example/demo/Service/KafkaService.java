package com.example.demo.Service;

import com.example.demo.Dto.IsBilling;

public interface KafkaService {
    void sendMessage(IsBilling isBilling);
}
