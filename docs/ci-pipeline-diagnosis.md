# CI/CD 现状诊断与处置建议

> 核查日期：2026-09-15 ｜ 事实基准：`master` = `3abd5ab`（= tag `v7.1.0`）
> 唯一 workflow：`.github/workflows/release.yml`（337 行，7 个 job）

## 一、当前流水线结构

```
触发：PR 合并进 master（且源分支名以 release/ 开头）
     或 workflow_dispatch（可带 dry_run 输入）

validate (ubuntu)              ← 单元测试 + 版本号查重
  ├─ Run unit tests        ./gradlew testAll
  └─ Validate version      比对 gradle.properties vs 最新 tag
       │
       ├─ build-android        (ubuntu)  HMP_BUILD_TARGET=android  APK + AAB
       ├─ build-desktop-macos  (macos)   HMP_BUILD_TARGET=desktop  DMG
       ├─ build-desktop-windows(windows) HMP_BUILD_TARGET=desktop  MSI
       └─ build-desktop-linux  (ubuntu)  HMP_BUILD_TARGET=desktop  DEB + AppImage
            │
            └─ release (ubuntu)   收集产物 → 重命名 → SHA256 → 分类 Notes → 打 tag + 建 Release
                 │
                 └─ deploy-site (ubuntu)  上传 site/ → GitHub Pages
```

**发版动作链**（人工部分）：bump `gradle.properties` → 开 `release/x.y.z` 分支 → 开 PR → 合并进 master → 自动触发。

---

## 二、发现的问题（按严重度排序）

### 🔴 P0-1：`validate` job 缺 Android SDK，`testAll` 在 CI 上必失败

**证据**
- `release.yml` 全文**没有任何** `setup-android` / `android-actions` 步骤（已扫描确认）
- `validate` job **未设** `HMP_BUILD_TARGET` → `settings.gradle.kts` 走 **all 分支**（含 `:android:app`、`:android:core-player`）
- `testAll` 的任务定义里 **硬编码**了 `:shared-ui:testAndroidHostTest`（`build.gradle.kts:93`）
- `shared-ui` 用的是 `com.android.kotlin.multiplatform.library` 插件（`compileSdk 36` + `withHostTest`）→ **编译与运行该测试集必须有 Android SDK**

**后果**：`validate` 失败 → 后续 4 个 build job 与 release 全部 `needs: [validate]` 被跳过 → **流水线整体不可用**。

> 注意与旧状态的区别：改动**前**该步骤带 `continue-on-error: true`，任务名虽错但被吞掉，CI 恒绿（假绿）。改动**后**错误不再被吞 → 从「假绿」变成「真红」。**方向是对的**（暴露问题），但必须配套补 Android SDK 才能跑通。

**处置**：在 `validate` job 加 Android SDK 安装步骤，并显式声明构建目标。

```yaml
      - name: Setup Android SDK
        uses: android-actions/setup-android@v3
        with:
          packages: 'platforms;android-36 build-tools;36.0.0'

      - name: Run unit tests
        run: ./gradlew testAll --no-daemon --no-configuration-cache
        env:
          ANDROID_HOME: ${{ env.ANDROID_SDK_ROOT }}
```

需要核实的一点：`ubuntu-latest` 预装 SDK 的默认 `compileSdk` 未必是 36，所以要把 `platforms;android-36` 显式列进 `packages`。

---

### 🔴 P0-2：官网 `site/` 版本号停在 v6.13.1，落后实际版本两个 minor

**证据**（`master` 上的 `site/`）
| 文件 | 行 | 内容 |
|---|---|---|
| `index.html` | 26 | `<a href="changelog.html" class="top-badge">v6.13.1</a>` |
| `index.html` | 35 | `HMP v6.13.1`（首页 hero 徽标） |
| `download.html` | 135/166/208/209 | 四处下载链接全部指向 `releases/download/v6.13.1/...` |
| `changelog.html` | 88–216 | 最新条目 `v6.13.1`，**完全没有 v7.0.0 / v7.1.0** |

而 `gradle.properties` = `7.1.0`、最新 tag = `v7.1.0`。**首页显示 v6.13.1，下载按钮指向已过期的 Release 资产。**

**根因**：`deploy-site` job 只是 `upload-pages-artifact` + `deploy-pages`，**没有任何版本同步步骤**；site 里的版本号是**手工硬编码**的。

**已经有人做完了修复，但没合并**：远端 `origin/feature/site-sync` 分支（3 笔提交、28 文件、+5244/−558）包含：
- `23b4884` **站点元信息单源化并接入 Release 流水线自动同步** —— 新增 `site/js/config.js` 作唯一配置源，并在 `deploy-site` job 里加了 `Sync site config with released version` 步骤（从 `gradle.properties` + GitHub Release 现场改写 config.js，不回写仓库）
- `efa15e2` 站点 i18n —— 运行时语言切换 + 7 语种词典（de/en/es/fr/ja/ko/pt/zh）
- `8a2d97b` 官网全面改版 —— 多页面结构 + canvas 动态背景（新增 `features.html` / `gallery.html`）

该分支**领先 master 3 笔、落后 0 笔**（可 fast-forward 或直接开 PR 合并）。

**处置**：合并 `feature/site-sync`（同时解掉 P0-2 与 P0-1 的版本来源问题）。

---

### 🟠 P1：`validate` 的版本查重当前会直接 fail

**证据**：`gradle.properties` 的 `hmp.versionName=7.1.0`，最新 tag 也是 `v7.1.0`。校验逻辑：
```bash
if [ "$VERSION" = "$LATEST_TAG" ]; then
  echo "::error::Version $VERSION already exists!"; exit 1
fi
```
**这是设计意图**（防重复发版），不是缺陷。但要点明：**下一次发版前必须先 bump `gradle.properties`**，否则流水线第一步就停。

**注意一个边界**：`v7.1.0` tag 与 `master` HEAD 是同一个提交。由于 `master` 上此后**没有新提交**，当前若手动触发 `workflow_dispatch`，会**立刻**在 validate 挂掉。

---

### 🟠 P2：`preflight` 任务未被 CI 使用，CI 却自己复刻了一套校验

**证据**：本地新增的 `preflight`（版本 + 测试 + 平台完整性）与 `checkVersion` 两个任务，**release.yml 完全没有调用**。CI 自己用内联 `grep`/`git tag` 实现同样的查重。

**影响**：同一套规则维护两份，容易漂移（例：本地 `checkVersion` 与 CI 的比对口径若不一致，两边结论会打架）。

**处置建议**（二选一）：
- A. CI 改调 `./gradlew checkVersion`，删掉内联脚本 → 单一事实来源
- B. 保留内联（CI 不依赖项目构建，启动更快）→ 本地 `checkVersion` 定位为「CI 前置自检」

**倾向 A**，但要先确认 `checkVersion` 在无 git tag 的浅克隆下行为正确（CI `validate` 已设 `fetch-depth: 0`，可行）。

---

### 🟡 P3：`deploy-site` 与 `release` 强耦合，站点点更新需发版

**证据**：`deploy-site` 的 `needs: [release]`。而 `release` job 带 `if: ${{ github.event.inputs.dry_run != 'true' }}` —— 意味着 **dry_run 模式下站点也不会部署**。

**影响**：改一句官网文案也要走一次完整发版（或接受 dry_run 下站点不更新）。site 内容与 Release 资产本无强依赖（合并 site-sync 后更明显 —— config.js 会从现场同步版本号，站点可独立部署）。

**处置建议**：给 `deploy-site` 加独立触发条件（如 `paths: ['site/**']` 的 push 触发），与 Release 解耦。此项可延后。

---

### 🟡 P4：`deploy-site` 未使用 `if: always()`，Release 失败则站点不部署

当前 `deploy-site` 仅 `needs: [release]`，若 `release` 失败则站点跳过。合并 site-sync 后站点已能自洽（config.js 现场同步、Release 缺失时回退当天日期），可考虑解耦。

---

## 三、正确的部分（确认无需改）

| 项 | 结论 |
|---|---|
| `HMP_BUILD_TARGET` 用法 | 4 个 build job 各自正确设置了目标，与 `settings.gradle.kts` 的 include 逻辑匹配 |
| 产物路径 | 各 job 的 `upload-artifact` path 与 Gradle 输出目录一致（已比对 `packageDistributionForCurrentOS` 的 dmg/msi/deb/appimage 子目录） |
| 版本号单源 | 4 处都从 `gradle.properties` 的 `hmp.versionName` 读取（release job 的 `Read version`、产物命名、tag 名），无硬编码 |
| 并发控制 | `concurrency: group: release / cancel-in-progress: false` —— 发版不互相打断，正确 |
| 权限 | `contents: write`（打 tag）+ `pages: write` + `id-token: write`（Pages 部署），最小够用 |
| `fetch-depth: 0` | `validate`（读 tag）与 `release`（生成 Notes 需完整历史）都设了，正确 |
| dry_run 语义 | 只跳过 `release`（建 Release），前面的构建全跑 —— 可用于验证构建 |

---

## 四、建议处置顺序

| # | 动作 | 解决 | 风险 |
|---|---|---|---|
| 1 | 合并 `origin/feature/site-sync` | P0-2（官网版本） + P2 的版本来源 | 低（领先 3 笔、落后 0，可 fast-forward） |
| 2 | 给 `validate` 补 Android SDK 步骤 | P0-1（CI 可跑通） | 低 |
| 3 | 推一个测试分支实跑 CI 验证 | 确认 P0-1 修复有效 | — |
| 4 | 统一版本校验来源（P2 方案 A） | P2 | 低 |
| 5 | 解耦 `deploy-site`（可选，延后） | P3 / P4 | 中（需设计独立触发） |

> **第 3 步不可省**。P0-1 的判断基于「`testAll` 依赖 Android SDK」的静态推理 + `shared-ui` 的插件配置，**尚未在真实 CI 上验证过**。
