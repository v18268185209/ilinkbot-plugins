package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wcf_webhook_delivery")
public class WechathlinkWebhookDelivery extends WechathlinkAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long wechatAccountId;
    private Long eventId;
    private String deliveryType;
    private String targetUrl;
    private String requestBody;
    private String responseBody;
    private Integer responseCode;
    private String deliveryStatus;
    private Integer attemptCount;
    private String errorMessage;
    private String traceId;
}
