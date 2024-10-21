package com.nowcoder.community.consumer;

import com.alibaba.fastjson.JSONObject;
import com.nowcoder.community.dao.PostLikeMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Map;

@Component
public class LikeEventConsumer {

    @Autowired
    private PostLikeMapper postLikeMapper;

    @KafkaListener(topics = {"like"})
//    @Transactional // 确保数据库操作的事务性
    public void handleLikeMessage(String message, Acknowledgment ack) {
        // 解析消息
        if (message == null || message.isEmpty()) {
            return; // 无效消息
        }

        Map<String, Object> event = JSONObject.parseObject(message, Map.class);
        if (event == null || event.isEmpty()) {
            return; // 无效消息
        }

        // 获取消息中的数据
        int userId = (Integer) event.get("userId");
        int entityType = (Integer) event.get("entityType");
        int entityId = (Integer) event.get("entityId");
        boolean liked = (Boolean) event.get("liked");

        // 数据库操作：根据点赞状态插入或删除点赞记录
        if (liked) {
            // 用户点赞：插入一条点赞记录
            postLikeMapper.insertPostLike(entityId, userId, new Date());
        } else {
            // 用户取消点赞：删除点赞记录
            postLikeMapper.cancelPostLike(entityId, userId);
        }

//         手动提交偏移量，确保消息已经正确处理
        ack.acknowledge();
    }
}
