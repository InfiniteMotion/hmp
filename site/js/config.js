/* ============================================================
 * 站点唯一配置源（Single Source of Truth）
 * 版本升级时只需修改此文件，并与根目录 gradle.properties 的
 * hmp.versionName 保持一致；各页面的版本徽标、下载链接、
 * 版权行均由 main.js 在启动时从这里读取填充。
 * ============================================================ */
window.SITE = {
  name: 'Hearable Music Player',
  shortName: 'HMP',
  version: '7.1.0',
  released: '2026-08-26',
  author: 'WLYB',
  year: 2026,
  repo: 'https://github.com/InfiniteMotion/HMP',
  /* 与应用一致的界面语言（对齐 shared-ui composeResources/values-*），
     name 用各自语言的自称；zh 为源语言（页面内置内容） */
  /* 语言清单：仅收录 i18n/{code}.json 已完整翻译的语言；
     新语言翻译完成后（键数与 zh.json 一致）再加回此处 */
  langs: [
    { code: 'zh', name: '中文' },
    { code: 'en', name: 'English' },
    { code: 'ja', name: '日本語' },
    { code: 'ko', name: '한국어' },
    { code: 'de', name: 'Deutsch' },
    { code: 'es', name: 'Español' },
    { code: 'fr', name: 'Français' },
    { code: 'pt', name: 'Português' }
    /* 待翻译完成后启用：ru vi th id hi ar */
  ],
  /* Release 资产文件名模板，{v} 替换为版本号 */
  assets: {
    apk: 'HMP-v{v}-release.apk',
    msi: 'HMP-v{v}-windows.msi',
    dmg: 'HMP-v{v}-macos.dmg',
    deb: 'HMP-v{v}-linux.deb',
    appimage: 'HMP-v{v}-linux.AppImage'
  }
};
