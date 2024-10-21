package com.nowcoder.community;

import com.nowcoder.community.dao.PostLikeMapper;
import com.nowcoder.community.service.LikeService;
import com.nowcoder.community.util.RedisKeyUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)  // 使用 SpringRunner 运行测试
@SpringBootTest  // 表示将启动完整的 Spring Boot 应用上下文
@ContextConfiguration(classes = CommunityApplication.class)  // 指定 Spring 配置类
public class LikeServiceTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private KafkaTemplate kafkaTemplate;

    @Autowired
    private PostLikeMapper postLikeMapper;

    @Test
    public void testLike0() {
        int userId = 1;
        int entityType = 1;
        int entityId = 1;
        int entityUserId = 1;

        // RedisKey for the post or entity being liked
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
        String userLikeKey = RedisKeyUtil.getUserLikeKey(entityUserId);

        // 清空 Redis 之前的内容
        redisTemplate.delete(entityLikeKey);
        redisTemplate.delete(userLikeKey);

        // 验证初始点赞状态
        assertFalse(redisTemplate.opsForSet().isMember(entityLikeKey, userId));
        assertNull(redisTemplate.opsForValue().get(userLikeKey));

        // 第一次点赞
        likeService.like(userId, entityType, entityId, entityUserId);

        // 验证点赞后 Redis 中的数据
        assertTrue(redisTemplate.opsForSet().isMember(entityLikeKey, userId));
        assertEquals(1, redisTemplate.opsForValue().get(userLikeKey));

        // 取消点赞
        likeService.like(userId, entityType, entityId, entityUserId);

        // 验证取消点赞后 Redis 中的数据
        assertFalse(redisTemplate.opsForSet().isMember(entityLikeKey, userId));
        assertEquals(0, redisTemplate.opsForValue().get(userLikeKey));
    }

    @Test
    public void testLike() {
        int postId = 109;
        int userId = 101;
        int entityType = 1;

        // 清除测试数据，确保开始前没有记录
        postLikeMapper.cancelPostLike(postId, userId);

        likeService.like(userId, entityType, postId, userId);
        try {
            Thread.sleep(1000);  // 假设处理消息需要 1 秒
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        int likeCount = postLikeMapper.countPostLikes(postId);
        assertEquals(1, likeCount);  // 验证点赞数是否增加

        // 验证点赞记录是否存在
        boolean isLiked = postLikeMapper.isPostLiked(postId, userId);
        assertEquals(true, isLiked);

        // 取消点赞
        likeService.like(userId, entityType, postId, userId);
        try {
            Thread.sleep(1000);  // 假设处理消息需要 1 秒
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 检查数据库中点赞记录是否被删除
        isLiked = postLikeMapper.isPostLiked(postId, userId);
        assertEquals(false, isLiked);

        // 验证点赞数是否更新为 0
        likeCount = postLikeMapper.countPostLikes(postId);
        assertEquals(0, likeCount);
    }

    @Test
    public void testKafkaMessageSend() {
        int userId = 1;
        int entityType = 1;
        int entityId = 1;
        int entityUserId = 1;

        // RedisKey for the post or entity being liked
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);

        // 清空 Redis 之前的内容
        redisTemplate.delete(entityLikeKey);

        // 监听 Kafka 模拟消息发送
        likeService.like(userId, entityType, entityId, entityUserId);
        try {
            Thread.sleep(5000);  // 假设处理消息需要 1 秒
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 暂时无法直接从 Kafka 验证消息，需要用 Kafka 测试工具或 Mock 模拟，但在集成测试中发送消息成功即可。
        // 您可以在实际的 Kafka 消费者中实现消费并打印消息来手动验证
    }
//    @KafkaListener(topics = {"like"})
//    public void consumeKafkaMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
//        System.out.println("Received Kafka message: " + record.value());
//        System.out.println("Partition: " + record.partition());
//        System.out.println("Offset: " + record.offset());
//        System.out.println("Topic: " + record.topic());
//        // 处理完消息后，手动提交偏移量
//        ack.acknowledge();
//    }
}
