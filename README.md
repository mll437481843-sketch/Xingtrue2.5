# 云音乐管家 · 网易云音乐歌单与单曲下载管理工具（Android）

版本 1.2 · 包名 `com.ncmd` · minSdk 26 / targetSdk 34 · 单 APK，无第三方运行时依赖

## 一、安装

1. 把 `build/app-release.apk` 传到手机，点击安装（允许「安装未知来源应用」）。
2. 首次打开会请求通知与音频读取权限；**要写入 `/sdcard/Download` 等公共目录，需要在首页卡片点「去授权」开启「所有文件访问」**（Android 11+）。
3. 安装包使用本地自签名证书（`ncmd.keystore`，口令 `android`），非商店分发。

## 二、功能

| 模块 | 能力 |
| :-- | :-- |
| 歌单批量下载 | 链接/分享文本/纯 ID/专辑链接识别；解析歌曲名·歌手·专辑·时长·封面·序号；搜索、按歌手筛选、按下载状态筛选、全选/反选/仅未下载、重复检测、可下载性核验、完整性统计 |
| 单曲下载 | 每行一个链接，最多 100 条；自动提取 ID、清理分享参数；逐首显示可用音质与不可下载原因 |
| 任务中心 | 队列、并发上限、暂停/恢复（单任务与全队列）、取消、删除、失败重试（指数退避）、清空已完成、实时进度与速度 |
| 文件管理 | 命名模板 5 种、自动分类（歌手/专辑/歌单/日期）、同名冲突处理、按平台 ID + 标题歌手 + 文件哈希三级查重 |
| 元数据 | MP3 写入 ID3v2.3（标题/艺术家/专辑/曲目/年份/封面 APIC/歌词 USLT），写前备份，失败自动回滚 |
| 歌词封面 | LRC/TXT 保存、封面 JPG/PNG 保存与内嵌 |
| 本地音乐库 | 目录扫描、读取元数据、缺封面/缺元数据筛查、同名重复检测、点按用系统播放器打开 |
| 历史与设置 | 下载历史检索、数据库备份、日志查看、深浅色主题、界面缩放、运行自检与网络测试 |

## 三、数据来源与合规边界（重要）

- 歌曲信息、歌单、封面、歌词：网易云音乐**公开 Web 接口**（music.163.com/api/...），无需登录。
- 音频：**只使用平台自身返回的播放源**（/api/song/enhance/player/url）。接口对某首歌不返回可用地址时，应用会把该曲目标记为「无法获取」并给出原因（版权受限 / 会员付费 / 无资源），**不用试听片段冒充完整歌曲，不做解密，不绕过权限**。
- 实测结论：匿名状态下**部分**歌曲可获取完整音频（已验证一首 5.0MB / 326s 的完整曲目，与平台返回的文件大小、歌曲时长完全一致）；热门/会员曲目普遍返回 -110 版权受限或 404。这是平台的授权限制，不是程序缺陷。
- 请仅在合法授权范围内使用，下载内容不得二次传播或商用。

## 四、目录结构

```
netmusic/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── assets/ui/            # 界面（HTML/CSS/JS，WebView 承载）
│   ├── java/com/ncmd/        # 业务层（Kotlin）
│   │   ├── MainActivity.kt   入口、权限、WebView 容器
│   │   ├── Bridge.kt         JS 与原生 桥（唯一出入口）
│   │   ├── NeteaseApi.kt     接口客户端 + 链接解析
│   │   ├── Downloader.kt     单任务下载（Range 续传、重试分类）
│   │   ├── TaskManager.kt    队列调度、并发、状态机、持久化
│   │   ├── Db.kt             SQLite（6 张表 + 版本迁移骨架）
│   │   ├── FileOps.kt        命名/分类/查重/歌词/封面
│   │   ├── Tagger.kt         ID3v2.3 写入器
│   │   ├── Library.kt        本地音乐库扫描
│   │   ├── Settings.kt       偏好存储
│   │   ├── Util.kt           HTTP/日志/工具
│   │   └── FileProviderEx.kt 文件对外共享
│   └── res/
├── test/                     # 可离线复跑的测试（JVM + Node）
├── docs/                     # 技术可行性报告 / 测试报告 / 功能清单
├── build.sh                  # 一键构建（JDK + kotlinc + aapt2 + R8 + apksig）
├── ziptool.mjs               # APK 内 dex 追加工具
└── build/app-release.apk     # 产物
```

## 五、重新构建

```sh
cd netmusic && sh build.sh
# 产物 build/app-release.apk
```

修改版本号：编辑 `version.txt`（第一列 versionCode，第二列 versionName）。

## 六、运行自检

应用内「关于 → 运行自检」会检查：原生桥接、网络连通性与延迟、保存目录、目录读取、文件权限、任务队列、数据库记录数。遇到问题时先跑这个，再到「设置 → 查看运行日志」。

## 七、测试

```sh
cd netmusic/test
java -cp "out:json.jar:<kotlin-stdlib>:../build/classes" TestApi   # 后端逻辑 + 真实接口
UIDIR=../app/src/main/assets/ui node render.mjs                    # 界面渲染 16 个场景
node dltest.mjs                                                    # 真实音频完整性
```

详见 docs/测试报告.md。

## 八、v1.3 接口升级（重要变更）

**接口从旧端点换成 Web 播放器同款端点**，可用率与音质都变了：

| | 旧（v1.2） | 新（v1.3） |
| :-- | :-- | :-- |
| 端点 | /api/song/enhance/player/url?br=128000 | /api/song/enhance/player/url/v1?level=exhigh&encodeType=mp3 |
| 档位 | 固定码率参数 | level: standard / higher / exhigh / lossless + 自动降级 |
| 实测「起风了」 | 128 kbps · 5.0 MB | **320 kbps · 12.4 MB（exhigh）** |

**同时修复了一个真实缺陷**：会员曲目在新旧接口下都会返回 `freeTrialInfo`（试听片段，约 0.5 MB）。旧版本会把这种片段当成完整歌曲保存。v1.3 起：

- 解析阶段：`trial` 判定为「仅提供试听片段」，标为不可下载
- 下载阶段：拿到 `trial` 直接置为「无法获取」并说明原因
- 兜底：下载完成后按「文件大小 ÷ 码率」估算时长，短于歌曲时长 85% 直接丢弃，绝不落盘

**新增可选项：账号 Cookie**（设置 → 高级）。

填入你自己登录后的 `MUSIC_U=...` 等 Cookie 后，平台会按**你账号自身的权限**返回音源与音质（你已开通的会员曲目才能拿到完整音频）。不填则按匿名权限获取。Cookie 只存在本机偏好里，不上传、不打印到日志（日志写入前会过滤 cookie/token 字段）。

参考项目：GitHub `Binaryify/NeteaseCloudMusicApi`（★30224）的接口封装思路——本项目只采用其中**公开 Web 端点**的用法，未使用其登录代理与加密参数逻辑。
