package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wcf_login_session")
public class WechathlinkLoginSession extends WechathlinkAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionCode;
    private String baseUrl;
    private String qrCodeUrl;
    private String qrCodeContent;
    private String sessionStatus;
    private Long wechatAccountId;
    private String errorMessage;
    private LocalDateTime expiredAt;
}
