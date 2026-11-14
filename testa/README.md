# Testa 前端

Vue 3（Composition API、`<script setup>`）、TypeScript、Vite、Tailwind CSS v4、reka-ui、Lucide、KaTeX、vue-draggable-plus、vue-sonner。

```bash
npm install
npm run dev        # :5173，/api 轉給 localhost:8080 的後端
npm test           # 單元測試（vitest）
npm run build      # 型別檢查＋正式建置
```

後端怎麼啟動、示範帳號是什麼，看[根目錄的 README](../README.md#-快速開始)。

## 頁面

路由是 hash 模式，網址長得像 `http://localhost:5173/#/exams`。

| | 路徑 | 誰看得到 | 內容 |
| --- | --- | --- | --- |
| 🔑 | `/login` | 所有人 | 考生：准考證號＋登入碼；教職員：Email＋密碼 |
| 🔒 | `/password` | 教職員 | 換密碼（臨時密碼登入後會被帶到這裡） |
| 🏠 | `/` | 教職員 | 首頁：依角色列出能做的事、進行中的考試 |
| 📚 | `/banks` | 出題、負責人 | 題庫列表 |
| ✏️ | `/banks/:id` | 出題 | 題庫編輯器（專注模式）：編輯／模擬作答 |
| 🗓️ | `/exams` | 負責人、監考 | 考試列表：建立、發布、結束 |
| 📋 | `/exams/:id` | 負責人、監考 | 單場考試：考生名單、登入單、監考、評分者與成績、緊急檢視授權 |
| 📝 | `/grading` | 評分 | 閱卷：一次一份、匿名 |
| 👥 | `/members` | 負責人 | 單位成員與角色 |
| ⚙️ | `/admin` | 系統管理員 | 帳號、單位、操作紀錄、備份、緊急檢視 |
| 🎓 | `/student` | 考生 | 我的考試、成績 |
| 💻 | `/student/exam/:id` | 考生 | 作答（專注模式）：核對身分 → 計時作答 → 交卷 |

導覽列在桌面是頂部分頁，手機是底部分頁。前端的守衛只負責導向；真正的權限檢查在後端。

## 結構

```
src/
  router.ts               路由與登入、權限守衛
  App.vue                 全域：RouterView、確認視窗、Toast
  layouts/AppLayout.vue   導覽列＋內容區＋手機底部分頁
  pages/                  每個路由一個檔案
  composables/
    useAuth.ts            目前登入的人、單位與角色
    useBanks.ts           題庫：載入、自動儲存、版本衝突
    useExams.ts           考試、名單
    useGrading.ts         閱卷
    useCandidate.ts       考生端：試卷、作答、計時顯示
    useConfirm.ts         確認視窗（Promise 介面）
    useQuestionActions.ts 帶確認與 toast 的題目動作
    useTheme.ts           深淺色
    useWorkspace.ts       示範資料（只給 DEMO_FEATURES 用）
  components/
    ui/                   Button、Switch、Tip、Menu、SidePanel、ConfirmDialog…
    app/                  Logo、UserMenu、UnitMembers、ExamScoring
    editor/               題庫編輯器與作答共用的題卡
  lib/
    api.ts                fetch 包裝：CSRF、problem+json 錯誤
    grading.ts            自動批改（模擬作答用；正式批改在後端）
    question.ts           題目檢查
    math.ts               公式轉 HTML（KaTeX）
    flags.ts              DEMO_FEATURES
    __tests__/            單元測試
  data/                   示範題庫、模擬的 AI 辨識結果
```

## 慣例

- **題卡共用**：編輯時看到的題目就是考生作答時的樣子。考生作答與教師的「模擬作答」共用 `ExamPreview`（`real` 屬性切換）。
- **確認與回饋**：無法輕易回復的動作（刪除、交卷、結束考試、停用帳號…）都透過 `useConfirm()` 跳確認視窗；完成後用 toast 回報。
- **示範功能**：AI 匯入、AI 輔助評分、後台系統設定沒接後端，用 `DEMO_FEATURES` 包起來，正式建置不顯示。
- **元件**：`components/ui` 依 shadcn-vue 的寫法（reka-ui + cva + `cn()`）手寫並套用本專案的 token，沒有透過 CLI 產生。

## Design tokens

定義在 `src/style.css`，深淺色各一組。

| | Token | 用途 |
| --- | --- | --- |
| ⬜ | `desk` / `paper` / `sunken` | 畫布、卡片、輸入區與 hover |
| ⬛ | `ink` / `ink-2` / `ink-3` | 文字三階 |
| 🟩 | `brand` / `brand-strong` / `brand-soft` | 黑板綠：品牌、主要動作、正確答案 |
| 🟥 | `grade` / `grade-soft` | 紅：只用在錯誤、警示、刪除 |
| 🟨 | `marker` / `marker-ink` | 黃：待處理（搭配用 `bg-marker text-marker-ink`，沒有 `marker-soft`） |

全站使用同一組黑體；題目內容套用 `.exam-text`，編輯與作答共用。
