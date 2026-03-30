package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wcf_event")
public class WechathlinkEvent extends WechathlinkAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long wechatAccountId;
    private String direction;
    private String eventType;
    private String fromUserId;
    private String toUserId;
    private Long messageId;
    private String contextToken;
    private String bodyText;
    private String mediaPath;
    private String mediaFileName;
    private String mediaMimeType;
    private String rawJson;
    private Long ownerUserId;
}
