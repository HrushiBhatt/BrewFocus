// Sets the theme before the first paint, so a light page never flashes dark. It's a file because the
// page's security policy allows no inline scripts. src/state/theme.js takes over once the app loads,
// so keep the storage key and the colors in step with it.
(function () {
  var choice = null;
  try {
    choice = JSON.parse(localStorage.getItem('brew:theme'));
  } catch {
    // Blocked or corrupt storage: stay dark.
  }
  var light = choice === 'light';
  document.documentElement.setAttribute('data-theme', light ? 'light' : 'dark');
  var meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', light ? '#f7f1e8' : '#000000');
})();
