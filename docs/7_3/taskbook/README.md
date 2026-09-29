# taskbook · 施工条目存放处

> 结论与判据在 [`../domain-baseline.md`](../domain-baseline.md) 与各域章节 `../domain/D{n}.md`；这里只放**可拆成动作的执行条目**。
> 一个条目一个文件，不要按域合并成一份长文档——合并过一次就会开始漂移。

## 一、命名与内容

- 文件名：`D{域号}-{序号}.md`，与域章节里的 `D{域}-{序号}` 一一对应，例 `D2-01.md`；横切线用 `X-01.md`。
- 每个文件固定四段：**现状 → 动作 → 判据 → 验证方式**。
- 判据必须能落成断言，并写明落在哪个测试源集（`:shared:desktopTest` / `:shared-ui:testAndroidHostTest` / `commonTest`）；落不进测试的写明谁在什么机器上验。
- 做完就删文件，并把结论回填到对应域章节。条目不删 = 台账又开始漂。

## 二、来源纪律

本目录的条目**只来自对代码的实读**。本仓库另有两份带日期的审查报告（`review-7.3-architecture.md`、`review-7.3-code.md`）与 `TODO.md`，它们是历史证据，**不作为本目录条目的来源**：不抄其编号、不沿用其分类。独立重读撞出同一现象属互证，照常编号。

## 三、存放纪律

- 不动 `../../archive/**`（只进不改）。
- 行号是当日工作树坐标，动手前按符号名 grep 复验。
- 计数类数字不作判据，只作量级描述且须写明 grep 口径。

## 四、CI 现在能跑哪些测试（2026-09-29 实测）

结论先行：**`:shared:desktopTest` 与 `:shared-ui:testAndroidHostTest` 现在就能进 CI**，不需要先解决"测试在 runner 上挂死"的问题。

实测依据（全仓 `*.kts` grep `robolectric` 只有一处命中）：

- `android/core-player/build.gradle.kts:57-60` = `junit` / `mockk` / `kotlinx.coroutines.test` / **`robolectric`**
- 其余任何模块的 `build.gradle.kts` 都**没有** `robolectric`

即 Robolectric 只存在于 `:android:core-player`。因此：

- `:shared:desktopTest`、`commonTest`、`:shared-ui:testAndroidHostTest` **不依赖 Robolectric**，可以直接挂 CI。
- 挂死嫌疑（执行期从 Maven Central 现拉 `android-all` 大 jar，默认无超时、无输出）只落在 `:android:core-player`。本机 `~/.m2` 是热的所以复现不出来；给该模块单独加缓存或挂钟超时即可，不必阻塞其余测试。

> 两个操作提醒：
> - `shared-ui` 的测试任务是 **`testAndroidHostTest`**，不存在 `testDebugUnitTest`。
> - 本机跑测试一律用 `./gradlew-lowmem`（1G 堆 + in-process）；裸 `gradlew` 在本机会被 OS 静默杀掉 daemon。

## 五、测试报告的位置

聚合任务自己不出报告，报告落在**被依赖的底层任务**目录：

- 人看：`<模块>/build/reports/tests/<任务名>/index.html`
- 机器读：`<模块>/build/test-results/<任务名>/TEST-*.xml`

任务 UP-TO-DATE 时沿用旧报告，要刷新加 `--rerun`。
