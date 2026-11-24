"""ほかの Maven のビルドが、このプロジェクトで動いているかを確かめる（Stop と PostToolUse の hook が使う）。

./mvnw verify を2つ同時に動かすと、target/ や frontend/node_modules・frontend/coverage を
取り合って失敗する（docs/ai-review-log.md の #58・#63）。hook は動く前にこれで確かめ、
ほかのビルドが動いていたら、今回の確認を飛ばす。

ほかのビルドとして数えるプロセス（Linux の /proc から読む。/proc がなければ何も数えない）:
  - 作業ディレクトリが、このプロジェクトの中にある
  - コマンドラインが Maven のもの：java の起動クラスが Maven のもの、
    mvnw・mvn そのもの、または sh・bash で動かした mvnw・mvn
  - hook 自身と、hook を動かした側のプロセス（祖先）ではない
"""
import os
from pathlib import Path

PROC = Path("/proc")

# mvnw（only-script）→ mvn → java と動いたあとの起動クラスと、Maven Wrapper（jar 版）の起動クラス
MAVEN_MAIN_CLASSES = (
    "org.codehaus.plexus.classworlds.launcher.Launcher",
    "org.apache.maven.wrapper.MavenWrapperMain",
)
MAVEN_COMMANDS = ("mvnw", "mvn")
SHELLS = ("sh", "bash", "dash", "zsh")

# Claude に伝えるコマンドラインの上限（java のコマンドラインは長いので）
MAX_COMMAND_CHARS = 200


def is_maven(argv: list) -> bool:
    if not argv:
        return False
    if any(arg in MAVEN_MAIN_CLASSES for arg in argv):
        return True
    command = os.path.basename(argv[0])
    if command in MAVEN_COMMANDS:
        return True
    return command in SHELLS and len(argv) >= 2 and os.path.basename(argv[1]) in MAVEN_COMMANDS


def parent_pid(pid: int) -> int:
    # stat の2番目の項目（コマンド名）は空白や ) を含みうるので、最後の ) の後ろから読む
    stat = (PROC / str(pid) / "stat").read_text()
    return int(stat[stat.rindex(")") + 2 :].split()[1])


def ancestors(pid: int) -> set:
    """pid 自身と、その祖先の PID の集合。"""
    result = set()
    while pid > 0 and pid not in result:
        result.add(pid)
        try:
            pid = parent_pid(pid)
        except (OSError, ValueError):
            break
    return result


def describe(argv: list) -> str:
    """Claude に伝える、ビルドのコマンドの短い表し方。java なら起動クラスより後ろだけにする。"""
    for i, arg in enumerate(argv):
        if arg in MAVEN_MAIN_CLASSES:
            argv = ["mvn", *argv[i + 1 :]]
            break
    text = " ".join(argv)
    return text if len(text) <= MAX_COMMAND_CHARS else text[:MAX_COMMAND_CHARS] + "…"


def find_other_maven_builds(project_dir) -> list:
    """このプロジェクトで動いている、ほかの Maven のビルドの一覧: [(PID, コマンド), ...]（PID の順）。"""
    if not PROC.is_dir():
        return []
    project = Path(project_dir).resolve()
    mine = ancestors(os.getpid())
    builds = []
    for entry in PROC.iterdir():
        if not entry.name.isdigit() or int(entry.name) in mine:
            continue
        try:
            argv = (entry / "cmdline").read_bytes().decode(errors="replace").split("\0")[:-1]
            if not is_maven(argv):
                continue
            cwd = Path(os.readlink(entry / "cwd"))
        except OSError:  # 調べている間に終わったプロセスや、読む権限のないプロセス
            continue
        if cwd == project or project in cwd.parents:
            builds.append((int(entry.name), describe(argv)))
    return sorted(builds)


def summary(builds: list) -> str:
    return "、".join(f"PID {pid}（{command}）" for pid, command in builds)
