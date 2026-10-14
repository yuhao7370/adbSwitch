<div align="center">

# 调试开关 · AdbSwitch

<img src="icon.png" width="96" style="border-radius: 18px;" alt="app icon"/>

**用 root / Shizuku 真实开关 Android 调试状态的本地工具**

[![Platform](https://img.shields.io/badge/Platform-Android%208.1%2B-3DDC84?logo=android&logoColor=white)](https://www.android.com)
[![Written in](https://img.shields.io/badge/Written%20in-Java-007396?logo=openjdk&logoColor=white)](https://www.java.com)
[![UI](https://img.shields.io/badge/UI-Material%203-6750A4?logo=materialdesign&logoColor=white)](https://m3.material.io)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue)](LICENSE)

</div>

## 小米 17 Pro Max 背屏版

此 fork 的包名为 `com.yuhao7370.adbswitch`，可与原版并存。自动和手动检查更新均暂时禁用。

针对小米 17 Pro Max 的内置背屏（问就是香港汇丰银行 HSBC 的 App 把背屏识别成了副屏闪退），新增每个快捷应用独立的“临时关闭背屏”选项，默认关闭：

1. 在“快捷打开”添加目标应用，长按应用行，在底部设置面板开启“临时关闭背屏”。开关即时保存；同一面板可以创建桌面快捷方式。
2. 点应用行或对应桌面快捷方式：确认调试关闭，再停用背屏，最后启动目标应用。任一步失败就不启动，并尝试恢复原状态。
3. 用完点通知或首页的“仅恢复背屏”：结束本次目标应用，重新启用并唤醒背屏，不改变调试状态。“恢复打开”调试也会恢复背屏。

实现按显示器稳定标识识别唯一的非主屏内置演示屏，排除主屏、外接屏和虚拟屏。恢复同时执行显示器启用和电源组唤醒。已在小米 17 Pro Max 上验证显示、触摸和再次双击唤醒；其他机型或系统命令不兼容时会取消启动并提示。

---

部分银行类应用（例如香港汇丰银行 HSBC 的 App）会检测手机的 adb 调试状态，而每次手动进开发者选项开关一遍相当繁琐。

adbSwitch 就是为这件事做的：一个**通过 Shizuku / root 真实改写系统调试状态的快捷开关**，支持 USB 调试、无线调试与开发者选项的开关，可从桌面快捷方式或通知磁贴一键操作；关闭调试后会常驻一条通知，点一下即可恢复（Android 16+ 支持「实时动态」，提升为状态栏胶囊）。

项目以 GPL-3.0 开源，代码可自行审查、自行构建。

## 📦 原版下载

以下渠道提供上游原版。背屏版使用此 fork 自行签名构建的安装包。

| 渠道 | 地址 |
| :--- | :--- |
| GitHub Releases | <https://github.com/gaoyilun/adbSwitch/releases> |
| 蒲公英（国内下载） | <https://www.pgyer.com/adbSwitch> |
| 城通云 | <https://url05.ctfile.com/f/20347205-17569904400247-fe06bb?p=1245> |
| 蓝奏云 | <https://elleng.lanzouv.com/iy7Pd4bhqhvc> |

> 城通云 / 蓝奏云为网盘镜像，适合 GitHub 与蒲公英访问不畅时备用；各渠道的 APK 均由同一份源码构建。

## ✨ 功能特性

| 功能 | 说明 |
| :--- | :--- |
| 🔌 **调试主开关** | 一键开关 USB 调试（`adb_enabled`）与无线调试（`adb_wifi_enabled`）：关闭 = 两者全关（adbd 停止，真正可通过检测），打开 = 还原关闭前的组合 |
| 📡 **USB / 无线子开关** | 单独控制每一个键，带状态指示灯；ROM 隐藏这些键时仍能经授权通道读写真实状态 |
| 🛠️ **开发者选项独立开关** | 单独控制开发者选项入口（`development_settings_enabled`），与调试开关互不联动 |
| 📋 **QS 快捷磁贴** | 下拉通知栏加入「调试开关」磁贴，点一下即切换；长按菜单还有 USB / 无线子开关与快捷打开入口 |
| 🔔 **临时关闭 + 常驻通知** | 关闭调试后常驻一条可一键恢复的通知（Android 16+ 支持「实时动态」提升为状态栏胶囊） |
| ⚡ **快捷打开** | 把会检测调试状态的应用加入快捷应用列表，点列表项或桌面快捷方式即「先关调试、再启动目标应用」；重新开启调试时可自动强制停止这些应用（默认开启），避免其在状态翻转瞬间读到调试 |
| 🎨 **Material 3 + 动态取色** | 全面采用 M3 组件与色彩角色，Android 12+ 跟随壁纸取色 |

## 📱 系统要求

| 项目 | 要求 |
| :--- | :--- |
| 系统版本 | Android 8.1（API 27）及以上 |
| 授权方式 | 已 root，或已安装并授权的 [Shizuku](https://shizuku.rikka.app/)（无需 root） |
| 动态取色 | Android 12+ 跟随壁纸；低版本回落静态配色 |

## 🚀 安装与使用

1. 在上方任一渠道下载最新 APK 并安装；
2. 首次打开选择授权模式（root / Shizuku）并完成授权；
3. 下拉通知栏编辑快捷开关，把「调试开关」磁贴拖进快捷区；
4. 想临时过检：关闭调试，用完点常驻通知上的「恢复打开」；
5. 想一键直达：进入「快捷打开」添加目标应用，长按该行可创建桌面快捷方式。

## 🛠️ 从源码构建

```bash
# 本机需 JDK 17+ 与 Android SDK（API 36）
git clone https://github.com/gaoyilun/adbSwitch.git
cd adbSwitch
./build-all.sh        # 自动定位 Gradle；无签名文件时自动生成调试用 keystore
# 产物：adbswitch/app/build/outputs/apk/release/*.apk
```

- 蒲公英更新通道密钥从 `adbswitch/pgyer.properties` 注入（gitignored，模板见 `pgyer.properties.example`）；不配置该文件时蒲公英通道自动禁用，仅保留 GitHub 更新通道。
- 正式发布签名：在 `keystore/signing.properties` 里配置你自己的 `storePassword / keyAlias / keyPassword`。

## 🧩 技术要点

- **读写同源**：部分 ROM 会向第三方应用隐藏调试相关键（ContentResolver 读恒 0），本 App 所有读写都经同一授权通道（su / Shizuku shell），带回读确认，杜绝「写入未生效但 UI 显示成功」；
- **单 shell 合并写入**：读前态 → 写入 → 回读后态合并为一条命令，减少 fork 开销；
- **状态机收口**：所有写入经 `DebugController` 单一入口，同键请求去重合并，写失败绝不污染状态缓存；
- **弱提示更新**：版本判定只比 `versionCode`，启动检查节流 3 天、主线程空闲才跑，发现新版本只 Toast + 关于行提示，不强制弹窗；下载支持 SHA-256 校验。

## ❓ 常见问题

**Q：关掉调试后，开发者选项总开关还在吗？**

A：在。调试开关只改 `adb_enabled` / `adb_wifi_enabled`；开发者选项入口是独立开关，需要时单独控制。

**Q：关掉开发者选项入口后，怎么恢复？**

A：系统设置 → 关于手机 → 连点「版本号」7 次即可重新开启开发者选项。

**Q：没有 root 能用吗？**

A：可以。安装并授权 [Shizuku](https://shizuku.rikka.app/) 后，本 App 以 shell 身份完成同样的改写，无需 root。

**Q：这是伪装关闭吗？**

A：不是。它通过系统设置真实改写 `adb_enabled`，系统会据此重新配置 USB（`sys.usb.config` 去掉 `adb`）、停止 adbd，是真实的状态变化。

**Q：快捷打开里的应用为什么会被强制停止？**

A：重新开启调试前先 `am force-stop` 目标应用，可避免它在调试状态翻转的瞬间读到「已开启」而触发检测。该行为默认开启，可在「快捷打开」设置里关闭。

## 🔒 隐私

本 App 无任何遥测与数据上传；唯一的网络请求是「检查更新」（访问 GitHub Release 元数据与蒲公英通道），且可在不联网的情况下正常使用全部功能。

## 🎨 图标与参考声明

本 fork 的图标参考了 **[@甶丶](https://www.bilibili.com/opus/1242097128919007239) 的 MiMo 娘同人形象**（虽然这东西跟 MiMo 只有小米的关系）。

图标绘制风格参考 [SukiSU Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra) 的简洁 Q 版表情包图标，保留疑惑表情、抵下巴动作、蓝白滑动开关和问号，并增加四周留白；人物仍为上述 MiMo 娘同人形象。

参考作品为 [《MiMo娘立绘 · 01》](https://xn--pssy23gqgbz2d718b.com/works/mimo202609230001.html) 与 [《MiMo娘立绘 · 02》设定图](https://xn--pssy23gqgbz2d718b.com/works/mimo202609230002.html)，由「蓝色大肥鱼」整理收录。整理页标注原作品采用 **CC BY-NC（署名·非商用）**；原作品权利与署名归原作者所有。感谢原作者及整理者提供参考。

该图标为本项目使用的同人衍生图，不代表小米或 MiMo 官方形象。

## 📄 许可证

本项目代码以 [GPL-3.0](LICENSE) 许可证开源；图标参考作品的权利与许可说明见上文。

<p align="center">
  Made with ❤️ on Android by <a href="https://github.com/gaoyilun">@gaoyilun</a>
</p>
