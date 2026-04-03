package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wcf_bot_runtime")
public class WechathlinkBotRuntime extends WechathlinkAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long wechatAccountId;
    private String runtimeType;
    private String baseUrl;
    private String botToken;
    private String ilinkUserId;
    private String runtimeStatus;
    private LocalDateTime expiresAt;
    private LocalDateTime lastHeartbeatAt;
    private LocalDateTime lastOnlineAt;
    private LocalDateTime lastOfflineAt;
    private Long replaceByRuntimeId;
    private Integer isActive;
    private String lastError;
}
