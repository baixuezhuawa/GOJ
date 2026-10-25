package com.gusl.common.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** ContestRankSnapshotRecord，保存赛后归档数据。 */
@Data
@TableName("contest_rank_snapshot")
public class ContestRankSnapshotRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contestId;

    private Long lastSequence;

    private Long elapsedMillis;

    private String rowsJson;

}
