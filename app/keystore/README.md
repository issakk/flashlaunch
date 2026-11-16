# 签名密钥

`flashlaunch.p12` 是仓库自带的**自签名证书**，用来给 CI 构建出的 APK 签名，
这样每次 Actions 产出的包都能直接安装、并且可以覆盖升级（签名一致）。

```
storeType     PKCS12
storeFile     app/keystore/flashlaunch.p12
storePassword flashlaunch
keyAlias      flashlaunch
keyPassword   flashlaunch
```

SHA-256 指纹：

```
4B:37:D9:54:B4:7D:49:C0:F0:5F:05:46:DE:A4:81:FD:37:AE:4B:BD:CF:4C:FD:D2:F5:85:32:85:1D:51:8F:9E
```

## 注意

- 这是**公开**密钥：任何拿到仓库的人都能签出同签名的包。只适合自用 / 内测分发，
  不要用它发布到应用商店（商店要求私钥保密）。
- 如果私钥泄露需要更换，改名 + 重新生成会导致已安装的旧版本无法覆盖升级
  （必须卸载重装）。所以换 key 前想清楚。
- 签名配置在 `app/build.gradle.kts` 的 `signingConfigs.shared`，debug 与 release 共用，
  但两者 `applicationId` 不同（`com.flashlaunch.debug` / `com.flashlaunch`），可共存。
