package com.nowcoder.community.service;

import com.nowcoder.community.dao.DiscussPostMapper;
import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.util.SensitiveFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

import static com.nowcoder.community.util.CommunityConstant.ENTITY_TYPE_POST;

@Service
public class DiscussPostService {

    @Autowired
    private DiscussPostMapper discussPostMapper;

    @Autowired
    private SensitiveFilter sensitiveFilter;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private LikeService likeService;

    public List<DiscussPost> findDiscussPosts(int userId, int offset, int limit) {
        return discussPostMapper.selectDiscussPosts(userId, offset, limit);
    }

    public int findDiscussPostRows(int userId) {
        return discussPostMapper.selectDiscussPostRows(userId);
    }

    public int addDiscussPost(DiscussPost post) {
        if (post == null) {
            throw new IllegalArgumentException("参数不能为空!");
        }

        // 转义HTML标记
        post.setTitle(HtmlUtils.htmlEscape(post.getTitle()));
        post.setContent(HtmlUtils.htmlEscape(post.getContent()));
        // 过滤敏感词
        post.setTitle(sensitiveFilter.filter(post.getTitle()));
        post.setContent(sensitiveFilter.filter(post.getContent()));

        return discussPostMapper.insertDiscussPost(post);
    }

    public DiscussPost findDiscussPostById(int id) {
        return discussPostMapper.selectDiscussPostById(id);
    }

    public int updateCommentCount(int id, int commentCount) {
        return discussPostMapper.updateCommentCount(id, commentCount);
    }

    /**
     * 查询所有帖子并返回为一个列表
     */
    public List<DiscussPost> findAllPosts() {
        int offset = 0;
        int limit = 20;  // 每次分页查询20条
        List<DiscussPost> allPosts = new ArrayList<>();
        List<DiscussPost> postsPage;

        // 分页获取所有帖子
        do {
            postsPage = discussPostMapper.selectDiscussPosts(0, offset, limit);  // userId=0 表示查询所有帖子
            allPosts.addAll(postsPage);
            offset += limit;
        } while (postsPage.size() == limit);  // 当查询数量小于limit，说明已到最后一页

        return allPosts;
    }

    /**
     * 获取点赞数前 limit 名的帖子
     */
    public List<Map<String, Object>> findTopLikedPosts(int limit) {
        String topPostsKey = "post_like_top";

        // 从 Redis Sorted Set 中获取点赞前 limit 名的帖子 ID 和点赞数
        Set<Integer> topPostIds = redisTemplate.opsForZSet().reverseRange(topPostsKey, 0, limit - 1);

        List<Map<String, Object>> topPosts = new ArrayList<>();
        if (topPostIds != null) {
            for (Integer postId : topPostIds) {
                Map<String, Object> postInfo = new HashMap<>();
                postInfo.put("postId", postId);
                // 获取对应帖子的点赞数量
                Double likeCount = redisTemplate.opsForZSet().score(topPostsKey, postId);
                postInfo.put("likeCount", likeCount != null ? likeCount.longValue() : 0);
                topPosts.add(postInfo);
            }
        }
        return topPosts;
    }

    @Scheduled(initialDelay = 0, fixedRate = 10000)  // 每隔10秒执行一次
    public void refreshTopPosts() {
        String topPostsKey = "post_like_top";

        // 获取所有帖子
        List<DiscussPost> allPosts = findAllPosts();

        // 获取前100个点赞数最高的帖子
        List<DiscussPost> top100Posts = allPosts.stream()
                .sorted((p1, p2) -> {
                    long likeCount1 = likeService.findEntityLikeCount(ENTITY_TYPE_POST, p1.getId());
                    long likeCount2 = likeService.findEntityLikeCount(ENTITY_TYPE_POST, p2.getId());
                    return Long.compare(likeCount2, likeCount1);  // 按点赞数倒序排序
                })
                .limit(100)
                .collect(Collectors.toList());

        // 更新Redis排行榜
        redisTemplate.delete(topPostsKey);  // 清空现有排行榜数据

        for (DiscussPost post : top100Posts) {
            long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId());
            redisTemplate.opsForZSet().add(topPostsKey, post.getId(), likeCount);
        }
        System.out.println("排行榜已刷新");
    }

}
