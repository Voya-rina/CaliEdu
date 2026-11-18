"""CaliEdu 的 Python 执行入口。

只做一件事：把一段代码跑起来，把 stdout 和异常原样带回去。
不注入任何模块，也不做沙箱——手机上跑的就是用户自己粘进来的代码。
"""

import io
import sys
import traceback


def run(code):
    buf = io.StringIO()
    old_out, old_err = sys.stdout, sys.stderr
    sys.stdout = buf
    sys.stderr = buf
    namespace = {"__name__": "__main__", "__builtins__": __builtins__}
    ok = True
    try:
        exec(compile(code, "<caliedu>", "exec"), namespace)
    except SystemExit:
        pass
    except BaseException as exc:
        ok = False
        # 把本文件这一帧从 traceback 里摘掉，用户只该看到自己代码的错。
        tb = exc.__traceback__
        if tb is not None and tb.tb_frame.f_code.co_filename.endswith("runner.py"):
            tb = tb.tb_next
        exc.__traceback__ = tb
        buf.write("".join(traceback.format_exception(type(exc), exc, tb)))
    finally:
        sys.stdout, sys.stderr = old_out, old_err
    return (ok, buf.getvalue())
