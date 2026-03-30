package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.entity;

import cn.net.rjnetwork.qixiaozhu.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class WechathlinkAuditEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;
}
