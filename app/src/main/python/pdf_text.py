# PDF -> 纯文本。只做一件事：把文字层抽出来。
# 扫描件没有文字层，抽不出来就是抽不出来，不猜、不编。

import re

_PAGE_NO = re.compile(r"^\s*[-—]?\s*\d{1,4}\s*[-—]?\s*$")


def _clean(text):
    lines = []
    for raw in text.replace("\r", "\n").split("\n"):
        line = " ".join(raw.split())
        if not line:
            continue
        # 页眉页脚最常见的形态就是一串孤零零的数字，去掉能省下一堆噪音
        if _PAGE_NO.match(line):
            continue
        lines.append(line)
    return "\n".join(lines)


def extract(path, start=1, count=10):
    """抽第 start 页起的 count 页。教材整本几千页，全抽既慢又没用。"""
    try:
        from pypdf import PdfReader
    except Exception as exc:
        return [False, "安装包里没有带 pypdf：%s" % exc]

    try:
        reader = PdfReader(path)
    except Exception as exc:
        return [False, "打不开这个 PDF：%s" % exc]

    try:
        if getattr(reader, "is_encrypted", False):
            try:
                reader.decrypt("")
            except Exception:
                return [False, "这个 PDF 加了密，读不了。"]
        total = len(reader.pages)
    except Exception:
        total = 0
    if total == 0:
        return [False, "这个 PDF 一页都没有。"]

    first = max(1, int(start))
    if first > total:
        return [False, "这个 PDF 只有 %d 页，起始页填超了。" % total]

    last = min(total, first + max(1, int(count)) - 1)
    chunks = []
    for index in range(first - 1, last):
        try:
            chunks.append(reader.pages[index].extract_text() or "")
        except Exception:
            chunks.append("")

    text = _clean("\n".join(chunks))
    if len(text) < 20:
        return [False, "第 %d-%d 页没抽到文字，多半是扫描件或纯图。换个起始页试试，或者自己复制。" % (first, last)]
    return [True, text]
