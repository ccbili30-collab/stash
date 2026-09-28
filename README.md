# Stash

安卓私藏收集箱：从任意 app **分享**图片和链接进来，或用**快捷磁贴一键临时截图**（不进相册）。纯本地存储，数据不上传；更新通过 GitHub Releases 分发。

## 功能

- **分享收集**：系统分享菜单里选 Stash，图片（支持多张）复制进私有目录，文字自动提取第一个网址
- **临时截图**：无障碍授权一次后，下拉通知栏点「Stash 临时截图」磁贴，当前屏幕直接进库，相册无感知
- **自动收截屏**（可选开关）：监测系统截屏目录，新截屏自动复制进库
- **链接抓标题**：入库时后台抓取 og:title，列表一眼可认
- **整理**：标签体系、全文搜索、备注、瀑布流浏览（全部 / 图片 / 链接筛选）
- **纯本地**：Room 数据库 + app 私有目录，唯一网络行为是抓链接标题和检查更新

## 更新

设置 → 检查更新，自动从 [Releases](../../releases) 下载 APK 安装（首次需允许"来自此来源安装"）。

## 构建

```bash
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

要求 JDK 17、Android SDK 36。签名用仓库内固定 keystore（自用 app），任何机器构建出的 APK 签名一致，可直接覆盖安装。

## 技术说明

- Kotlin + Jetpack Compose（Material 3 Expressive + Material You 动态取色，Ocean 种子色预设）
- Room + DataStore；图标为 Lucide stroke（MIT），命名沿用 HugeIcons 体系
- 临时截图走 `AccessibilityService.takeScreenshot`，服务声明 `canRetrieveWindowContent=false`——只截屏，不读屏幕内容
- `design/gen_icons.py`：Lucide SVG → VectorDrawable 生成脚本

## 权限清单

| 权限 | 用途 | 时机 |
|---|---|---|
| INTERNET | 抓链接标题 / 检查更新 | 使用时 |
| VIBRATE | 磁贴截图成功反馈 | 使用时 |
| READ_MEDIA_IMAGES | 自动收截屏（只读截屏目录） | 仅开启该开关 |
| REQUEST_INSTALL_PACKAGES | 应用内更新安装 APK | 仅点更新时 |
