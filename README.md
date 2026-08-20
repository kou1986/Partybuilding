# 党建评分展示 APP

把 PPT `可改数据版本---新评分党员2026.8.18.pptx` 转换成可在 **海信电视（Android 系统）** 上运行的 APK 应用。

## 功能

- ✅ 完整还原 19 页幻灯片（背景图 + 装饰图 + 文字 + 视频）
- ✅ 所有文字可在电视上用遥控器编辑（弹出软键盘）
- ✅ 两种模式：**编辑模式**（改数据） / **播放模式**（自动翻页）
- ✅ 视频静音循环播放
- ✅ **翻页时间可设置**（默认 8 秒，范围 3–60 秒）
- ✅ 数据本地保存（SharedPreferences），卸载 APP 前一直保留

## 遥控器按键说明

| 按键 | 编辑模式 | 播放模式 |
|---|---|---|
| ← → | 切换上一/下一项文字（编辑焦点） | 上一/下一页 |
| ↑ ↓ | 上下移动文字焦点 | 切换到编辑模式 |
| OK / 确认 | 编辑当前项（弹软键盘） | — |
| 菜单键 | 切换模式 | 切换模式 |
| 频道+ / 频道- | 打开设置页 | 打开设置页 |

---

## 🎮 方式一：在电脑上模拟运行（推荐先试）

**完全不用装到电视**，Android Studio 自带模拟器，能完整模拟海信电视的体验。

### 1.1 安装 Android Studio

> 这一步大约需要 10-20 分钟，下载约 3 GB。

1. 打开浏览器访问 https://developer.android.com/studio
2. 下载 **Android Studio Hedgehog (2023.1.1) 或更新版本**
3. 双击安装包 `android-studio-*.exe` → 一路 Next
   - 安装类型选 **Standard**
   - 提示安装 **Android SDK** 时选 Accept
   - 提示安装 **Android Virtual Device** **一定要勾上**（电视模拟器要用）
4. 安装完成首次启动 Android Studio：
   - 它会自动下载 SDK 组件，等待完成
   - 进入 **More Actions → SDK Manager**，确认以下已勾选：
     - ✅ Android 14.0 (API 34)
     - ✅ Android SDK Build-Tools 34
     - ✅ Android SDK Platform-Tools
     - ✅ Android SDK Command-line Tools (latest)

### 1.2 创建电视模拟器

1. 顶部菜单 **Tools → Device Manager**（或右上角手机图标）
2. 点 **Create device**（+）
3. **选硬件** → 选 **TV** 这一类 → 选 **Android TV (1080p)** → Next
4. **System Image**：
   - 推荐 **API 33 或 34**（Android 13/14）的 **Android TV** 版本
   - 如果旁边显示 "Download"，点一下下载（约 700 MB）
5. **AVD Name**：随便取，比如 "HisenseTV"
6. 点 **Finish**

### 1.3 启动模拟器

1. 在 Device Manager 里点 **▶** 启动刚才创建的电视模拟器
2. 第一次启动会黑屏加载 30 秒-1 分钟，正常
3. 启动后看到 Android TV 的桌面（一般是深蓝色屏，显示时间天气）

### 1.4 跑这个 APP

1. **File → Open** → 选 `E:\Partybuilding\PartyBuildingApp` → 打开
2. Android Studio 自动同步 Gradle，等右下角显示 "Gradle sync finished"
3. 顶部工具栏，**Run/Debug Configurations** 框里选 **app**
4. 旁边的设备下拉框选刚才创建的 **HisenseTV**（如果没显示，等几秒让它识别）
5. 点工具栏绿色的 **▶ Run** 按钮（或者按 **Shift + F10**）
6. 等待 30 秒-1 分钟（首次编译 + 安装 + 启动）
7. **模拟器上自动弹出 APP**，可以看到 19 页幻灯片

### 1.5 模拟遥控器按键

模拟器右侧有一个工具栏，可以模拟遥控器：

| 模拟器按钮 | 对应真实遥控器 | 功能 |
|---|---|---|
| **Power** | 电源键 | 锁屏/亮屏 |
| **Direction Pad ←→↑↓** | 方向键 | 移动焦点 |
| **OK** | 确认键 | 编辑文字 |
| **Menu** | 菜单键 | 切换模式 |
| **Channel + / −** | 频道键 | 打开设置 |
| 数字键盘输入框 | — | 直接键盘打字（编辑模式焦点内） |

> 💡 **小技巧**：在模拟器里可以直接用电脑键盘打字！
> - 把焦点移到要编辑的文字上（方向键选）
> - 按 OK 进入编辑
> - **弹出软键盘后，直接用电脑键盘输入中文/英文**就行（不用电视输入法）
> - 按 Enter 保存

### 1.6 模拟器调试

- **查看日志**：底部 **Logcat** 标签，能看到所有日志
- **改代码后重新运行**：工具栏 **↻** 按钮
- **快速重启 APP**：模拟器菜单 → 关闭 APP → 重新 Run

---

## 方式二：直接装到海信电视

等你用模拟器调好之后，再装到电视上。

### 2.1 生成 APK

**Debug APK**（开发测试用）：
1. 顶部菜单 **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. 等编译完成（首次 3–8 分钟，之后约 30 秒）
3. 弹窗点 **locate**
4. APK 在：`app\build\outputs\apk\debug\app-debug.apk`

**Release APK**（正式发布）：
1. **Build → Generate Signed Bundle / APK**
2. 选 **APK** → 下一步 → **Create new**（首次）：
   - Key store path：`E:\Partybuilding\PartyBuildingApp\keystore.jks`
   - Password：自己设一个（记住！）
   - Alias：partybuilding，Validity 25 年
3. 选 **release** → 勾 **V1 + V2** → Finish
4. APK 在：`app\build\outputs\apk\release\app-release.apk`

### 2.2 安装到电视

**方式 A：U 盘（推荐）**
1. 把 APK 拷到 U 盘
2. U 盘插到电视 USB 口
3. 电视上文件管理器 → 找到 APK → 安装
4. 提示时允许"未知来源"

**方式 B：ADB**
1. 电视：**设置 → 关于本机 → 连按版本号 7 次** → 打开开发者模式
2. 电视：**设置 → 开发者选项 → USB 调试 → 开**
3. 电脑命令行：
   ```bash
   adb connect 192.168.1.100   # 改成电视 IP
   adb install app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.partybuilding.app/.MainActivity
   ```

---

## 使用流程

1. **首次启动**：默认进入 **编辑模式**
2. **编辑文字**：
   - 用 ← → 移动到要改的那一行
   - 按 OK 弹出软键盘和输入框
   - 输入新内容 → 点 "保存"
   - 改完一项按 ← / → 移到下一项继续改
3. **切到播放模式**：
   - 按遥控器 **菜单键**（模拟器按 **Menu** 按钮）
   - 屏幕右上角会显示 "播放模式"
   - 每 N 秒（默认 8）自动切下一页
   - 视频页视频静音循环（不到 8 秒也会强制切走）
4. **调整翻页时间**：
   - 播放模式按 **频道+ / 频道-** 打开设置
   - 拖动滑块改秒数（3–60 秒）
5. **重启 APP** 时改过的数据会自动恢复
6. **想清空所有改动** → 设置页 → "恢复默认数据"

---

## 常见问题

**Q: Gradle 同步失败 "Could not resolve ..."**
A: 检查网络；如果在大陆，打开 **Settings → Appearance & Behavior → System Settings → HTTP Proxy**，勾 "Auto-detect proxy settings" 或手动填代理。

**Q: 模拟器启动后一直黑屏**
A: 第一次启动要下载 system image，耐心等。也可能需要电脑开启 **VT-x / AMD-V**（BIOS 里搜 "Virtualization"）。

**Q: 安装 APK 时电视提示"应用未安装"**
A: 可能是签名冲突，先卸载旧版本。Debug APK 默认用 Android Studio 的调试证书签名；Release APK 自己签名。两种签名不同，必须先卸载另一个。

**Q: 软键盘弹不出来**
A: 海信电视部分 ROM 默认不装谷歌输入法。电视设置 → 输入法 → 装个第三方（如"讯飞电视输入法"）。在模拟器里没有这个问题。

**Q: 视频不播放 / 黑屏**
A: 检查 `app/src/main/assets/media/` 下两个 mp4 是否齐全（media1.mp4 约 4MB + media2.mp4 约 12MB）。

**Q: 字体显示成方块**
A: PPT 用了"微软雅黑"，电视上没这字体。APP 已 fallback 到 Sans Serif。想更接近原版，把字体文件（如 msyh.ttf）放到 `app/src/main/assets/fonts/`，然后 SlideView.kt 改成 `Typeface.createFromAsset`。

**Q: 文字太小看不清**
A: 默认按 1280x720 设计，1080p/4K 电视会按比例放大。如果还不够大，编辑 `SlideView.SLIDE_W` 调小（比如改成 960），文字相对屏幕会更大。

**Q: 模拟器上没法输入中文**
A: 模拟器用电脑键盘输入，所以**你电脑上装了什么输入法，模拟器里就能用什么**。系统默认微软拼音就能输中文。

---

## 工程结构

```
PartyBuildingApp/
├── app/
│   ├── build.gradle.kts            # 模块配置
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/partybuilding/app/
│   │   │   ├── MainActivity.kt     # 主界面 + 模式切换
│   │   │   ├── SlideView.kt        # 自定义 View，渲染 + 编辑
│   │   │   ├── SlideData.kt        # 解析 slides.json
│   │   │   ├── SlideLoader.kt      # 加载图片 / 视频资源
│   │   │   ├── AnimatedGifDrawable.kt
│   │   │   ├── DataStore.kt        # SharedPreferences 封装
│   │   │   ├── SettingsActivity.kt # 设置页
│   │   │   └── SettingsFragment.kt
│   │   ├── res/                    # 布局 / 颜色 / 主题
│   │   └── assets/
│   │       ├── slides.json         # 从 PPT 提取的所有数据
│   │       └── media/              # 25 张图 + 2 个视频
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew, gradlew.bat
└── README.md
```

## 后续维护

- **改数据**：编辑模式直接改，无需重装
- **换背景图**：替换 `assets/media/` 下对应文件，重打包 APK
- **加新幻灯片**：把新 PPT 的 slide XML 跑一遍 `extract_full.py`，合并到 `assets/slides.json`，并把新图片 / 视频放到 `assets/media/`，重打包
- **改代码后打包**：Android Studio → Build → Build APK(s)

---

## 联系 / 反馈

工程由 Claude Code 生成。如有问题，把错误信息贴出来继续调。

