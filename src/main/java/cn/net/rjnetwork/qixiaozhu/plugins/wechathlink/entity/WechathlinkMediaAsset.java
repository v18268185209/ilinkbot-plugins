package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wcf_media_asset")
public class WechathlinkMediaAsset extends WechathlinkAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long wechatAccountId;
    private Long eventId;
    private Long dispatchId;
    private String assetType;
    private String storagePath;
    private String fileName;
    private String mimeType;
    private String sha256;
    private String downloadStatus;
    private String errorMessage;
}
