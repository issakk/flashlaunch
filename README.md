# 闪启 · FlashLaunch

极速应用启动器：**T9 九宫格 / 全拼 / 拼音首字母 / 智能子序列** 搜索本机应用，一点即开。
纯本地运行，不联网、不上传。

## 功能

| 功能 | 说明 |
| --- | --- |
| 应用列表 | 读取全部有启动入口的应用（含系统应用） |
| 多模搜索 | 中文直接匹配、全拼 `weixin`、首字母 `wx`、T9 数字 `93494`、子序列 `wxtb` → 微信淘宝 |
| 半屏面板 | 启动即底部面板，上方留空可直接看到桌面；高度 35%~95% 可调，点空白处退出 |
| 九宫格键盘 | 面板内置电话式 T9 键盘（1-9/0/⌫/清空），按键带触感反馈（可关），不用调系统输入法 |
| 隐藏应用 | 长按 → 隐藏；隐藏项从列表消失，可在设置页恢复 |
| 冻结应用 | 长按 → 冻结 / 解冻（Shizuku 或 Root）；冻结的应用点击后自动解冻再打开 |
| 排序 | 空查询按拼音排序，查询时按匹配分数排序 |
| 固定网格 | 空查询和检索中都是同一个 5 列图标网格，不切换成列表 |

## 使用

1. 从桌面图标启动 → 屏幕底部升起面板，上方保持透明（能看到桌面）
2. 键盘直接按 T9 数字：`99` → 微信/微信读书…；`93494` → 微信
3. 点某个图标 → 面板关闭，目标应用全屏打开
4. 按返回键 → 直接退出面板（面板外的区域本来就属于桌面，可以正常操作）
5. 设置 → 「启动面板高度」拖动滑块，35%~95% 立即生效

## 搜索算法

每个应用在加载时预计算 4 个检索串：

```
微信         -> label=微信      pinyinFull=weixin  pinyinInitials=wx  t9Initials=99
Google Maps  -> label=google maps pinyinFull=googlemaps pinyinInitials=gm
```

打分优先级：标签精确 > 标签前缀 > 首字母前缀 > 全拼前缀 > T9 前缀 > 包含 > 子序列。
同级时标签更短者优先。

拼音表来自 [mozillazg/pinyin-data](https://github.com/mozillazg/pinyin-data)，
由 `tools/gen_pinyin.py` 生成 57KB 的 `app/src/main/assets/pinyin.dat`
（U+3400–U+9FFF 全部 26712 个汉字，409 个无声调音节），无第三方拼音依赖。

## 冻结 / 解冻

Android 没有公开 API 让普通应用禁用别的应用，因此通过外部身份执行 `pm`：

```sh
pm disable-user --user 0 <pkg>   # 冻结
pm enable --user 0 <pkg>         # 解冻
```

两条通道，自动择一（设置页可看当前生效的通道）：

1. **Shizuku（推荐，免 Root）** —— 装了 Shizuku 并在其中启动服务后，设置页点「授权」；
   之后以 shell 用户（uid 2000）执行 `pm`。用的是 `dev.rikka.shizuku:api` +
   `ShizukuProvider`（manifest 里 `${applicationId}.shizuku`）。
2. **Root** —— `su -c` 执行同样的命令。`su` 探测结果会缓存，避免每次回前台都弹授权框。

两者都不可用时，长按菜单里的冻结项置灰并提示「需要 Root / Shizuku」。

## 安装

Actions 每次构建产出两个包，**都已签名、都能直接装**：

| 包 | applicationId | 说明 |
| --- | --- | --- |
| `app-debug.apk` | `com.flashlaunch.debug` | 体积略大（含调试工具），可与正式包共存 |
| `app-release.apk` | `com.flashlaunch` | 正式包 |

签名用的是仓库自带的自签名证书 `app/keystore/flashlaunch.p12`（见该目录 README），
所以每次构建的签名一致，可以直接覆盖升级。CI 里有 `apksigner verify` 兜底，
万一签名丢了会直接红。

## 构建

**本仓库不发本地构建**，所有构建都在 GitHub Actions 完成（`.github/workflows/android.yml`）：

- push 到 `main` → 构建 `assembleDebug` + `assembleRelease`
- 产物在 Actions 运行页面的 Artifacts 里下载
- 构建失败时，CI 会把日志尾部作为 **commit comment** 贴回来，方便直接排错

本地如需构建（不推荐，仅供你自己玩）：

```sh
gradle wrapper --gradle-version 8.13   # 仓库里只提交了 wrapper 的 properties
./gradlew :app:assembleDebug
```

## 环境

- Kotlin 2.1.20 · Jetpack Compose (BOM 2025.04.01) · AGP 8.9.1 · Gradle 8.13
- minSdk 26 · targetSdk 35 · JDK 17
- debug 包名 `com.flashlaunch.debug`，可与 release 包共存

## 目录

```
app/src/main/java/com/flashlaunch/
├── MainActivity.kt
├── data/        AppEntry / AppRepository / PinyinDict / Prefs
├── search/      Matcher（打分与 T9）
├── system/      RootShell（su 桥）
└── ui/          HomeScreen / SettingsScreen / AppsViewModel / IconCache / Theme
tools/gen_pinyin.py   生成拼音资产
```

## 后续

- [ ] 收藏栏（置顶常用应用）
- [ ] 快捷设置磁贴 / 桌面小工具 / 通知栏唤起
- [ ] 悬浮窗快捷唤醒
- [x] Shizuku 免 Root 冻结
- [ ] 应用活动（Activity）跳转与收藏
