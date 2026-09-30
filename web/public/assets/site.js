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
  // A page in both languages (the 404 page) stays put: there is no Chinese copy of it.
  var bilingual = document.documentElement.hasAttribute('data-bilingual');
  var onChinese = path === '/zh' || path.indexOf('/zh/') === 0;
  var browserChinese = (navigator.language || '').toLowerCase().indexOf('zh') === 0;
  var preferred = saved() || (browserChinese ? 'zh' : 'en');
  if (!bilingual && !onChinese && preferred === 'zh') {
    location.replace('/zh' + (path === '/' ? '/' : path) + location.hash);
    return;
  }
  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-lang-link]').forEach(function (link) {
      link.addEventListener('click', function () { save(link.getAttribute('data-lang-link')); });
    });
  });
})();

// Screenshots open full size over the page rather than as a separate image: a click (or tap)
// anywhere closes it again, as does Esc; the arrow keys step through them. Without script,
// the links still open the image itself.
document.addEventListener('DOMContentLoaded', function () {
  var links = Array.prototype.slice.call(document.querySelectorAll('.shots a'));
  if (!links.length || typeof HTMLDialogElement !== 'function') return;
  var dialog = document.createElement('dialog');
  dialog.className = 'lightbox';
  var image = document.createElement('img');
  var caption = document.createElement('p');
  dialog.appendChild(image);
  dialog.appendChild(caption);
  document.body.appendChild(dialog);
  var current = 0;
  function show(i) {
    current = (i + links.length) % links.length;
    var link = links[current];
    var thumb = link.querySelector('img');
    image.src = link.href;
    image.alt = thumb ? thumb.alt : '';
    var figcaption = link.parentNode.querySelector('figcaption');
    caption.textContent = figcaption ? figcaption.textContent : '';
  }
  links.forEach(function (link, i) {
    link.addEventListener('click', function (event) {
      // A new tab or window (Ctrl, Cmd, Shift, middle click) still gets the image itself.
      if (event.ctrlKey || event.metaKey || event.shiftKey || event.button !== 0) return;
      event.preventDefault();
      show(i);
      dialog.showModal();
    });
  });
  dialog.addEventListener('click', function () { dialog.close(); });
  dialog.addEventListener('keydown', function (event) {
    if (event.key === 'ArrowRight') show(current + 1);
    else if (event.key === 'ArrowLeft') show(current - 1);
  });
  // The page behind doesn't scroll while one is open.
  dialog.addEventListener('close', function () { document.documentElement.classList.remove('lightbox-open'); });
  var open = dialog.showModal.bind(dialog);
  dialog.showModal = function () { document.documentElement.classList.add('lightbox-open'); open(); };
});
