# 微信对话开放平台 - ilink 协议关键文档

## 核心 API 文档索引

### 客服消息收发
- 发送客服消息: `/doc/aispeech/confapi/thirdkefu/sendmsg.html`
- 接收用户消息: `/doc/aispeech/confapi/thirdkefu/recivemsg.html`
- 客服接入流程: `/doc/aispeech/confapi/thirdkefu/flow.html`
- 查看/修改客服状态: `/doc/aispeech/confapi/thirdkefu/getstate.html` / `changestate.html`
- H5客服鉴权: `/doc/aispeech/confapi/thirdkefu/h5auth.html`

### 第三方服务接口
- 第三方服务接口: `/doc/aispeech/confapi/thirdapi/thirdapi.html`
- 错误码说明: `/doc/aispeech/confapi/openinterface/errorcode.html`

### 机器人配置
- 获取 AccessToken: `/doc/aispeech/confapi/dialog/token`
- 机器人配置: `/doc/aispeech/confapi/dialog/bot/query.html`
- 简单问答导入: `/doc/aispeech/confapi/dialog/bot/import.html`
- 发布: `/doc/aispeech/confapi/dialog/bot/publish.html`
- 发布进度: `/doc/aispeech/confapi/dialog/bot/progress.html`
- 异步任务查询: `/doc/aispeech/confapi/dialog/bot/fetch.html`

## 关键协议细节（从文档中提取）

### 发送消息 (sendmsg)
- URL: `https://chatbot.weixin.qq.com/openapi/sendmsg/{TOKEN}`
- 方法: POST, Content-Type: application/json
- 消息格式: XML 加密后放入 `{"encrypt": "..."}` body
- 加密: AES-CBC, KEY=前16字节为IV, APPID校验
- 支持消息类型:
  - 纯文本: `msg = "文本内容"`
  - 文本+推荐问法: `<a href="weixin://bizmsgmenu?msgmenucontent=...">...</a>`
  - 文本+超链接: `<a href="https://...">...</a>`
  - H5卡片: `{"news":{"articles":[...]}}`
  - 公众号图片: `{"image":{"media_id":"...","url":"..."}}`
  - 小程序卡片: `{"miniprogrampage":{...}}`
  - 合并回答: `{"multimsg":[...]}` (数组，每项为以上单一类型)

### 接收消息 (recivemsg)
- 回调配置: 发布管理 -> 应用绑定 -> 开放API -> 回调地址
- Content-Type: application/json
- 消息格式: `{"encrypted": "..."}` 解密后为 XML
- 解密后字段: userid, appid, content(msg), event, from, kfstate, channel, assessment, createtime
- from: 0=用户, 1=机器人, 2=人工客服
- kfstate: 0=待接入, 1=已接入, 2=对话关闭, 3=待转人工

### 错误码（重要）
- 1001: token无效
- 1002: 机器人审核没有通过
- 1008: appid/openid/msg 字段不能为空
- 1013: 加解密参数不一致
- 1019: 没开通开放api / 数据加密不正确
- 3005: 公众号未认证
- 3014: 敏感词 ← **当前项目可能遇到的问题！**
- 110003: 内容黄反校验不通过
- 210205: 接口调用频繁 ← **频率限制！**

### 频率限制
- 210205: "接口调用频繁" — 说明有频率限制
- 文档未明确给出具体数值，但经验值约 10条/秒

## 与当前项目的差异分析

1. **当前项目使用 ilink 协议**（`ilinkai.weixin.qq.com`），文档中是**对话开放平台**（`chatbot.weixin.qq.com`）— 两者是不同的接入方式
2. ilink 协议的消息格式使用 `item_list` 数组，支持多种消息类型混合
3. 文档中提到的 `multimsg` 合并回答与 ilink 的 `item_list` 类似
4. 错误码 3014（敏感词）和 110003（黄反校验）可能导致消息发送失败但未被捕获
5. 频率限制 210205 需要在代码中处理
