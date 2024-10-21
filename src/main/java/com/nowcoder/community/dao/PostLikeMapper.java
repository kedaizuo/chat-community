package com.nowcoder.community.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Date;
import java.util.List;

@Mapper
public interface PostLikeMapper {

    // 插入点赞
    int insertPostLike(@Param("postId") int postId, @Param("userId") int userId, @Param("createTime") Date createTime);

    // 取消点赞
    int cancelPostLike(@Param("postId") int postId, @Param("userId") int userId);

    // 查询帖子点赞数
    int countPostLikes(@Param("postId") int postId);

    // 查询用户是否对帖子点赞
    boolean isPostLiked(@Param("postId") int postId, @Param("userId") int userId);

    List<Integer> findUserIdsWhoLiked(@Param("postId") int postId);
}
