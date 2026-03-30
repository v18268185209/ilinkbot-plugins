package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.base;

import cn.net.rjnetwork.qixiaozhu.result.ResultBody;

public abstract class WechathlinkBaseController {

    protected <T> ResultBody<T> renderSuccess(T data) {
        return ResultBody.success(data);
    }

    protected ResultBody<Void> renderSuccess() {
        return ResultBody.success(null);
    }
}
