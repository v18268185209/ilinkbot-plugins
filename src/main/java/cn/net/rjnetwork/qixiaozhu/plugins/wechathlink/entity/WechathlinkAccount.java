package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wcf_wechat_account")
public class WechathlinkAccount extends WechathlinkAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String accountCode;
    private String accountName;
    private String baseUrl;
    private String botToken;
    private String ilinkUserId;
    private String loginStatus;
    private String pollStatus;
    private String lastError;
    private String getUpdatesBuf;
    private LocalDateTime lastPollAt;
    private LocalDateTime lastInboundAt;
    private Long ownerUserId;
}
