# DSH-Fusion 合并架构说明

> dsh-mobile-apk (dm) × DSH-Folk (df) 合并工程。目标：**保留 df 的原生 UI 与容器化设计，
> 采用 dm 的运行时实现逻辑与功能插件**。

## 1. 合并原则

| 维度 | 采用哪边 | 理由 |
|---|---|---|
| 原生 App UI（主页/终端/插件/设置/主题/备份） | **df** | df 的 UI 完成度高、可玩性强（首页多布局、主题商店、设置搜索、八类设置分页） |
| 容器化设计（proot rootfs、下载式运行时、运行时管理/更新/重装） | **df** | df 把运行时与 App 解耦，容器可独立更新；dm 是内嵌快照，二者架构不兼容 |
| 运行时实现逻辑（看门狗、配置快照回滚、桥兼容、事务化更新思想） | **dm → 适配层** | dm 的 WatchdogV2 / UndoGate / 桥协议是成熟实现；以新增代码适配进 df，不破坏 df 既有类 |
| 功能插件（@dsh-android/* 全家桶、undo、marketplace、web compat） | **dm → 仓库资产** | 源码收进 merge/dm/，CI 构建 tgz，经 df 插件商店安装；peer 依赖需 dsh 0.2.x（beta 运行时） |

**实现纪律**：合并层全部为**新增文件**（me.bmax.apatch.dsh.merge 包），对 df 既有文件的改动只限
最小侵入接入点（Application.onCreate 一行、WebView 桥注入一处）。

## 2. 目录地图

- app/src/main/java/me/bmax/apatch/dsh/merge/ —— 合并层 Kotlin（新增，本次核心交付）
  - MergeManifest.kt —— 版本/常量清单
  - MergeRuntime.kt —— 合并层入口（Application.onCreate 挂载）
  - EngineWatchdog.kt —— 引擎看门狗（dm WatchdogV2 移植：5s 探活 + 指数退避 + 自动重启/回滚）
  - MergeUndoSnapshot.kt —— 配置快照与自动回滚（dm UndoGate 移植：崩溃纪元防循环）
  - MergeAndroidBridge.kt —— window.androidBridge 兼容桥（dm 桥协议降级实现）
  - MergeNotify.kt —— 壳侧低打扰通知通道
- merge/ —— dm 资产（源码，不进 APK 构建）
  - dm/plugins/ —— 7 个 @dsh-android/* 插件源码（bridge/browser/file-open/linux-env/manage/vdisplay/model-capability）
  - dm/vendor/ —— dsh-undo-savepoint、dshmarketplace-plugin（含 lib 产物）
  - dm/（与 plugins/ 平级）—— dsh-client-ui-responsive、dsh-host-web-compat、dsh-shell-termux
    （保持 dm 原始 file: 相对路径布局：插件经 file: 引用兄弟包，如
      dsh-android-linux-env -> ../../dsh-shell-termux）
  - dm/runtime-logic/ —— dm 壳侧核心 Kotlin 参考实现（WatchdogV2/UndoGate/UpdateManager/SnapshotTransaction）
- scripts/build-merge-plugins.sh —— 构建全部 dm 插件为 tgz
- .github/workflows/plugins.yml —— 插件打包 CI（产物即 df 插件商店可装的本地 tgz）
- docs/MERGE.md —— 本文档

## 3. 运行时逻辑移植明细

### 3.1 引擎看门狗（EngineWatchdog）

dm WatchdogV2 的核心闭环：引擎进入 RUNNING 后每 5s 对 http://127.0.0.1:<port>/ 做 HTTP 探活；
连续失败进入指数退避（5s→10s→20s→40s→80s 封顶，与 dm 同款 delayForFailureCount）；
连续失败 6 次（约 30s 无响应）自动 DshRuntime.restart()；连续失败 16 次（约 80s 仍起不来）
触发配置快照自动回滚（MergeUndoSnapshot.tryAutoRollback）。

与 df 语义的兼容点：仅在 DshPhase.RUNNING 阶段探活（下载/解压/启动中不打扰）；phase 变化
自动复位计数；全部走 df 公开 API（DshRuntime.state / restart()），不触碰内部字段。

### 3.2 配置快照与自动回滚（MergeUndoSnapshot）

df 原版只有手动配置备份（DshConfigBackup，依赖 dsh-config-manager 插件）。合并层补上 dm
UndoGate 的「引擎坏了自动回退」闭环：

- 引擎 RUNNING 后每 3 分钟检查一次，距上次快照 ≥ 5 分钟才建新快照（避免把启动中间态存成好状态）；
- 快照内容：rootfs/root/.dsh/profiles/web（settings.yaml + 补丁）及根部 settings.yaml，
  **绝不包含** sessions / workspaces / 附件等用户数据；
- 自动回滚遵守 dm 的防循环纪律：每个崩溃纪元只执行一次，距上次自动回滚 < 30 分钟不重复执行；
- 恢复前先把坏状态复制到 crash-state-<ts>/ 便于人工排查；
- 保留最近 8 份快照自动裁剪。

### 3.3 androidBridge 兼容桥（MergeAndroidBridge）

dm 插件客户端（vdisplay 面板、bridge 客户端）会探测 window.androidBridge 并调用其方法。
df 的 WebView 原本只注入 BlobBridge，缺这个对象时相关页面会崩或功能缺失。合并层注入同名
androidBridge 兼容桥：能映射到 df 现有能力的直接转发（剪贴板、系统暗色、沉浸式、通知、
设备信息），dm 壳侧独有而 df 没有的（BrowserHost / Vdisplay / Shizuku 特权传输 / ADB 授权）
按 dm 协议返回合法的「不可用」JSON，让页面走降级路径而不是抛异常。

## 4. dm 功能插件如何启用

@dsh-android/* 全家桶（bridge / browser / file-open / linux-env / manage / vdisplay /
model-capability）peer 依赖 @deepseek-ai/dsh-tools@^0.2.0-rc.2，**只在 dsh 0.2.x（beta
运行时）上可装**（df 同时支持 stable 0.1.7 与 beta 0.2.0 两个通道）。启用步骤：

1. 触发 .github/workflows/plugins.yml（workflow_dispatch），下载 dm-plugins artifact；
   artifact 内含 9 个 tgz（7 插件 + undo + marketplace，另含 3 个 web compat 包）。
2. 应用设置 → 功能 → 运行时 → 切换/安装 **beta** 通道运行时（0.2.x）。
3. 应用插件商店支持本地 .tgz 安装：选择构建出的 tgz，安装时 df 会用临时端口验证
   插件树能否加载，不通过自动卸载（安全兜底）。
4. 优先装 dsh-undo-savepoint 与 dshmarketplace-plugin（vendor，含 lib 产物，开箱即用），
   再装业务插件。

> 为什么不直接加进 df 的 SEED_PLUGINS：df 种子清单纪律要求「同时兼容 0.1.x 与 0.2.x」，
> 而 @dsh-android/* 只兼容 0.2.x，强制预装会破坏 stable 通道用户的插件树。保持手动/本地
> 安装是兼容性上的正确选择。

## 5. 关闭开关

SharedPreferences("merge_settings")：

- merge.watchdog.enabled（默认 true）：false 时看门狗不启动；
- merge.snapshot.enabled（默认 true）：false 时不自动建快照。

## 6. 许可与来源

- DSH-Folk (df)：GPL-3.0（LICENSE），基线版本 1.9.8，commit 7d8015f
  （https://github.com/IPF-Sinon/DSH-Folk）。
- dsh-mobile-apk (dm)：壳 MIT / 插件 MIT（见各 package.json），基线版本 0.14.3
  （https://github.com/kelai141/dsh-mobile-apk）。
- 合并层（merge/ 目录）为新增代码，以 dm 实现为参考（MIT 兼容），按 df 仓库 GPL-3.0 分发。

## 6.5 打开软件自动释放容器组件 + dsh 版本真源

DSH-Fusion 的运行逻辑（对应需求「打开软件自动释放容器组件，容器使用 Ubuntu，
dsh 版本依据官方 GitHub 仓库下载运行」）：

1. **打开软件自动释放容器组件**：[DshRuntime.autoStartOnLaunch] 默认**开**；
   MainActivity 打开即自动完成 —— 未安装 → DshRuntime.bootstrap() 自动下载并解压
   （Ubuntu 24.04 rootfs，proot/proroot）；已安装 → 自动拉起 HarnessService 启动 `dsh web`。
   无需手动点「启动」，可在设置关闭。
2. **容器使用 Ubuntu**：rootfs 由 runtime-builder/build-rootfs.sh 以 Ubuntu 24.04 noble
   为基础构建（UBUNTU_RELEASE=noble），node + dsh + pnpm 装入其中。
3. **dsh 版本依据官方 GitHub 仓库**：
   - 构建侧：build-rootfs.sh 的 DSH_VERSION 默认从官方仓库
     （deepseek-ai/deepseek-harness）最新 release tag（如 `dsh-v0.2.0-rc.2`）解析版本号，
     再交给 npm 安装该版本 —— 版本真源是官方 GitHub，下载走 npm（官方唯一可下载渠道，
     其 GitHub release 无资产）；可在外部用 DSH_VERSION 覆盖。
   - App 侧：打开自动释放容器后，后台经 UpdateChecker.fetchApiJson（直连 + gh-proxy 镜像）
     查询官方仓库最新 dsh 版本（DshRuntime.fetchOfficialDshVersion），与安装时落盘的
     KEY_RUNTIME_DSH 对比；官方有新版且本仓库 runtime 通道有对应版本时，走既有运行时
     更新提示（RuntimeCheckResult → runtimePrompt）下载新 rootfs。

### 6.6 两段式运行时：先容器，后 dsh（当前架构）

容器与 dsh 引擎解耦，按顺序装配：

1. **下载并启动 Ubuntu 容器**（容器实现沿用 df 的 proot/rootfs 方法）：
   - runtime-builder/build-rootfs.sh 以 `BASE_ONLY=1` 产出**基础容器**
     （Ubuntu 24.04 + node + pnpm，**不含 dsh**）；metadata.dsh="base"、
     metadata.version="base-ubuntu-noble-r<N>"。
   - 由 runtime.yml（workflow_dispatch, base_only=true）构建并发布到
     `runtime-latest` 滚动 release；App 打开后自动下载解压（DshRuntime.downloadAndInstall）。
2. **容器启动后，按官方 GitHub 版本安装 dsh**：
   - bootstrap() 在 setupResolvConf() 之后调用 [DshRuntime.ensureDshInstalled]：
     查询官方仓库最新 dsh 版本（fetchOfficialDshVersion，经 gh-proxy），与本地
     KEY_RUNTIME_DSH 比较；本地为空 / "base" / 版本不一致 → 容器内执行
     `npm install -g --prefix /usr/local @deepseek-ai/dsh@<官方版>`
     （先官方 registry，失败回退 npmmirror），装完回读 package.json 版本校验并落盘。
3. **最后启动 dsh web**：dsh 就绪后才 seedPlugins（预装插件依赖 dsh plugin）→
   startAndAwait() 启动 `dsh web`（proot/proroot）。

好处：容器一次下载长期复用；dsh 引擎按官方 GitHub 版本随时独立更新，
无需为换引擎重建整个 rootfs。一体容器（含 dsh）仍兼容：localDshVersion 与官方一致时跳过安装。

## 7. 后续路线（不在本版内）

- BrowserHost / Vdisplay / Shizuku 特权传输从 dm 完整移植为 df 能力（当前为降级桥）；
- dm 的在线运行时 manifest 更新与快照指纹检测接入 df 运行时管理；
- dm 的 dsh-client-ui-responsive 作为 WebUI 层可切换皮肤（当前 df 用 dsh-web-mobile）。
