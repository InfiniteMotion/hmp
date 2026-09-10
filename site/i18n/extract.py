#!/usr/bin/env python3
"""从 site/*.html 提取中文文案 → site/i18n/zh.json（站点 i18n 的源语言清单）。

策略：只收集「纯文本节点」（无元素子节点）中含 CJK 字符的字符串，
按内容的 SHA1 前 10 位作 key——运行时 main.js 以同样的 key 匹配替换，
因此无需给 HTML 加任何标记。含内联标签的段落会被拆成多个片段分别翻译。
重新运行即可在文案改动后刷新清单（翻译脚本会增量补齐新 key）。
"""
import glob
import hashlib
import html
import json
import os
import re
import sys
from html.parser import HTMLParser

CJK = re.compile(r'[\u4e00-\u9fff]')
SKIP_TAGS = {'script', 'style', 'code', 'pre'}


class TextCollector(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack = []
        self.texts = []

    def handle_starttag(self, tag, attrs):
        if tag not in ('br', 'img', 'meta', 'link', 'input', 'hr', 'source'):
            self.stack.append(tag)

    def handle_endtag(self, tag):
        if self.stack and self.stack[-1] == tag:
            self.stack.pop()

    def handle_data(self, data):
        if SKIP_TAGS.intersection(self.stack):
            return
        t = data.strip()
        if t and CJK.search(t):
            self.texts.append(t)


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(root)
    all_texts = {}
    for f in sorted(glob.glob('*.html')):
        p = HTMLCollector_file(f)
        for t in p:
            key = hashlib.sha1(t.encode()).hexdigest()[:10]
            all_texts.setdefault(key, t)

    out = 'i18n/zh.json'
    os.makedirs('i18n', exist_ok=True)
    with open(out, 'w', encoding='utf-8') as fh:
        json.dump(all_texts, fh, ensure_ascii=False, indent=1, sort_keys=True)
    print(f'{len(all_texts)} unique strings -> {out}')


def HTMLCollector_file(path):
    c = TextCollector()
    c.feed(open(path, encoding='utf-8').read())
    return c.texts


if __name__ == '__main__':
    sys.exit(main())
