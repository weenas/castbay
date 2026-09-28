// Dark or light, as on weenas.com: the choice made with the theme button if any, else the
// system's; the system switching (e.g. at sunset) switches the page too. Set before the page
// paints, so it never flashes in the other theme.
(function () {
  var KEY = 'castbay-theme';
  var system = window.matchMedia('(prefers-color-scheme: dark)');
  function stored() {
    try { return localStorage.getItem(KEY); } catch (e) { return null; }
  }
  var theme = stored() || (system.matches ? 'dark' : 'light');
  function apply() {
    document.documentElement.setAttribute('data-theme', theme);
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', theme === 'dark' ? '#0b0910' : '#f7f5fb');
    var button = document.getElementById('theme-btn');
    if (button) button.setAttribute('aria-label', theme === 'dark' ? button.dataset.toLight : button.dataset.toDark);
  }
  function choose(next) {
    theme = next;
    try { localStorage.setItem(KEY, theme); } catch (e) { /* private mode: still switches */ }
    apply();
  }
  apply();
  system.addEventListener('change', function (event) { choose(event.matches ? 'dark' : 'light'); });
  document.addEventListener('DOMContentLoaded', function () {
    apply();
    var button = document.getElementById('theme-btn');
    if (button) button.addEventListener('click', function () { choose(theme === 'dark' ? 'light' : 'dark'); });
  });
})();

// Each language has its own pages: English at the root, Chinese under /zh/.
// English pages send visitors who prefer Chinese (chosen with the switch, or else their
// browser's language) to the Chinese page; choosing English with the switch is remembered.
(function () {
  var KEY = 'castbay-lang';
  function saved() {
    try { return localStorage.getItem(KEY); } catch (e) { return null; }
  }
  function save(lang) {
    try { localStorage.setItem(KEY, lang); } catch (e) { /* private mode: the link still works */ }
  }
  var path = location.pathname;
  var onChinese = path === '/zh' || path.indexOf('/zh/') === 0;
  var browserChinese = (navigator.language || '').toLowerCase().indexOf('zh') === 0;
  var preferred = saved() || (browserChinese ? 'zh' : 'en');
  if (!onChinese && preferred === 'zh') {
    location.replace('/zh' + (path === '/' ? '/' : path) + location.hash);
    return;
  }
  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-lang-link]').forEach(function (link) {
      link.addEventListener('click', function () { save(link.getAttribute('data-lang-link')); });
    });
  });
})();
