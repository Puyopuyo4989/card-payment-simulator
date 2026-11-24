"""hook のテストで使う「ほかの Maven のビルド」の偽物。

本物の Maven は動かさない。sleep するだけのプロセスを、Maven のビルドに見えるコマンドライン
（java の起動クラス、または sh で動かした mvnw）と作業ディレクトリで起動する。
"""
import os
import signal
import subprocess
import sys
from pathlib import Path

# mvnw（only-script）→ mvn → java と動いたあとの、java のプロセスの起動クラス
MAVEN_LAUNCHER = "org.codehaus.plexus.classworlds.launcher.Launcher"

SLEEP = "import time; time.sleep(60)"


def _start(testcase, argv, cwd):
    # プロセスのグループごと止められるよう、別のセッションで起動する
    proc = subprocess.Popen(argv, cwd=cwd, start_new_session=True)

    def stop():
        try:
            os.killpg(proc.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        proc.wait()

    testcase.addCleanup(stop)
    return proc


def start_java_build(testcase, cwd):
    """java で動いている Maven（mvnw verify の本体）に見えるプロセスを起動する。"""
    return _start(testcase, [sys.executable, "-c", SLEEP, MAVEN_LAUNCHER, "--batch-mode", "verify"], cwd)


def start_java_run(testcase, cwd):
    """./mvnw spring-boot:run（開発中に動かし続けるアプリ）に見えるプロセスを起動する。"""
    return _start(testcase, [sys.executable, "-c", SLEEP, MAVEN_LAUNCHER, "spring-boot:run"], cwd)


def start_mvnw_build(testcase, cwd):
    """sh で動いている ./mvnw に見えるプロセスを起動する（java を起動する前の段階）。"""
    script = Path(cwd) / "other-build" / "mvnw"
    script.parent.mkdir(parents=True, exist_ok=True)
    # exec にすると sh が sleep に置き換わり、mvnw に見えなくなるので、exec を付けない
    script.write_text("sleep 60\n")
    return _start(testcase, ["sh", str(script), "--batch-mode", "verify"], cwd)


def start_unrelated(testcase, cwd):
    """mvnw という文字を引数に持つだけで、Maven のビルドではないプロセスを起動する（エディタで開いた、など）。"""
    return _start(testcase, [sys.executable, "-c", SLEEP, "mvnw"], cwd)


def under_build(command):
    """command を、Maven のビルドに見えるプロセスの子として動かすコマンドにする（hook を動かした側のビルド）。"""
    code = "import subprocess, sys; sys.exit(subprocess.call(sys.argv[1:-3]))"
    return [sys.executable, "-c", code, *command, MAVEN_LAUNCHER, "--batch-mode", "verify"]
