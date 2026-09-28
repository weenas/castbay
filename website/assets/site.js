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
