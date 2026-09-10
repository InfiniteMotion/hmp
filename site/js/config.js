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
  /* Release 资产文件名模板，{v} 替换为版本号 */
  assets: {
    apk: 'HMP-v{v}-release.apk',
    msi: 'HMP-v{v}-windows.msi',
    dmg: 'HMP-v{v}-macos.dmg',
    deb: 'HMP-v{v}-linux.deb',
    appimage: 'HMP-v{v}-linux.AppImage'
  }
};
