#!/usr/bin/env python3
"""对 agent 体系核心术语做人工校订，覆盖机器翻译的欠佳译法。

这些词是「音乐伙伴」体系的主干词汇（唯一大脑 / 三个执行域 / 共用基建…），
机器翻译常给出不一致或生硬的译法，这里统一成各语言的固定术语。
以 key（= 中文原文的 SHA1 前 10 位）定位，改写各 {lang}.json 并刷新 .js 注入版。
可重复运行（幂等）。
"""
import hashlib
import json
import os

# 术语表：中文原文 -> {lang: 译法}
TERMS = {
    '唯一大脑': {
        'en': 'The only brain', 'ja': '唯一の頭脳', 'ko': '유일한 두뇌',
        'de': 'Das einzige Gehirn', 'es': 'El único cerebro',
        'fr': 'Le seul cerveau', 'pt': 'O único cérebro',
    },
    'MasterAgent · 唯一大脑': {
        'en': 'MasterAgent · the only brain', 'ja': 'MasterAgent · 唯一の頭脳',
        'ko': 'MasterAgent · 유일한 두뇌', 'de': 'MasterAgent · das einzige Gehirn',
        'es': 'MasterAgent · el único cerebro', 'fr': 'MasterAgent · le seul cerveau',
        'pt': 'MasterAgent · o único cérebro',
    },
    'MasterAgent —— 唯一大脑': {
        'en': 'MasterAgent — the only brain', 'ja': 'MasterAgent — 唯一の頭脳',
        'ko': 'MasterAgent — 유일한 두뇌', 'de': 'MasterAgent — das einzige Gehirn',
        'es': 'MasterAgent — el único cerebro', 'fr': 'MasterAgent — le seul cerveau',
        'pt': 'MasterAgent — o único cérebro',
    },
    '永不暂停': {
        'en': 'Never pauses', 'ja': '決して止まらない', 'ko': '절대 멈추지 않음',
        'de': 'Pausiert nie', 'es': 'Nunca se pausa',
        'fr': 'Ne s\u2019arrête jamais', 'pt': 'Nunca pausa',
    },
    '三个执行域': {
        'en': 'Three execution domains', 'ja': '3つの実行ドメイン',
        'ko': '세 개의 실행 도메인', 'de': 'Drei Ausführungsdomänen',
        'es': 'Tres dominios de ejecución', 'fr': 'Trois domaines d\u2019exécution',
        'pt': 'Três domínios de execução',
    },
    '三个独立域': {
        'en': 'Three independent domains', 'ja': '3つの独立したドメイン',
        'ko': '세 개의 독립 도메인', 'de': 'Drei unabhängige Domänen',
        'es': 'Tres dominios independientes', 'fr': 'Trois domaines indépendants',
        'pt': 'Três domínios independentes',
    },
    '原子工具': {
        'pt': 'Ferramentas atômicas',
    },
    '音乐伙伴': {
        'en': 'Music Companion', 'ja': '音楽の相棒', 'ko': '음악 파트너',
        'de': 'Musikbegleiter', 'es': 'Compañero musical',
        'fr': 'Compagnon musical', 'pt': 'Companheiro musical',
    },
    '本地词表': {
        'en': 'local lexicon', 'ja': 'ローカル語彙', 'ko': '로컬 어휘',
        'de': 'lokales Lexikon', 'es': 'léxico local',
        'fr': 'lexique local', 'pt': 'léxico local',
    },
    '步数硬熔断': {
        'en': 'hard step cutoff', 'ja': 'ステップ数ハード遮断', 'ko': '스텝 수 하드 컷오프',
        'de': 'harte Schrittbegrenzung', 'es': 'corte estricto de pasos',
        'fr': 'coupure stricte des étapes', 'pt': 'corte rígido de etapas',
    },
    '许可门': {
        'en': 'permission gate', 'ja': '許可ゲート', 'ko': '권한 게이트',
        'de': 'Berechtigungs-Gate', 'es': 'puerta de permisos',
        'fr': 'porte de permissions', 'pt': 'portão de permissões',
    },
    '共用基建': {
        'en': 'Shared infrastructure', 'ja': '共有インフラ', 'ko': '공용 인프라',
        'de': 'Gemeinsame Infrastruktur', 'es': 'Infraestructura compartida',
        'fr': 'Infrastructure partagée', 'pt': 'Infraestrutura compartilhada',
    },
    '门面问候': {
        'pt': 'Saudação',
    },
    '上下文预算': {
        'en': 'context budget', 'ja': 'コンテキスト予算', 'ko': '컨텍스트 예산',
        'de': 'Kontextbudget', 'es': 'presupuesto de contexto',
        'fr': 'budget de contexte', 'pt': 'orçamento de contexto',
    },
    '用量记账': {
        'en': 'usage ledger', 'ja': '使用量記録', 'ko': '사용량 원장',
        'de': 'Nutzungs-Ledger', 'es': 'registro de uso',
        'fr': 'registre d\u2019usage', 'pt': 'registro de uso',
    },
    '工具目录': {
        'en': 'tool catalog', 'ja': 'ツールカタログ', 'ko': '도구 카탈로그',
        'de': 'Werkzeugkatalog', 'es': 'catálogo de herramientas',
        'fr': 'catalogue d\u2019outils', 'pt': 'catálogo de ferramentas',
    },
    '记忆子系统': {
        'en': 'memory subsystem', 'ja': '記憶サブシステム', 'ko': '기억 서브시스템',
        'de': 'Speicher-Subsystem', 'es': 'subsistema de memoria',
        'fr': 'sous-système mémoire', 'pt': 'subsistema de memória',
    },
    '调度器': {
        'en': 'scheduler', 'ja': 'スケジューラ', 'ko': '스케줄러',
        'de': 'Scheduler', 'es': 'planificador', 'fr': 'planificateur', 'pt': 'escalonador',
    },
    '五维标签': {
        'en': 'five-dimension labels', 'ja': '5次元タグ', 'ko': '5차원 태그',
        'de': 'fünf-dimensionale Tags', 'es': 'etiquetas de cinco dimensiones',
        'fr': 'étiquettes à cinq dimensions', 'pt': 'etiquetas de cinco dimensões',
    },
    '用户侧': {
        'en': 'User side', 'ja': 'ユーザー側', 'ko': '사용자 측',
        'de': 'Nutzerseite', 'es': 'Lado del usuario', 'fr': 'Côté utilisateur',
        'pt': 'Lado do usuário',
    },
    '播放器侧': {
        'en': 'Player side', 'ja': 'プレイヤー側', 'ko': '플레이어 측',
        'de': 'Player-Seite', 'es': 'Lado del reproductor', 'fr': 'Côté lecteur',
        'pt': 'Lado do player',
    },
    '写入类操作': {
        'en': 'write operations', 'ja': '書き込み系操作', 'ko': '쓰기 작업',
        'de': 'Schreibvorgänge', 'es': 'operaciones de escritura',
        'fr': 'opérations d\u2019écriture', 'pt': 'operações de escrita',
    },
    '每天刷新一批卡': {
        'en': 'Refresh a batch of cards daily', 'ja': '毎日カードを一新',
        'ko': '매일 카드 한 묶음 갱신', 'de': 'Täglich neue Karten',
        'es': 'Actualiza tarjetas cada día', 'fr': 'Nouvelles cartes chaque jour',
        'pt': 'Atualiza cartões a cada dia',
    },
    # ── 短词组（用于补齐被配额卡住的 pt，其余语言已由机器翻译覆盖）──
    '两条路径': {
        'pt': 'Duas rotas',
    },
    '三个域都可以停，大脑一直在线': {
        'pt': 'Os três domínios podem parar; o cérebro permanece online',
    },
    '每日刷新': {
        'pt': 'Atualização diária',
    },
    '每日轮播几张卡，不多说一句': {
        'pt': 'Alguns cartões giram a cada dia, sem dizer mais nada',
    },
    '时段 · 最近在听': {
        'pt': 'Período · ouvido recentemente',
    },
    '不说话': {
        'pt': 'Não falar',
    },
    '推断音乐，不推断人——拒绝 MBTI 式人格标签': {
        'pt': 'Inferir música, não pessoas — sem rótulos de personalidade tipo MBTI',
    },
    '许可与信任': {
        'pt': 'Permissão e confiança',
    },
    '只做两个动作': {
        'pt': 'Apenas duas ações',
    },
    '你的话最算数': {
        'pt': 'Sua palavra é a que mais vale',
    },
    '唯一大脑 · 永不暂停': {
        'pt': 'O único cérebro · nunca pausa',
    },
    '每一档都是临场编排。': {
        'pt': 'Cada sessão é improvisada na hora.',
    },
    '少说话': {
        'pt': 'Fala pouco',
    },
    '带进下一轮上下文': {
        'pt': 'Levado ao próximo contexto',
    },
    '追加队列 / 替换队列': {
        'pt': 'Acrescentar à fila / substituir a fila',
    },
    '⑤ 反思': {
        'pt': '⑤ Reflexão',
    },
    '语言 · 年代': {
        'pt': 'Idioma · década',
    },
    '问候': {
        'pt': 'Saudação',
    },
    '本地词表': {
        'pt': 'Léxico local',
    },
    '共用基建': {
        'pt': 'Infraestrutura compartilhada',
    },
    '对曲库的全部认识': {
        'pt': 'Todo o conhecimento da biblioteca',
    },
    '记忆只存设备': {
        'pt': 'Memória só no dispositivo',
    },
    '电量过半、连着 WiFi': {
        'pt': 'Bateria acima da metade e conectado ao WiFi',
    },
    'Agent · 协作': {
        'pt': 'Agente · Colaboração',
    },
    '用量记账': {
        'pt': 'Registro de uso',
    },
    '六轮编排': {
        'pt': 'Orquestração em seis rodadas',
    },
    '对话与上下文': {
        'pt': 'Diálogo e contexto',
    },
    # ── 配额耗尽后人工补译的长句（pt）──
    '它长在既有播放器上，没有另起一套架构。': {
        'pt': 'Ele cresce sobre o reprodutor existente, sem uma arquitetura nova.',
    },
    '推荐 / 探索 / 唤醒 / 纪念日，最多四张': {
        'pt': 'Recomendação / descoberta / resgate / aniversário, no máximo quatro',
    },
    '画像、对话、审计、用量账本全部存在本地数据库；没有账号，没有云同步。发往你自己填写的 AI 端点的，是对话、曲库摘要，以及认识歌曲时需要的歌名歌手、画像片段等上下文——发什么、发往哪家，界面明示。每次模型调用的用量按角色、端点、时间入账，监控看板实时可查。': {
        'pt': 'O perfil, o diálogo, a auditoria e o registro de uso ficam no banco local; '
              'sem conta e sem sincronização na nuvem. O que vai para o seu endpoint de IA '
              'é o diálogo, o resumo da biblioteca e o contexto necessário para conhecer '
              'as músicas (título, artista, trechos do perfil) — a interface mostra o que '
              'é enviado e para onde. O uso de cada chamada ao modelo é registrado por '
              'papel, endpoint e horário, consultável no painel em tempo real.',
    },
    '「你 90 天没听这首曾经的最爱」': {
        'pt': '"Você não ouve esta que já foi sua favorita há 90 dias"',
    },
    '播放队列与状态': {
        'pt': 'Fila de reprodução e estado',
    },
    '滑动收档：音乐暂停、队列保留，情境连续可续上': {
        'pt': 'Deslize para encerrar a sessão: a música pausa, a fila permanece e o '
              'contexto continua retomável',
    },
    '② 曲库认识 Enrich —— 导入即认识，越用越认得': {
        'pt': '② Compreensão da biblioteca Enrich — conhece ao importar, reconhece cada vez mais',
    },
    '但它最要紧的动作是': {
        'pt': 'Mas sua ação mais importante é',
    },
    '记忆不占窗口 · 窗口固定 64K · 步数硬熔断': {
        'pt': 'A memória não ocupa a janela · janela fixa de 64K · corte rígido de etapas',
    },
    '调用': {
        'pt': 'Chamada',
    },
    '不点开就不追问，点开才算送达；完整理由留在审计日志页，随时可查': {
        'pt': 'Sem abrir, não insiste; só ao abrir é entregue. O motivo completo fica '
              'na página de auditoria, consultável a qualquer momento',
    },
    '它是唯一的大脑，': {
        'pt': 'É o único cérebro,',
    },
    '① 电台 Radio —— 它替你排歌，不唠嗑': {
        'pt': '① Rádio Radio — ele organiza as músicas por você, sem tagarelar',
    },
    '你按下开播，它不给你一份歌单，而是直接铺开一晚上的路。它看此刻的时段、最近在听什么、对曲库的全部认识，排出这一档节目。它话不多，主要靠选歌说话。': {
        'pt': 'Você inicia a transmissão e ele não entrega uma lista, mas abre o caminho '
              'de uma noite inteira. Ele observa o horário, o que você ouviu recentemente '
              'e todo o conhecimento da biblioteca para montar a sessão. Ele fala pouco: '
              'fala principalmente pela escolha das músicas.',
    },
    '优先级 2 · 电量 ≥20% 或 WiFi 时运行': {
        'pt': 'Prioridade 2 · executa com bateria ≥20% ou WiFi',
    },
    '模糊需求走工具循环；「下一首」这类高频指令走本地词表，毫秒直达、零 token。': {
        'pt': 'Pedidos vagos passam pelo ciclo de ferramentas; comandos frequentes como '
              '"próxima faixa" passam pelo léxico local, direto em milissegundos e sem tokens.',
    },
    '两条调度路径：工具循环（30 个原子工具）· 内建意图（不经模型）': {
        'pt': 'Duas rotas de orquestração: ciclo de ferramentas (30 ferramentas atômicas) · '
              'intenções nativas (sem passar pelo modelo)',
    },
    '你在听歌、正在操作 → 闭嘴\u3000·\u3000同一件事不跨天重复 → 不打扰': {
        'pt': 'Você ouvindo ou operando → ele se cala　·　a mesma coisa não se repete '
              'em dias seguidos → sem incomodar',
    },
    '一个大歌手拆成若干批，六轮走完——目标把九成以上的歌都认下来': {
        'pt': 'Um grande artista é dividido em vários lotes, concluídos em seis rodadas — '
              'a meta é conhecer mais de noventa por cento das músicas',
    },
}


def key_of(text):
    return hashlib.sha1(text.encode()).hexdigest()[:10]


def write_shim(lang, done):
    with open(f'{lang}.js', 'w', encoding='utf-8') as f:
        f.write(f'/* generated from {lang}.json — do not edit; rerun translate.py */\n')
        f.write('window.SITE_I18N=window.SITE_I18N||{};\n')
        f.write(f'window.SITE_I18N.{lang}=' + json.dumps(done, ensure_ascii=False) + ';\n')


def main():
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    # 仅对「机器翻译配额未补齐」的语言允许插入新 key（避免覆盖 translate.py 成果）
    insert_langs = {'pt'}
    langs = sorted({l for t in TERMS.values() for l in t})
    applied = {l: 0 for l in langs}
    for zh_text, per_lang in TERMS.items():
        k = key_of(zh_text)
        for lang, val in per_lang.items():
            path = f'{lang}.json'
            if not os.path.exists(path):
                continue
            d = json.load(open(path, encoding='utf-8'))
            if k not in d and lang not in insert_langs:
                continue      # 该 key 不在本词典（页面未收录或由 translate 负责）
            if d.get(k) != val:
                d[k] = val
                applied[lang] += 1
                json.dump(d, open(path, 'w', encoding='utf-8'),
                          ensure_ascii=False, indent=1, sort_keys=True)
    for lang in langs:
        d = json.load(open(f'{lang}.json', encoding='utf-8'))
        write_shim(lang, d)
        print(f'[{lang}] {applied[lang]} terms refined, {len(d)} keys')


if __name__ == '__main__':
    main()
