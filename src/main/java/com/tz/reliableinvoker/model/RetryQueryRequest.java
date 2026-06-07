package com.tz.reliableinvoker.model;

import java.util.List;

/**
 * 重试查询请求
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 00:48:02
 * @version 1.0.0-SNAPSHOT
 */
public class RetryQueryRequest {

    /** 业务场景 */
    private String scene;

    /** 状态列表 */
    private List<Integer> statusList;

    /** 分片总数，默认1 */
    private int shardTotal = 1;

    /** 当前分片索引 */
    private int shardIndex;

    /** 每次处理数量上限，默认100 */
    private int limit = 100;

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public List<Integer> getStatusList() {
        return statusList;
    }

    public void setStatusList(List<Integer> statusList) {
        this.statusList = statusList;
    }

    public int getShardTotal() {
        return shardTotal;
    }

    public void setShardTotal(int shardTotal) {
        this.shardTotal = shardTotal;
    }

    public int getShardIndex() {
        return shardIndex;
    }

    public void setShardIndex(int shardIndex) {
        this.shardIndex = shardIndex;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
