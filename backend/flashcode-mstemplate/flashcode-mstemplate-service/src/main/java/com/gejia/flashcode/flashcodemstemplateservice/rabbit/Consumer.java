package com.gejia.flashcode.flashcodemstemplateservice.rabbit;

import com.gejia.flashcode.flashcodemstemplateservice.domain.MessageDTO;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.stereotype.Component;

@Component
public class Consumer {

    @RabbitListener(queuesToDeclare = @Queue("testQueue"))
    public void listenerQueue(MessageDTO messageDTO){
        System.out.println("收到消息为: " + messageDTO);
        System.out.println(MessageDTO.class);
    }
}
