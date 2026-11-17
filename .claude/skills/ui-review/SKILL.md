---
name: ui-review
description: Review Testa's UI and UX against the owner's taste and a fixed checklist, from real desktop and phone screenshots. Use when asked to review, audit or polish the UI, check how a page looks on phones, or after a UI change before committing.
---

# Testa UI review

Review what people actually see, not the code. Every finding comes from a screenshot.

## Who uses it

School staff (exam officers, teachers, system administrators) and candidates in a computer lab. None are engineers.
Administrators and officers also use phones. The owner judges the result by eye and is particular about alignment.

## The owner's principles (from their feedback)

1. Clean and plain. It must not look AI-made: no decorative icons everywhere, no gradient cards, no badges for the sake of it.
2. Short text. One line of hint at most. No text after an input box ("天", "%" belong in the option label or not at all).
3. Say each thing once per block. The latest backup is not shown in both the status and the list.
4. Grouping: not "cut too fine" (a card per field) and not "all stuck together" (one long card split only by lines).
   Group by what the person is doing; separate groups with spacing (`space-y-4` between cards), titled with `text-[17px] font-semibold`.
5. Controls line up: inputs and selects share one width column (`SettingRow`, `sm:w-48`); no ragged boxes.
6. Pick, don't type: settings with a few sensible values are a select (`ChoiceSelect`) marking the default as "（預設）".
7. One primary button per row; rarely used and destructive actions live in the "..." menu (`MoreMenu`).
8. Lucide icons on buttons and tabs, with text. Icon-only only when the meaning is obvious, and then with `aria-label`.
9. Phones: bottom bar for navigation (at most four cells, the rest under "更多"), no sideways scrolling, tap targets at least 40px.
10. Flat material: a white page, cards outlined by a hairline (`shadow-card`), radius 10px, no floating shadows, no tinted
    greys, no filled header rows in lists (the owner dislikes grey header bars). A list is one card with rows split by lines,
    not a stack of separate cards (grids of cards are fine). The top bar of every full page is a dashed capsule
    (`NavCapsule`); navigation and tabs mark the current place with a dashed capsule (`pill-tab`), not a filled pill or an
    underline.
11. Same kind of UI, same component: `NavCapsule`, `BottomBar`, `PageHeader`, `CardHeader`, `TabBar`, `EmptyNote`, `ErrorNote`, `FormError`, `SettingRow`,
    `ChoiceSelect`, `MoreMenu`, `Badge`, `Button`. Flag any hand-written copy of these (a bare `<h1 class="text-[26px]...">`,
    a red message with its own retry button) and any new one-off style.
12. Lists that need attention first: active items (a live exam) sort to the top. Rows stay white; old ones (an exam ended over
    7 days ago) turn grey. No coloured row tints.

## Steps

1. Make sure the app is running: dev backend on :8080 with the dev profile, and either `npm run dev` (:5173) or a build served on :4173.
2. Take the screenshots into the scratchpad:
   ```bash
   TESTA_APP=http://localhost:5173/ node .claude/skills/ui-review/shots.mjs <scratchpad>/review [filter]
   ```
   `filter` narrows to pages whose shot name contains it (`admin`, `admin-backups`, `exams`).
   Candidate exam pages need seeded data; use `tools/capture-screenshots.mjs` for those.
3. Look at every image, desktop and phone, in order. For each page check:
   - Alignment: left edges, control column, baseline of label and control, table columns.
   - Spacing: consistent padding (`px-5 sm:px-6`, `py-4`), gaps between cards, nothing touching an edge.
   - Grouping: per principle 4.
   - Text: truncated or wrapped labels, hints longer than a line, repeated information, jargon, English leaking into the UI.
   - Phone: horizontal overflow, clipped tabs, buttons wrapping onto their own line, bottom bar overlap, tiny tap targets.
   - States: empty, loading, error and long-list states where visible.
   - Dark mode if asked (toggle in the user menu).
4. Report in Traditional Chinese, most visible problem first, as a short table: page, problem, suggested fix.
   Keep it to real problems; say plainly when a page is fine. Do not change code until the owner picks what to fix.
5. After fixing, rerun the script with a filter for the touched pages and compare.
