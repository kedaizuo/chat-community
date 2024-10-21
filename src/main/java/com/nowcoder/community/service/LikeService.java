package com.nowcoder.community.service;

import com.nowcoder.community.dao.PostLikeMapper;
import com.nowcoder.community.util.CommunityUtil;
import com.nowcoder.community.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LikeService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private PostLikeMapper postLikeMapper;

    // 点赞
    public void like(int userId, int entityType, int entityId, int entityUserId) {
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
        String userLikeKey = RedisKeyUtil.getUserLikeKey(entityUserId);
        Boolean liked = (Boolean) redisTemplate.execute(new SessionCallback<Boolean>() {
            @Override
            public Boolean execute(RedisOperations operations) throws DataAccessException {

                boolean hasKey = redisTemplate.hasKey(entityLikeKey);
                Boolean isMember;

                // 如果 Redis 中不存在该实体的点赞记录，从数据库中查询并更新缓存
                if (!hasKey) {
                    boolean isLikedInDB = postLikeMapper.isPostLiked(entityId, userId);
                    if (isLikedInDB) {
                        // 数据库中已点赞，同步缓存
                        isMember = true;
                    } else {
                        isMember = false;
                    }
                }
                else{
                    isMember = operations.opsForSet().isMember(entityLikeKey, userId);
                }
                operations.multi(); // 开启 Redis 事务
                Boolean liked;
                if (isMember) {
                    operations.opsForSet().remove(entityLikeKey, userId); // 取消点赞
                    operations.opsForValue().decrement(userLikeKey); // 减少用户收到的点赞数量
                    liked = false;
                } else {
                    operations.opsForSet().add(entityLikeKey, userId); // 添加点赞
                    operations.opsForValue().increment(userLikeKey); // 增加用户收到的点赞数量
                    liked = true;
                }
                operations.exec();
                return liked;
            }
        });
        sendLikeMessage(userId, entityType, entityId, liked);

    }

    // 查询某实体点赞的数量
    public long findEntityLikeCount(int entityType, int entityId) {
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
        Long likeCount;
        System.out.println("*************** into func");

        // 先从 Redis 中查询
        Boolean hasKey = redisTemplate.hasKey(entityLikeKey);
        System.out.println("*************** finsih redis look up");
        if (hasKey != null && hasKey) {
            // 如果 Redis 中存在该 key，获取点赞数
            likeCount = redisTemplate.opsForSet().size(entityLikeKey)-1;
            System.out.println("redis like count="+likeCount);
        }
        else{
            likeCount = (long) postLikeMapper.countPostLikes(entityId);
            System.out.println("*************** from database");
            System.out.println("like count="+likeCount);
            redisTemplate.opsForSet().add(entityLikeKey, -1);
            // 如果数据库中有点赞记录，将其缓存到 Redis 中
            if (likeCount > 0) {
                List<Integer> userIds = postLikeMapper.findUserIdsWhoLiked(entityId);
                for (Integer userId : userIds) {
                    redisTemplate.opsForSet().add(entityLikeKey, userId);
                }
            }
        }
        return likeCount;
    }

    // 查询某人对某实体的点赞状态
    public int findEntityLikeStatus(int userId, int entityType, int entityId) {
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);

        // 首先判断 Redis 中是否存在该 key
        Boolean hasKey = redisTemplate.hasKey(entityLikeKey);

        // 如果 Redis 中存在该 key，则从 Redis 查询
        if (hasKey != null && hasKey) {
            Boolean isMember = redisTemplate.opsForSet().isMember(entityLikeKey, userId);
            return isMember ? 1 : 0; // 1 表示已点赞，0 表示未点赞
        } else {
            // 如果 Redis 中不存在该 key，回退到数据库查询
            boolean isLikedInDB = postLikeMapper.isPostLiked(entityId, userId);
            if (isLikedInDB) {
                // 将用户点赞状态同步到 Redis
                redisTemplate.opsForSet().add(entityLikeKey, userId);
                return 1; // 已点赞
            } else {
                return 0; // 未点赞
            }
        }
    }


    // 查询某个用户获得的赞
    public int findUserLikeCount(int userId) {
        String userLikeKey = RedisKeyUtil.getUserLikeKey(userId);
        Integer count = (Integer) redisTemplate.opsForValue().get(userLikeKey);
        return count == null ? 0 : count.intValue();
    }
    private void sendLikeMessage(int userId, int entityType, int entityId, boolean liked) {
        Map<String, Object> message = new HashMap<>();
        message.put("userId", userId);
        message.put("entityType", entityType);
        message.put("entityId", entityId);
        message.put("liked", liked);  // 点赞状态：'like' 或 'cancel'

        // 发送异步消息到 Kafka
        kafkaTemplate.send("like", CommunityUtil.getJSONString(0, null, message));
    }

}
