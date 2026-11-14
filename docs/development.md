# 開發

啟動方式與示範帳號在 [README](../README.md#-快速開始)。

## 專案結構

```
考試系統/
├── testa/                    前端（Vue 3）→ 細節見 testa/README.md
├── server/                   後端（Spring Boot 4.1、Java 21）
│   └── src/main/
│       ├── java/tw/testa/
│       │   ├── auth/         登入、Session 裡的身分、AccessGuard（權限檢查）
│       │   ├── staff/        教職員帳號
│       │   ├── unit/         單位、成員與角色
│       │   ├── bank/         題庫
│       │   ├── exam/         考試、名單、登入碼
│       │   ├── attempt/      考生作答、計時、收卷、自動批改
│       │   ├── grading/      閱卷、成績、匯出
│       │   ├── emergency/    系統管理員的緊急檢視（負責人授權）
│       │   ├── audit/        操作紀錄
│       │   ├── config/       安全設定、設定值、第一位管理員、備份清單
│       │   └── common/       錯誤處理（problem+json）、分頁
│       └── resources/db/migration/   Flyway（V1 起，只增不改）
├── deploy/                   Nginx、備份腳本、env 範本、冒煙測試
├── docs/                     文件
├── docker-compose.yml        開發用：只有 PostgreSQL
└── docker-compose.prod.yml   正式部署：Nginx＋後端＋資料庫＋備份
```

## 測試

| 指令 | 內容 |
| --- | --- |
| `cd server && ./mvnw test` | 後端整合測試。用 Testcontainers 開真的 PostgreSQL，需要 Docker |
| `cd testa && npm test` | 前端單元測試（vitest） |
| `cd testa && npm run build` | 型別檢查＋正式建置 |
| `python3 deploy/smoke-test.py http://localhost:8080 帳號 密碼` | 用真的 HTTP 走完一場模擬考。會留下測試資料 |

`.github/workflows/ci.yml` 會在 GitHub 上跑前三項。

## 後端慣例

| | 規則 |
| --- | --- |
| 🔐 | 權限檢查只走 `AccessGuard`（`@PreAuthorize("@access...")` 或 `access.require*`）。系統管理員不會自動通過單位角色檢查 |
| 🗃️ | 資料表變更一律新增 Flyway migration，不改舊檔 |
| 💬 | 錯誤回 problem+json：`detail` 是可以直接顯示給使用者的中文，`code` 給程式判斷 |
| 🙈 | 送到考生端的資料只走 `AttemptDtos`，不能包含答案、解析、評分規準（`AttemptTest` 有檢查） |
| ⏱️ | 計時、收卷、批改只在後端做（`AttemptService`）；前端的倒數只是顯示 |
| 📜 | 不可逆或敏感的動作寫入 `AuditService`；`audit_log` 在資料庫層禁止 update／delete |
| 🚪 | 系統管理員看考試只能走 `EmergencyAccessService`；`*ForEmergency` 方法不檢查角色，不要從別的地方呼叫 |
| 🪪 | Session 裡的身分（`StaffPrincipal`、`CandidatePrincipal`）要能序列化 |

> ⚠️ 整合測試關掉了資料庫 Session（MockMvc 用的是假的 Session）。**改動登入流程後要跑 `deploy/smoke-test.py`**，那是唯一會走到真 Session 的測試。

## 只在開發模式出現的示範功能

AI 匯入題目（假資料）、申論題的「AI 輔助評分」開關、後台「系統設定」分頁都沒接後端。由 `testa/src/lib/flags.ts` 的 `DEMO_FEATURES` 控制：`npm run dev` 看得到，`npm run build` 不顯示。
