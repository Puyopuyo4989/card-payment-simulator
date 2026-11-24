# CLAUDE.md

## 作業のルール

- hook や権限の設定で操作が止められたときは、別の書き方で回避しない。作業を止めて、止められた理由と、やりたかったことを人に報告し、判断を仰ぐ
- ファイルの編集は Edit・Write ツールで行い、Bash（ヒアドキュメント、python、sed など）でファイルを書き換えない
  - ただし、git restore・git checkout でコミット済みの状態に戻すのはよい（戻した先は、すでにチェックを通った状態だから）
  - 理由：hook の判定と、PostToolUse の自動フォーマット・テストが正しく働くようにするため
- PR を出す前には、必ずスキル②（pre-pr-check）を使い、その結果の一覧を PR の説明文に貼る

## ./mvnw verify でテスト用の JVM が止まったとき

`./mvnw verify` が、テストの失敗ではなく、テスト用の JVM が途中で止まったことで失敗することがある（2026-11-24 に1回。原因は未調査）。出るメッセージは、たとえば `The forked VM terminated without properly saying goodbye`、`ExecutionException`、`See dump files (if any exist) [date].dump, [date]-jvmRun[N].dump and [date].dumpstream`。

今は直さない。次に起きたときに、原因を調べるための証拠を残す。

- 失敗したら、**`./mvnw clean` も `./mvnw clean verify` の再実行もする前に**、次を実行する。clean すると `target/` ごと証拠が消える

  ```bash
  d="logs/surefire-crash-$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$d"
  # *.dump* で .dump と .dumpstream の両方を拾う（片方だけのときも、もう片方がないことで失敗しないように）
  cp target/surefire-reports/*.dump* "$d"/ 2>/dev/null \
    || echo "dump ファイルはなかった" > "$d/no-dump.txt"
  free -m > "$d/free-m.txt"
  ```

- 残したら、残した場所（`logs/surefire-crash-…`）と、そのとき同時に動いていたもの（Stop・PostToolUse の hook、手で動かした verify や vitest など）を人に報告する。原因の調査と修正は、人の判断を待ってから行う
- `free -m` は止まった後の空きなので、止まった瞬間の空きとは違うことがある。参考の値として扱う
- `logs/` は `.gitignore` で git の管理外にしてある。中身はコミットしない
