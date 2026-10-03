---
layout: ../../layouts/Doc.astro
lang: zh
page: compatibility
title: '兼容性 – 映湾 CastBay'
description: '映湾已经测试过的电视、手机和 App，以及各自的支持情况。'
heading: '兼容性'
intro: '映湾目前已测试过的设备和 App。在列表之外的设备上用过，或者发现哪里不能用？欢迎<a href="/zh/feedback">告诉我们</a>。'
---

## 电视和车机

| 设备 | Android 版本 | 状态 |
| --- | --- | --- |
| 索尼 BRAVIA XR-55X90L（4K） | 12 | ✅ 已测试：H.265 4K 屏幕镜像及其他全部功能 |
| TCL 电视 | 9 | ✅ 已测试 |
| 小米电视 4（MiTV4，晶晨，32 位） | 6.0.1 | ✅ 已测试：iPhone 屏幕镜像、音乐；解码延迟约 0.3 秒，比新电视高 |
| Google TV Streamer | 14 | ✅ 已安装运行，带硬件 HEVC 解码（接 4K 屏幕时可 4K 镜像） |
| 比亚迪车机 DiLink 5.0（触摸屏） | 12 | ✅ 已测试：屏幕镜像、音乐、视频、方向盘按键切歌和暂停；熄火后会被系统强行停止，启动车辆后需手动打开映湾 |

映湾需要 Android 6.0 及以上的安卓设备。其他品牌的电视、电视盒子、车机和平板应该也能用，遥控器和触摸屏都支持；4K H.265 屏幕镜像需要电视带硬件 HEVC 解码（主界面会显示支持的镜像规格）。

## 手机和电脑

| 设备 | 系统 | 状态 |
| --- | --- | --- |
| iPhone 17 Pro Max | iOS 26 | ✅ 已测试：屏幕镜像、音乐、视频、PIN 码配对 |
| 三星 Galaxy（安卓手机） | Android 16 | ✅ 已测试：网易云音乐 DLNA 投屏 |
| iPad | iPadOS | ❔ 尚未确认，和 iPhone 使用相同的 AirPlay |
| MacBook：屏幕镜像 | macOS | ✅ 已测试：1080p 和 4K 屏幕镜像；延迟见[常见问题](/zh/faq) |
| Mac："音乐"App | macOS | ❌ 不支持：它使用的加密方式，开源 AirPlay 库无法兼容 |

## App

| App | 投屏方式 | 状态 |
| --- | --- | --- |
| Apple Music | AirPlay | ✅ 歌词同步，暂停后恢复无缝接上 |
| 网易云音乐（iPhone） | AirPlay | ✅ 可用；在电视上暂停后，电视会把已收到的两秒左右播完 |
| 网易云音乐（安卓） | DLNA | ⚠️ 可以播放，显示歌名和封面；手机上的播放状态不一定同步 |
| YouTube（iPhone） | AirPlay | ✅ 4K 电视上最高 4K；电视需要能直接访问 YouTube |
| YouTube（安卓） | — | ❌ 只支持 Google Cast，映湾无法接收 |
| 爱奇艺 | AirPlay、屏幕镜像 | ✅ 可用 |
| 哔哩哔哩 | DLNA | ⚠️ 可用，最高 720P（哔哩哔哩对其他设备的限制） |
| QQ 音乐 | DLNA | ❔ 尚未确认 |
| 任意 App | 屏幕镜像 | ✅ 手机屏幕上显示什么就投什么 |

## 不支持的投屏方式

- **Google Cast（Chromecast）**：只有经过 Google 认证的设备才能接收。
- **Miracast 和安卓自带的屏幕镜像**：需要电视系统级的权限，普通 App 拿不到。
