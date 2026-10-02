## DSH-Fusion 0.1.0 (df 1.9.8 + dm 0.14.3)

DSH-Folk (df) 与 dsh-mobile-apk (dm) 的首次合并构建。

### 合并内容

- 保留 df 的原生 UI 与容器化设计:主页 / 终端 / 插件 / 插件商店 / 设置(八类) / 主题 / 备份
- 保留 df 的 proot rootfs 容器运行时与运行时管理(下载 / 更新 / 重装 / 导入)
- 引入 dm 的运行时实现逻辑(新增 merge 层,见 docs/MERGE.md):
  - 引擎看门狗 EngineWatchdog:5s HTTP 探活 + 指数退避(5s→80s)+ 自动重启 + 自动配置回滚
  - 配置快照与自动回滚 MergeUndoSnapshot:崩溃纪元防循环,保留 8 份快照
  - window.androidBridge 兼容桥 MergeAndroidBridge:dm 客户端插件降级可用
- 引入 dm 功能插件源码(@dsh-android/* 全家桶 + undo + marketplace + web compat):
  经 plugins.yml 构建 tgz,由插件商店本地安装(需 beta 0.2.x 运行时)

### 注意

- @dsh-android/* 插件 peer 依赖 dsh-tools ^0.2.0-rc.2,只能在 beta 通道运行时上安装
- dm 的 BrowserHost / Vdisplay / Shizuku 特权传输为降级桥(返回 not-wired),完整移植见路线图

来源:DFH https://github.com/byxumi/dsh-fusion (df: IPF-Sinon/DSH-Folk@7d8015f, dm: kelai141/dsh-mobile-apk@v0.14.3)
