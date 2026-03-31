package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wcf_audit_log")
public class WechathlinkAuditLog extends WechathlinkAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long wechatAccountId;
    private Long operatorUserId;
    private String operatorUserName;
    private String actionType;
    private String resourceType;
    private String resourceId;
    private String resultStatus;
    private String summary;
    private String detailJson;
}
