#!/usr/bin/env python3
"""批量翻译站点语言清单：site/i18n/zh.json → site/i18n/{lang}.json。

- 端点：MyMemory 免费翻译 API（配额：带邮箱 50k 字符/天）
- 增量缓存：目标文件里已有的 key 直接跳过，可随时重跑断点续传
- 配额耗尽：保存已完成的进度后优雅退出，次日重跑自动补齐
- 失败回退：单条重试 3 次仍失败则跳过（运行时回退显示中文原文）
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

EMAIL = 'hmp-site@example.com'   # MyMemory 用邮箱把匿名 5k/天 提到 50k/天
WORKERS = 5                      # 并发请求数（MyMemory 可容忍小幅并发）
# 优先级：站点访客主力语言优先
LANGS = ['en', 'ja', 'ko', 'de', 'es', 'fr', 'pt', 'ru', 'vi', 'th', 'id', 'hi', 'ar']


class QuotaExceeded(Exception):
    pass


def mm_translate(text, lang, retries=3):
    pair = f'zh-CN|{lang}'
    q = urllib.parse.quote(text)
    url = (f'https://api.mymemory.translated.net/get?q={q}&langpair={pair}&de={EMAIL}')
    for i in range(retries):
        try:
            with urllib.request.urlopen(url, timeout=15) as r:
                d = json.loads(r.read().decode())
            if d.get('responseStatus') == 403 or d.get('quotaFinished'):
                raise QuotaExceeded()
            t = (d.get('responseData') or {}).get('translatedText') or ''
            if t and 'MYMEMORY WARNING' not in t:
                # MyMemory 有时把 HTML 实体带回来
                return t.replace('&quot;', '"').replace('&#39;', "'").replace('&amp;', '&')
        except QuotaExceeded:
            raise
        except Exception:
            time.sleep(1.5 * (i + 1))
    return None


def write_shim(lang, done):
    """生成 i18n/{lang}.js（window.SITE_I18N 注入版）。
    运行时用 <script> 加载而非 fetch——file:// 协议下 fetch 被禁，切换语言会无效。"""
    if not done:
        return
    with open(f'{lang}.js', 'w', encoding='utf-8') as f:
        f.write(f'/* generated from {lang}.json — do not edit; rerun translate.py */\n')
        f.write('window.SITE_I18N=window.SITE_I18N||{};\n')
        f.write(f'window.SITE_I18N.{lang}=' + json.dumps(done, ensure_ascii=False) + ';\n')


def main():
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    src = json.load(open('zh.json', encoding='utf-8'))
    langs = sys.argv[1].split(',') if len(sys.argv) > 1 else LANGS
    done_total = 0
    for lang in langs:
        out_path = f'{lang}.json'
        done = json.load(open(out_path, encoding='utf-8')) if os.path.exists(out_path) else {}
        todo = [k for k in src if k not in done]
        if not todo:
            write_shim(lang, done)
            print(f'[{lang}] complete ({len(done)} keys), skip', flush=True)
            continue
        ok, fail, quota_hit = 0, 0, False
        with ThreadPoolExecutor(max_workers=WORKERS) as ex:
            futures = {ex.submit(mm_translate, src[k], lang): k for k in todo}
            for i, fu in enumerate(futures):
                k = futures[fu]
                try:
                    t = fu.result()
                except QuotaExceeded:
                    quota_hit = True
                    fu.cancel()
                    continue
                if t is None:
                    fail += 1
                    print(f'  [{lang}] FAIL: {src[k][:40]}', flush=True)
                else:
                    done[k] = t
                    ok += 1
                if (i + 1) % 20 == 0:
                    json.dump(done, open(out_path, 'w', encoding='utf-8'),
                              ensure_ascii=False, indent=1, sort_keys=True)
                if quota_hit:
                    break
        json.dump(done, open(out_path, 'w', encoding='utf-8'),
                  ensure_ascii=False, indent=1, sort_keys=True)
        write_shim(lang, done)
        done_total += ok
        print(f'[{lang}] {ok} translated, {fail} failed, total {len(done)}/{len(src)}', flush=True)
        if quota_hit:
            print(f'QUOTA EXCEEDED — rerun tomorrow to continue ({lang} at {len(done)}/{len(src)})')
            return
    print(f'DONE, {done_total} new translations')


if __name__ == '__main__':
    main()
