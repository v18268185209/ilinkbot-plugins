package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wcf_message_dispatch")
public class WechathlinkMessageDispatch extends WechathlinkAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long wechatAccountId;
    private Long runtimeId;
    private String peerUserId;
    private String dispatchType;
    private String payloadJson;
    private String dispatchStatus;
    private Integer retryCount;
    private String errorMessage;
    private String sourceType;
    private String sourceId;
    private String traceId;
}
