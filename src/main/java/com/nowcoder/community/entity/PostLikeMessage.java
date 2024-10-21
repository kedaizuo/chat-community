package com.nowcoder.community.entity;

public class PostLikeMessage {

    private int entityId;  // 帖子的ID
    private int userId;    // 用户ID
    private long likeCount;  // 点赞的数量

    // getter 和 setter

    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }
}
