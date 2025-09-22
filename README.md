# EnchantAuditor (PurPur/Paper 1.21.1)

Author: **grapelemon**

## 概要
- Lv255 → Lv10
- Lv20〜Lv99 → Lv20
- バックアップあり（変更対象スロットのみ、NBT保持）
- ログは **コンソール** と **専用ログファイル** の2系統
- ログイン時＆定期監査（`scan-interval-seconds`）
- コマンドで再監査、バックアップ一覧、復元、リロード

## ビルド
- JDK 21
- Gradle 8+
```bash
gradle build
```
`build/libs/EnchantAuditor-1.0.0.jar` を `plugins/` へ配置。

## コマンド
- `/enchfix rescan [player|all]`
- `/enchfix list <player>`
- `/enchfix restore <player> <timestamp|latest>`
- `/enchfix reload`

## パーミッション
- `enchfix.admin` (OP既定)

## 設定
`plugins/EnchantAuditor/config.yml` を編集して再起動、または `/enchfix reload`。
"# enchantfix" 
