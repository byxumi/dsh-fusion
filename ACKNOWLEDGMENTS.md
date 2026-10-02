# Acknowledgments / 致谢

DSH-Fusion 是独立维护的项目,其代码与设计大量借鉴了以下开源项目。
我们保留各自原始许可证,本仓库在 [LICENSE](LICENSE)(GPL-3.0) 下分发。

## 核心来源

| 项目 | 仓库 | 许可 | 在本项目中的用途 |
|---|---|---|---|
| DSH-Folk | https://github.com/IPF-Sinon/DSH-Folk | GPL-3.0 | 原生 App UI、容器化设计(proot rootfs)、运行时管理、插件商店、主题/备份/设置体系 |
| dsh-mobile-apk | https://github.com/kelai141/dsh-mobile-apk | MIT(插件)/见各 LICENSE | 运行时实现逻辑(引擎看门狗、配置快照回滚、androidBridge 桥)、@dsh-android/* 功能插件、web compat 层 |
| FolkPatch | https://github.com/LyraVoid/FolkPatch | GPL-3.0 | DSH-Folk 的 UI 基础(经 DSH-Folk 继承) |
| DeepSeek Harness | https://www.npmjs.com/package/@deepseek-ai/dsh | MIT | 被启动的 AI 编码 Agent 本体 |

## DSH-Folk 的上游致谢(保留)

DSH-Folk 的 UI 直接复用 FolkPatch,容器与运行时交付思路来自 DSHA / DSHM,
这些致谢随代码一并保留:

- [FolkPatch](https://github.com/LyraVoid/FolkPatch) —— UI 基础(GPL-3.0)
- [APatch](https://github.com/bmax121/APatch) —— FolkPatch 的上游
- [DSHA](https://github.com/DSH-APP/DSHA) —— 无线 ADB 配对方案、容器运行逻辑参考
- [DSHM](https://github.com/RochelimitDawn/DSHM) —— 运行时在线交付与镜像测速方案
- [DeepSeek Harness](https://www.npmjs.com/package/@deepseek-ai/dsh) —— 被启动的本体
- [proot](https://github.com/proot-me/proot) / [proroot](https://github.com/coderredlab/proroot) / [Termux](https://github.com/termux/termux-app) —— 容器执行与 PTY 终端
- [Shizuku](https://github.com/RikkaApps/Shizuku) —— 免 root 特权通道
- [KernelSU](https://github.com/tiann/KernelSU) / [SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra) —— 界面设计参考

## dsh-mobile-apk 的插件来源

`merge/dm/` 目录下的插件与库源码来自 dsh-mobile-apk v0.14.3:

- plugins/:7 个 @dsh-android/* 插件(各含 MIT LICENSE 字段)
- vendor/:dsh-undo-savepoint、dshmarketplace-plugin
- dsh-client-ui-responsive / dsh-host-web-compat / dsh-shell-termux:web 兼容层
- runtime-logic/:壳侧 Kotlin 参考实现(WatchdogV2 / UndoGate / UpdateManager / SnapshotTransaction)

## 构建与分发

- CI 构建依据各仓库的 README / docs / workflows 整理,产物为 Android APK(debug/release)与插件 tgz 包。
- 本仓库不包含上游的 git 历史,来源与版本信息以上表为准。
