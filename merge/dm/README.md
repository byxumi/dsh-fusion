# merge/dm —— dsh-mobile-apk (dm) 资产

本目录是从 https://github.com/kelai141/dsh-mobile-apk (v0.14.3) 引入的源码资产，
**不参与 APK 构建**，作为本合并仓库（DSH-Fusion）的功能插件与参考实现存放处。

## 内容

| 目录 | 内容 | 用途 |
|---|---|---|
| plugins/           | 7 个 @dsh-android/* 插件源码 | CI 构建 tgz 后经 df 插件商店安装 |
| vendor/            | dsh-undo-savepoint、dshmarketplace-plugin（含 lib 产物） | 直接可 pack 安装 |
| dsh-client-ui-responsive / dsh-host-web-compat / dsh-shell-termux | 与 plugins/ 平级（保持 dm 原始 file: 相对路径布局）| WebUI 兼容层 |
| runtime-logic/     | dm 壳侧 Kotlin 参考实现（WatchdogV2/UndoGate/UpdateManager/SnapshotTransaction） | 移植参考，见 docs/MERGE.md §3 |

## 插件清单（plugins/）

- @dsh-android/dsh-android-bridge@0.2.4 —— 安卓授权桥（ADB 授权状态机、fail-closed 执行、审计）
- @dsh-android/dsh-android-browser@0.1.0 —— 隔离 AI 浏览器工作台
- @dsh-android/dsh-android-file-open@0.1.0 —— 文件直达会话
- @dsh-android/dsh-android-linux-env@0.1.2 —— Linux 环境能力
- @dsh-android/dsh-android-manage@0.3.0 —— 手机控制（无障碍 + Shizuku 双通道）
- @dsh-android/dsh-android-vdisplay@0.1.0 —— 虚拟屏（Shizuku 特权通道）
- @dsh-android/dsh-model-capability@0.2.1 —— 模型能力探测

peer 依赖 @deepseek-ai/dsh-tools@^0.2.0-rc.2 + @deepseek-ai/cordis@4.0.4，需 dsh 0.2.x 运行时。

## 构建

见仓库根 scripts/build-merge-plugins.sh 与 .github/workflows/plugins.yml。
