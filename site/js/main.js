/* ============================================================
   HMP Site — shared behaviours
   主题切换 / 功能轮播 / 截图预览 / 更新日志折叠
   所有页面共用；head 里的内联片段只负责首屏防闪烁。
   ============================================================ */

(function () {
  'use strict';

  var THEME_KEY = 'theme';
  var ICON_DARK = '\u263D';  /* ☽ 暗色 */
  var ICON_LIGHT = '\u263C'; /* ☼ 亮色 */

  var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ── 主题 ─────────────────────────────────────── */

  function syncThemeUi(theme) {
    var isDark = theme === 'dark';
    document.querySelectorAll('#themeIcon').forEach(function (el) {
      el.innerHTML = isDark ? ICON_DARK : ICON_LIGHT;
    });
    document.querySelectorAll('.theme-toggle').forEach(function (btn) {
      btn.setAttribute('aria-label', isDark ? '切换到亮色主题' : '切换到暗色主题');
      btn.setAttribute('aria-pressed', isDark ? 'true' : 'false');
    });
    document.querySelectorAll('meta[name="theme-color"]').forEach(function (m) {
      m.setAttribute('content', isDark ? '#121212' : '#F5F5F7');
    });
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    syncThemeUi(theme);
  }

  window.toggleTheme = function () {
    var next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    try { localStorage.setItem(THEME_KEY, next); } catch (e) {}
    applyTheme(next);
  };

  /* 用户没手动选过时跟随系统 */
  var darkQuery = window.matchMedia('(prefers-color-scheme: dark)');
  var onSystemChange = function (e) {
    var saved = null;
    try { saved = localStorage.getItem(THEME_KEY); } catch (err) {}
    if (!saved) applyTheme(e.matches ? 'dark' : 'light');
  };
  if (darkQuery.addEventListener) darkQuery.addEventListener('change', onSystemChange);
  else if (darkQuery.addListener) darkQuery.addListener(onSystemChange);

  /* ── 首页功能轮播 ─────────────────────────────── */

  function initSlides() {
    var wrap = document.querySelector('.feature-slides');
    if (!wrap) return;

    var pages = Array.prototype.slice.call(wrap.querySelectorAll('.feature-page'));
    if (pages.length < 2) return;

    var dotsBox = document.getElementById('swipeDots');
    var AUTOPLAY_MS = 6000;
    /* 自动播放只在宽屏启用；窄屏由用户手动横滑，滑动后不应自动跳走 */
    var autoplayEnabled = window.innerWidth > 860;
    var current = 0;
    var locked = false;
    var timer;

    if (dotsBox) {
      dotsBox.innerHTML = '';
      pages.forEach(function (page, i) {
        var dot = document.createElement('button');
        dot.type = 'button';
        dot.className = 'swipe-dot' + (i === 0 ? ' active' : '');
        var label = page.querySelector('h2, h3');
        dot.setAttribute('aria-label', '查看：' + (label ? label.textContent : '第 ' + (i + 1) + ' 项'));
        dot.addEventListener('click', function () { go(i); });
        dotsBox.appendChild(dot);
      });
    }

    function render() {
      pages.forEach(function (p, i) {
        var active = i === current;
        p.classList.toggle('active', active);
        p.setAttribute('aria-hidden', active ? 'false' : 'true');
      });
      if (dotsBox) {
        dotsBox.querySelectorAll('.swipe-dot').forEach(function (d, i) {
          d.classList.toggle('active', i === current);
        });
      }
    }

    function go(index) {
      index = ((index % pages.length) + pages.length) % pages.length;
      if (index === current || locked) return;
      locked = true;
      current = index;
      render();
      setTimeout(function () { locked = false; }, 350);
      restart();
    }

    function restart() {
      clearTimeout(timer);
      if (reduceMotion || !autoplayEnabled) return;
      timer = setTimeout(function () { go(current + 1); }, AUTOPLAY_MS);
    }

    /* 桌面端：滚轮翻页 + 悬停暂停 */
    if (autoplayEnabled) {
      pages.forEach(function (card) {
        card.addEventListener('mouseenter', function () { clearTimeout(timer); });
        card.addEventListener('mouseleave', restart);
      });
      document.addEventListener('wheel', function (e) {
        if (locked) return;
        go(current + (e.deltaY > 0 ? 1 : -1));
      }, { passive: true });
      restart();
    }

    document.addEventListener('keydown', function (e) {
      if (e.key === 'ArrowRight') go(current + 1);
      else if (e.key === 'ArrowLeft') go(current - 1);
    });

    /* 横向滑动切换：只用 Pointer Events，避免 touch + pointer 双触发跳两页。
       阈值 40px，且横向位移须大于纵向，否则会把页面纵向滚动误判成切换 */
    var startX = null;
    var startY = null;

    wrap.addEventListener('pointerdown', function (e) {
      if (e.pointerType === 'mouse') { startX = null; return; }
      startX = e.clientX;
      startY = e.clientY;
    });
    wrap.addEventListener('pointercancel', function () { startX = null; }, { passive: true });
    wrap.addEventListener('pointerup', function (e) {
      if (startX === null || e.pointerType === 'mouse') return;
      var dx = e.clientX - startX;
      var dy = e.clientY - startY;
      startX = null;
      if (Math.abs(dx) < 40 || Math.abs(dx) <= Math.abs(dy)) return;
      go(current + (dx < 0 ? 1 : -1));
    });

    render();
  }

  /* ── 下载页截图预览 ───────────────────────────── */

  function initPreviews() {
    document.querySelectorAll('[data-preview]').forEach(function (group) {
      var nav = group.querySelector('.preview-nav');
      var img = group.querySelector('.preview-screen img');
      if (!nav || !img) return;

      nav.addEventListener('click', function (e) {
        var item = e.target.closest('.preview-nav-item');
        if (!item || !item.dataset.src) return;
        nav.querySelectorAll('.preview-nav-item').forEach(function (b) { b.classList.remove('active'); });
        item.classList.add('active');
        var src = item.getAttribute('data-src');
        if (img.getAttribute('src') === src) return;
        img.classList.add('switching');
        setTimeout(function () {
          img.setAttribute('src', src);
          img.classList.remove('switching');
        }, 150);
      });
    });
  }

  /* ── Canvas 极光背景（噪声流体 + 光标交互 + 滚动视差） ── */
  /* 在复刻 CSS orbDrift 关键帧的基础上叠加三层：
     1. simplex 噪声扰动 —— 光斑中心持续有机游移，半径呼吸
     2. 光标场 —— 光斑被光标轻微吸引/放大，光标本身带一团跟随光晕
     3. 滚动视差 —— 三斑深度系数不同，滚动时错落浮动（有界，不跑丢）
     纪律不变：切后台停 rAF；prefers-reduced-motion 静态单帧；dpr≤2。 */

  /* 紧凑版 3D simplex noise（Gustavson 公共域实现的精简移植） */
  var noise3 = (function () {
    var grad = [[1,1,0],[-1,1,0],[1,-1,0],[-1,-1,0],[1,0,1],[-1,0,1],[1,0,-1],[-1,0,-1],[0,1,1],[0,-1,1],[0,1,-1],[0,-1,-1]];
    var p = new Uint8Array(256);
    for (var i = 0; i < 256; i++) p[i] = i;
    var seed = 1337;
    function rnd() { seed = (seed * 16807) % 2147483647; return seed / 2147483647; }
    for (i = 255; i > 0; i--) { var j = Math.floor(rnd() * (i + 1)); var t = p[i]; p[i] = p[j]; p[j] = t; }
    var perm = new Uint8Array(512), permMod12 = new Uint8Array(512);
    for (i = 0; i < 512; i++) { perm[i] = p[i & 255]; permMod12[i] = perm[i] % 12; }
    var F3 = 1 / 3, G3 = 1 / 6;
    return function (xin, yin, zin) {
      var s = (xin + yin + zin) * F3;
      var i0 = Math.floor(xin + s), j0 = Math.floor(yin + s), k0 = Math.floor(zin + s);
      var t = (i0 + j0 + k0) * G3;
      var x0 = xin - (i0 - t), y0 = yin - (j0 - t), z0 = zin - (k0 - t);
      var i1, j1, k1, i2, j2, k2;
      if (x0 >= y0) {
        if (y0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
        else if (x0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1; }
        else { i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1; }
      } else {
        if (y0 < z0) { i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1; }
        else if (x0 < z0) { i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1; }
        else { i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
      }
      var x1 = x0 - i1 + G3, y1 = y0 - j1 + G3, z1 = z0 - k1 + G3;
      var x2 = x0 - i2 + 2 * G3, y2 = y0 - j2 + 2 * G3, z2 = z0 - k2 + 2 * G3;
      var x3 = x0 - 1 + 3 * G3, y3 = y0 - 1 + 3 * G3, z3 = z0 - 1 + 3 * G3;
      var ii = i0 & 255, jj = j0 & 255, kk = k0 & 255;
      var n = 0;
      var t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0;
      if (t0 > 0) { var g0 = grad[permMod12[ii + perm[jj + perm[kk]]]]; t0 *= t0; n += t0 * t0 * (g0[0] * x0 + g0[1] * y0 + g0[2] * z0); }
      var t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
      if (t1 > 0) { var g1 = grad[permMod12[ii + i1 + perm[jj + j1 + perm[kk + k1]]]]; t1 *= t1; n += t1 * t1 * (g1[0] * x1 + g1[1] * y1 + g1[2] * z1); }
      var t2 = 0.6 - x2 * x2 - y2 * y2 - z2 * z2;
      if (t2 > 0) { var g2 = grad[permMod12[ii + i2 + perm[jj + j2 + perm[kk + k2]]]]; t2 *= t2; n += t2 * t2 * (g2[0] * x2 + g2[1] * y2 + g2[2] * z2); }
      var t3 = 0.6 - x3 * x3 - y3 * y3 - z3 * z3;
      if (t3 > 0) { var g3 = grad[permMod12[ii + 1 + perm[jj + 1 + perm[kk + 1]]]]; t3 *= t3; n += t3 * t3 * (g3[0] * x3 + g3[1] * y3 + g3[2] * z3); }
      return 32 * n;
    };
  })();

  function initAurora() {
    var host = document.querySelector('.aurora-bg');
    if (!host || host.querySelector('canvas')) return;

    var canvas = document.createElement('canvas');
    canvas.setAttribute('aria-hidden', 'true');
    canvas.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;';
    host.appendChild(canvas);
    /* JS 成功接管后才隐藏 CSS 光斑；脚本失败时保留原方案兜底 */
    host.querySelectorAll('.hero-orb').forEach(function (orb) {
      orb.style.display = 'none';
    });
    var ctx = canvas.getContext('2d');
    if (!ctx) {
      host.querySelectorAll('.hero-orb').forEach(function (orb) { orb.style.display = ''; });
      canvas.remove();
      return;
    }

    /* 每个光斑：直径(px)、动画周期(s)、关键帧（百分比坐标按各自定位轴）、
       drift（噪声游移幅度 px）、parallax（滚动视差系数）、depth（滚动相位） */
    var ORBS = [
      { size: 600, dur: 50, axis: 'tl', drift: 90, par: 0.036, phase: 0.0, keys: [
        { x: -10, y: -15, s: 1.00, o: .50 }, { x: 55, y: 60, s: 1.15, o: .60 },
        { x: 70, y: 10, s: .88, o: .40 }, { x: -15, y: 55, s: 1.08, o: .55 }] },
      { size: 500, dur: 60, axis: 'tr', drift: 70, par: 0.026, phase: 2.1, keys: [
        { x: -10, y: 50, s: 1.00, o: .50 }, { x: 40, y: -10, s: 1.12, o: .42 },
        { x: 60, y: 70, s: .85, o: .62 }, { x: -15, y: 20, s: 1.06, o: .45 }] },
      { size: 400, dur: 45, axis: 'bl', drift: 110, par: 0.05, phase: 4.2, keys: [
        { x: 30, y: -10, s: 1.00, o: .50 }, { x: -10, y: 50, s: 1.14, o: .40 },
        { x: 65, y: -15, s: .84, o: .65 }, { x: 40, y: 60, s: 1.10, o: .52 }] }
    ];
    var THEMES = {
      dark: [[201, 44, 44], [0, 47, 167], [108, 92, 231]],
      light: [[229, 57, 53], [21, 101, 192], [124, 77, 255]]
    };

    /* 平行频谱线：细波动的曲线，两端密、中间疏，略微可见 */
    var SPECTRUM = [
      { y: 0.05, amp: 14, speed: 0.35, color: 0, alpha: 0.09 },
      { y: 0.13, amp: 18, speed: 0.27, color: 1, alpha: 0.08 },
      { y: 0.22, amp: 12, speed: 0.31, color: 2, alpha: 0.09 },
      { y: 0.33, amp: 16, speed: 0.24, color: 0, alpha: 0.08 },
      { y: 0.50, amp: 20, speed: 0.33, color: 1, alpha: 0.09 },
      { y: 0.67, amp: 13, speed: 0.28, color: 2, alpha: 0.08 },
      { y: 0.78, amp: 17, speed: 0.38, color: 0, alpha: 0.09 },
      { y: 0.87, amp: 14, speed: 0.26, color: 1, alpha: 0.08 },
      { y: 0.95, amp: 18, speed: 0.32, color: 2, alpha: 0.09 }
    ];

    var w, h, dpr;
    function resize() {
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      w = window.innerWidth; h = window.innerHeight;
      canvas.width = w * dpr; canvas.height = h * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    window.addEventListener('resize', resize);
    resize();

    /* 光标状态：目标值 + 平滑跟随值；pointerleave 后缓慢熄灭 */
    var mouse = { tx: 0, ty: 0, x: 0, y: 0, ta: 0, a: 0 };
    window.addEventListener('pointermove', function (e) {
      mouse.tx = e.clientX; mouse.ty = e.clientY; mouse.ta = 1;
    }, { passive: true });
    document.addEventListener('pointerleave', function () { mouse.ta = 0; });
    document.addEventListener('pointerdown', function (e) {
      mouse.tx = e.clientX; mouse.ty = e.clientY; mouse.ta = 1; mouse.a = 1.4;  /* 按下瞬间光晕增强 */
    }, { passive: true });

    function smoothstep(t) { return t * t * (3 - 2 * t); }

    /* 周期相位 → 关键帧插值（复刻 CSS 每段 ease-in-out） */
    function sample(orb, phase) {
      var n = orb.keys.length;
      var f = phase * n;
      var i = Math.floor(f) % n;
      var j = (i + 1) % n;
      var t = smoothstep(f - Math.floor(f));
      var a = orb.keys[i], b = orb.keys[j];
      return {
        x: a.x + (b.x - a.x) * t,
        y: a.y + (b.y - a.y) * t,
        s: a.s + (b.s - a.s) * t,
        o: a.o + (b.o - a.o) * t
      };
    }

    function draw(now) {
      var theme = document.documentElement.getAttribute('data-theme');
      var isLight = theme === 'light';
      var colors = THEMES[isLight ? 'light' : 'dark'];
      ctx.clearRect(0, 0, w, h);

      /* 光标平滑跟随与能量衰减；v 为平滑后的移动速度（px/帧），供频谱线驻留判断 */
      mouse.x += (mouse.tx - mouse.x) * 0.06;
      mouse.y += (mouse.ty - mouse.y) * 0.06;
      mouse.a += (mouse.ta - mouse.a) * 0.04;
      var instV = Math.hypot(mouse.tx - mouse.x, mouse.ty - mouse.y);
      mouse.v = (mouse.v || 0) * 0.85 + instV * 0.15;

      var t = now * 0.001;
      var scroll = window.scrollY || 0;

      for (var i = 0; i < ORBS.length; i++) {
        var orb = ORBS[i];
        var k = sample(orb, ((now / 1000) % orb.dur) / orb.dur);
        var size = orb.size * k.s;

        /* 复刻 CSS 定位轴：tl=top+left，tr=top+right，bl=bottom+left */
        var left = orb.axis === 'tr' ? w - size - (k.x / 100) * w : (k.x / 100) * w;
        var top = orb.axis === 'bl' ? h - size - (k.y / 100) * h : (k.y / 100) * h;
        var cx = left + size / 2, cy = top + size / 2;

        /* simplex 噪声：中心有机游移 + 半径呼吸 */
        var np = orb.phase * 10;
        cx += noise3(cx * 0.0011, cy * 0.0011, t * 0.11 + np) * orb.drift;
        cy += noise3(cx * 0.0011 + 50, cy * 0.0011 + 50, t * 0.11 + np) * orb.drift * 0.8;
        size *= 1 + noise3(np, 0, t * 0.14) * 0.07;

        /* 滚动视差：有界的正弦浮动，三斑相位不同产生深度错落 */
        cy += Math.sin(scroll * 0.0035 + orb.phase) * (36 + i * 18);

        /* 光标场：吸引 + 轻微放大，距离衰减 */
        var mdx = mouse.x - cx, mdy = mouse.y - cy;
        var md = Math.sqrt(mdx * mdx + mdy * mdy) || 1;
        var fall = Math.max(0, 1 - md / 720) * mouse.a;
        cx += (mdx / md) * fall * 70;
        cy += (mdy / md) * fall * 70;
        size *= 1 + fall * 0.12;

        var r = Math.max(size, 10) / 2;

        var c = colors[i];
        var g = ctx.createRadialGradient(cx, cy, 0, cx, cy, r + 100);
        g.addColorStop(0, 'rgba(' + c[0] + ',' + c[1] + ',' + c[2] + ',1)');
        g.addColorStop(0.5, 'rgba(' + c[0] + ',' + c[1] + ',' + c[2] + ',0.55)');
        g.addColorStop(0.82, 'rgba(' + c[0] + ',' + c[1] + ',' + c[2] + ',0.16)');
        g.addColorStop(1, 'rgba(' + c[0] + ',' + c[1] + ',' + c[2] + ',0)');
        ctx.globalAlpha = k.o * 0.5;
        ctx.fillStyle = g;
        ctx.beginPath();
        ctx.arc(cx, cy, r + 100, 0, Math.PI * 2);
        ctx.fill();
      }

      /*     平行频谱线：双频噪声驱动的单条细波动曲线，随滚动轻微浮动；
      每条线用各自的「虚影光标」（重重缓动地追赶真实光标）计算避让，
      形变像水波一样被缓慢拖过去，而不是瞬间贴上真实光标 */
      ctx.lineWidth = 1;
      var mA = Math.min(mouse.a, 1.4);
      for (var s = 0; s < SPECTRUM.length; s++) {
        var ln = SPECTRUM[s];
        /* 虚影光标：每条线追赶速度不同（3%~6%/帧），形成错落的延迟层次；
           每条线还有自己的随机宽度（基准 1px × 0.8~1.2）与随机震动
           （振幅 ×0.8~1.2、流速 ×0.85~1.15），初始化一次后固定 */
        if (ln.vx === undefined) {
          ln.vx = mouse.x; ln.vy = mouse.y; ln.inf = 0;
          ln.w = 0.8 + Math.random() * 0.4;
          ln.aj = 0.8 + Math.random() * 0.4;   /* 振幅随机系数 */
          ln.sj = 0.85 + Math.random() * 0.3;  /* 流速随机系数 */
          ln.dwell = 0;                        /* 悬浮驻留：持续照亮多久了 */
        }
        var chase = 0.03 + s * 0.006;
        ln.vx += (mouse.x - ln.vx) * chase;
        ln.vy += (mouse.y - ln.vy) * chase;
        ln.inf += (mA - ln.inf) * 0.05;          /* 影响强度也缓慢起落 */
        if (ln.inf < 0.005) ln.inf = 0;

        var mid = h * ln.y + Math.sin(scroll * 0.002 + s * 1.7) * 22;
        var lc = colors[ln.color];
        ctx.strokeStyle = 'rgba(' + lc[0] + ',' + lc[1] + ',' + lc[2] + ',1)';
        var near = 0;                            /* 该线受光标影响的峰值强度 */
        var baseAlpha = isLight ? ln.alpha * 0.8 : ln.alpha;
        ctx.globalAlpha = baseAlpha;
        ctx.lineWidth = ln.w;
        ctx.beginPath();
        for (var xx = -10; xx <= w + 10; xx += 8) {
          /* 光标附近先“静下来”：噪声震动按高斯衰减抑制，避免推挤时还在抖动显硬 */
          var g = 0;
          if (ln.inf > 0.02) {
            var dx = xx - ln.vx, dy = mid - ln.vy;
            /* sigma=150：影响半径收窄到 ±300px 左右，只拨动贴着光标的一两条线 */
            g = Math.exp(-(dx * dx + dy * dy) / (2 * 150 * 150));
            if (g < 0.004) g = 0;
          }
          var damp = 1 - g * ln.inf * 0.92;      /* 越靠近光标越平静 */
          var ny = (noise3(xx * 0.0016, mid * 0.002, t * ln.speed * ln.sj + s * 7.3) * ln.amp * ln.aj
                 + noise3(xx * 0.007, mid * 0.005, t * ln.speed * ln.sj * 1.7 + s * 3.1) * ln.amp * ln.aj * 0.35) * damp;
          /* 平滑避让：tanh 平滑过零——光标在线上方时向下让、下方时向上让，
             正对时形变自然收敛为零，无方向翻转点 */
          if (g > 0) {
            ny += Math.tanh(dy / 140) * g * 26 * ln.inf;
            if (g * ln.inf > near) near = g * ln.inf;
          }
          if (xx === -10) ctx.moveTo(xx, mid + ny);
          else ctx.lineTo(xx, mid + ny);
        }
        ctx.stroke();
        /* 一根线作为一个整体：该线靠近光标（峰值强度超阈值）则整根提亮；
           但悬浮超过约 1.5s 后提亮平滑暗回常态（驻留衰减）——
           光标一离开（near 低于阈值）驻留立即清零，重新靠近会再次亮起 */
        if (near > 0.12) {
          /* 光标悬停不动才累积驻留；光标在移动则快速回亮 */
          if (mouse.v < 2.5) ln.dwell = Math.min(1, ln.dwell + 1 / 90);
          else ln.dwell = Math.max(0, ln.dwell - 1 / 20);
        } else {
          ln.dwell = 0;
        }
        /* 实际亮度缓慢缓动到目标（约 2.5s 走完过渡），变暗不突兀 */
        var fadeTarget = 1 - ln.dwell * ln.dwell * (3 - 2 * ln.dwell);
        if (ln.fade === undefined) ln.fade = 1;
        ln.fade += (fadeTarget - ln.fade) * 0.018;
        var fade = ln.fade;
        if (near > 0.12 && fade > 0.02) {
          ctx.globalAlpha = Math.min(baseAlpha + near * 0.10 * fade, 0.32);
          ctx.lineWidth = ln.w * 1.4;
          ctx.stroke();
          ctx.lineWidth = ln.w;
        }
      }

      ctx.globalAlpha = 1;
    }

    var staticOnly = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var raf;
    function loop(now) {
      draw(now);
      raf = requestAnimationFrame(loop);
    }

    if (staticOnly) {
      draw(0);                             /* 静态一帧，不动画 */
    } else {
      raf = requestAnimationFrame(loop);
      document.addEventListener('visibilitychange', function () {
        if (document.hidden) { cancelAnimationFrame(raf); raf = null; }
        else if (!raf) { raf = requestAnimationFrame(loop); }
      });
    }
  }

  /* ── 更新日志折叠 ─────────────────────────────── */

  window.toggleEntry = function (el) {
    var entry = el.closest('.cl-entry');
    if (!entry) return;
    if (entry.hasAttribute('data-collapsed')) entry.removeAttribute('data-collapsed');
    else entry.setAttribute('data-collapsed', '');
    el.setAttribute('aria-expanded', entry.hasAttribute('data-collapsed') ? 'false' : 'true');
  };

  /* ── 启动 ─────────────────────────────────────── */

  function boot() {
    syncThemeUi(document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark');
    initAurora();
    initSlides();
    initPreviews();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
