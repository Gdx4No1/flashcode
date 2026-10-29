package com.gejia.flashcode.flashcodeportalservice.flash.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gejia.flashcode.flashcodecommoncore.utils.BeanCopyUtil;
import com.gejia.flashcode.flashcodecommonredis.service.RedisService;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.ChatHistoryDTO;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.entity.ChatHistory;
import com.gejia.flashcode.flashcodeportalservice.flash.eums.FlashMsgRole;
import com.gejia.flashcode.flashcodeportalservice.flash.mapper.ChatHistoryMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class RedisDbChatMemoryConfig implements ChatMemory {

    @Autowired
    private RedisService redisService;

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    @Value("${chat.memory.max-message:5}")
    private int maxMessage;

    @Value("${chat.memory.tll:24}")
    private long tll;

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return;
        }
        Long appId = Long.valueOf(conversationId);
        String listKey = chatHistoryCacheKey(conversationId);
        for (Message message : messages) {
            if (message instanceof UserMessage userMessage) {
//                UserMessage userMessage = (UserMessage)message;
                persist(appId, FlashMsgRole.USER_MSG, userMessage.getText(), listKey);
            } else if (message instanceof AssistantMessage assistantMessage) {
                persist(appId, FlashMsgRole.AI_MSG, assistantMessage.getText(), listKey);
            }
        }
    }

    private void persist(Long appId, FlashMsgRole userMsg, String userMessage, String listKey) {
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setAppId(appId);
        chatHistory.setMsgRole(userMsg.getValue());
        chatHistory.setContent(userMessage);
        chatHistoryMapper.insert(chatHistory);

        ChatHistoryDTO dto = new ChatHistoryDTO();
        BeanCopyUtil.copyProperties(chatHistory, dto);
        redisService.leftPushForList(listKey, dto);
        redisService.trimList(listKey, maxMessage);
        redisService.expire(listKey, tll, TimeUnit.HOURS);
    }

    @Override
    public List<Message> get(String conversationId) {
        String listKey = chatHistoryCacheKey(conversationId);
        long cacheListSize = redisService.getCacheListSize(listKey);
        List<ChatHistoryDTO> chatHistoryDTOList = Collections.emptyList();
        if (cacheListSize > 0) {
            chatHistoryDTOList = redisService.getCacheListByRange(listKey, 0, maxMessage - 1, ChatHistoryDTO.class);
        } else {
            Long appId = Long.valueOf(conversationId);
            List<ChatHistory> chatHistoryList = chatHistoryMapper.selectTopByAppId(appId, maxMessage);
            if (!CollectionUtils.isEmpty(chatHistoryList)) {
                chatHistoryDTOList = BeanCopyUtil.copyListProperties(chatHistoryList, ChatHistoryDTO::new);
                redisService.setCacheList(listKey, chatHistoryList);
                redisService.trimList(listKey, maxMessage);
//            redisService.expire(listKey, Duration.ofHours(tll).getSeconds());
                redisService.expire(listKey, tll, TimeUnit.HOURS);
            }
        }
        return toAIMessage(chatHistoryDTOList);
    }

    @Override
    public void clear(String conversationId) {
        String listKey = chatHistoryCacheKey(conversationId);
        redisService.deleteObject(listKey);
        Long appId = Long.valueOf(conversationId);
        chatHistoryMapper.delete(new LambdaQueryWrapper<ChatHistory>().eq(ChatHistory::getAppId, appId));
    }

    private String chatHistoryCacheKey(String conversationId) {
        return "chat:history:list:" + conversationId;
    }

    private List<Message> toAIMessage(List<ChatHistoryDTO> chatHistoryDTOList) {
        if (CollectionUtils.isEmpty(chatHistoryDTOList)) {
            return Collections.emptyList();
        }
        List<ChatHistoryDTO> ordered = new ArrayList<>(chatHistoryDTOList);
        Collections.reverse(ordered);
        List<Message> messageList = new ArrayList<>();
        for (ChatHistoryDTO dto : ordered) {
            if (FlashMsgRole.USER_MSG.getValue() == dto.getMsgRole()) {
                messageList.add(new UserMessage(dto.getContent()));
            } else if (FlashMsgRole.AI_MSG.getValue() == dto.getMsgRole()) {
                messageList.add(new AssistantMessage(dto.getContent()));
            }
        }
        return messageList;
    }
}
