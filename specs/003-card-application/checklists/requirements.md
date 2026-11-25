# Specification Quality Checklist: カードの申込管理

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-17
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- 2026-11-17：回答（Q1: A 6項目、Q2: A 差し戻しなし、Q3: C 履歴＋否決理由必須）と、一覧の前提の変更
  （状態での絞り込みはMust、キーワード検索とページ分けはShould）を反映した。
  [NEEDS CLARIFICATION] は残っていない。全項目合格。
- 要件の番号を振り直した（FR-001〜FR-021）。用語は「登録日時」から「申込日時」にそろえた。
- 2026-11-17：Shouldの定義を変更し、状態の変更時の担当者名（必須・自己申告・本人確認なし）を追加した。
  要件は FR-001〜FR-022 になった（FR-016 担当者名、FR-017 否決の理由、FR-018 変更履歴、FR-019 同時変更）。全項目合格。
- 「データベースに保存する」は、技術に依らない書き方として FR-005「再起動しても消えないように保存する」にした。
- 2026-11-25：plan で見つけた食い違い2つを、ユーザーの判断で直した（どちらも FR-002 を正とした）。
  US4-1 の例の氏名（カナ）を全角スペースに、エッジケースの100歳の境界を「101歳になる日でエラー」に。要件の番号は変わらない。全項目合格のまま。
- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`
