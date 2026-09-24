#!/usr/bin/env python3
"""按 zh.json 收敛各语言词典：删除已失效的 key（zh 里不再存在），
并重写 i18n/{lang}.js 注入版。不新增翻译——缺失的 key 由 translate.py 增量补齐。

用法：python prune.py            # 处理全部已有词典
"""
import json
import os
import sys

LANGS = ['en', 'ja', 'ko', 'de', 'es', 'fr', 'pt']


def write_shim(lang, done):
    with open(f'{lang}.js', 'w', encoding='utf-8') as f:
        f.write(f'/* generated from {lang}.json — do not edit; rerun translate.py */\n')
        f.write('window.SITE_I18N=window.SITE_I18N||{};\n')
        f.write(f'window.SITE_I18N.{lang}=' + json.dumps(done, ensure_ascii=False) + ';\n')


def main():
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    zh = json.load(open('zh.json', encoding='utf-8'))
    langs = sys.argv[1].split(',') if len(sys.argv) > 1 else LANGS
    for lang in langs:
        path = f'{lang}.json'
        if not os.path.exists(path):
            print(f'[{lang}] no dictionary, skip')
            continue
        d = json.load(open(path, encoding='utf-8'))
        kept = {k: v for k, v in d.items() if k in zh}
        removed = len(d) - len(kept)
        json.dump(kept, open(path, 'w', encoding='utf-8'),
                  ensure_ascii=False, indent=1, sort_keys=True)
        write_shim(lang, kept)
        print(f'[{lang}] pruned {removed}, kept {len(kept)}/{len(zh)}')


if __name__ == '__main__':
    main()
