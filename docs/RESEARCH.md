# GitHub 调研与模块组合

检索与 GitHub 官方 API 查询日期：2026-10-14（北京时间）。下表 Star 为当日 GitHub API 查询记录，数量会变化。本次是多轮相关关键词和候选对比，不宣称穷尽 GitHub。

| 项目 | API Star 快照 | 许可证 | 与需求的关系 | 决策 |
|---|---:|---|---|---|
| [Tasks.org](https://github.com/tasks/tasks) | 5655 | GPL-3.0 | 任务/截止/提醒强，缺少教学周课程表 | 阅读任务流程，未复制代码 |
| [Etar](https://github.com/Etar-Group/Etar-Calendar) | 2621 | GPL-3.0 | 通用安卓日历，基于系统日历账户 | 功能对比，不适合作为教学周基础 |
| [拾光课程表](https://github.com/ShiGuangSchedule/shiguangschedule) | 973 | Apache-2.0 | 中国高校教学周、节次、作息、导入导出最接近 | 参考数据划分；移植周起始日期小函数 |
| [Sleepy](https://github.com/lingion/sleepy) | 68 | GPL-3.0 | 原生课表、教务导入、组件 | 备选，不复制代码 |
| [android-week-view](https://github.com/tobiasschuerg/android-week-view) | 60 | MIT | 通用周视图库，时间区间/布局组件 | 裁剪并移植 TimeSpan 时间校验模块 |
| [ulan17/TimeTable](https://github.com/ulan17/TimeTable) | 53 | GPL-3.0 | 课程表与作业，更老的 Android 实现 | 对比；最近推送为 2020 年，不采用 |

额外查看了 [Lectro](https://github.com/Pankaj-Meharchandani/Lectro)（搜索页约 33 Star），功能组合更相似但热度和成熟度有限；以及 [EduFlow](https://github.com/DGtao13/EduFlow)，README 明确尚未选许可证，不能因公开可见就复制代码。两者未下载、未作源码安全审查，也未纳入代码。

## 为什么没有直接整仓合并

没有找到同时满足高热度、任意教学周、自定义节次、课程任务、简单本地保存以及当前许可/安全要求的一个项目。拾光主干已是多平台应用，包含教务 WebView、脚本、同步和多种权限；任务类项目多有账户同步、位置或系统日历集成。整仓相加会引入本次不需要的运行路径和依赖。

因此选择模块组合：

| 需求 | 来源与实现方式 |
|---|---|
| 周次、课程与各教学周关系 | 参考拾光 Course / CourseWeek 划分，新写 Course + Meeting + Set<Integer>，支持同门课多时段 |
| 周一归属计算 | 从拾光 ScheduleDateTimeUtils.kt 小函数移植为 `Models.startOfWeek`，改用 java.time |
| 起止时间/跨午夜校验 | 从 android-week-view TimeSpan.kt 裁剪移植为 `TimeSpan.java`，接入作息校验 |
| 课程表显示 | 根据节次规则实现原生 Android 网格；不引入整个 Compose 周视图库 |
| 截止任务、已完成视图 | 参考 Tasks.org 功能结构，独立实现模型、排序、表单与查询，不复制 GPL 源码 |
| 本地提醒 | 按 Android 官方 AlarmManager / 通知权限文档实现 |
| 备份与恢复 | 新写有版本 JSON + AtomicFile + 系统文件选择器 |

实际复用范围刻意保持小而可审查；这是新的安卓应用，不是拾光或 Tasks.org 的完整 fork，也不宣称已经移植其全部成熟能力。代码没有依赖这些仓库的主干或 JitPack 在线构建。

## 固定源码版本

| 下载源码仓库 | 固定提交 |
|---|---|
| ShiGuangSchedule/shiguangschedule | `feabb68f75c4058319b449287980b5f645eb8d61` |
| tasks/tasks | `61efcde52846803a1dee830498e24ad29b7aa513` |
| tobiasschuerg/android-week-view | `7c2dd7bfcdf99b587b3b5f7647dc84b0587e2597` |

本仓库仅保留实现来源、固定提交及必要许可证，不包含第三方项目完整源码或提交元数据。其余三个项目仅用于功能对比。

## 官方技术依据

- [AGP 8.7 的 Gradle 8.9 / JDK 17 / API 35 兼容表](https://developer.android.com/build/releases/agp-8-7-0-release-notes)
- [AlarmManager 调度说明](https://developer.android.com/develop/background-work/services/alarms)
- [Android 14 精确闹钟默认未授权](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)

具体许可证与变更见根目录 `THIRD_PARTY_NOTICES.md`。
