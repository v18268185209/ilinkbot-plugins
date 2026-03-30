package cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller.base;

import cn.net.rjnetwork.qixiaozhu.result.ResultBody;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "cn.net.rjnetwork.qixiaozhu.plugins.wechathlink.controller")
public class WechathlinkAdminExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResultBody<Void> handleException(Exception ex) {
        return ResultBody.error(ex.getMessage());
    }
}
