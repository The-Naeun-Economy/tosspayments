package com.example.demo.Service;

import com.example.demo.Dto.IsBilling;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
public class KafkaServiceImpl implements KafkaService {
    private final KafkaTemplate<String, IsBilling> kafkaTemplate;

    @Override
    public void sendMessage(IsBilling isBilling) {
        kafkaTemplate.send("tosspayments", isBilling);
        System.out.println("Producer Sent : " + isBilling);
    }
    @KafkaListener(id = "tosspayments", topics = "tosspayments")
    public void consume(IsBilling isBilling) {
        System.out.println("Consumer IsBilling : " + isBilling);
        System.out.println("Consumer IsBilling : " + isBilling.isBilling());
        //서비스 생성
        //내용은 Dto/IsBilling  확인
    }
}
