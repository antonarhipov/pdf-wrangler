(function(){
  function getPreferredTheme(){
    try {
      const stored = localStorage.getItem('theme');
      if (stored === 'dark' || stored === 'light') return stored;
    } catch (e) {}
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  function applyTheme(theme){
    const root = document.documentElement;
    if (theme === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }

  function saveTheme(theme){
    try { localStorage.setItem('theme', theme); } catch (e) {}
  }

  function createToggle(){
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.id = 'themeToggle';
    btn.className = 'ml-4 inline-flex items-center gap-2 px-3 py-1.5 rounded border border-gray-300 text-sm text-gray-700 hover:bg-blue-50';
    btn.setAttribute('aria-label', 'Toggle dark mode');

    function render(){
      const isDark = document.documentElement.classList.contains('dark');
      btn.innerHTML = isDark ? '☀️ Light' : '🌙 Dark';
    }

    btn.addEventListener('click', function(){
      const isDark = document.documentElement.classList.toggle('dark');
      saveTheme(isDark ? 'dark' : 'light');
      render();
    });

    // react to system changes if user hasn't set a preference
    try {
      if (!localStorage.getItem('theme') && window.matchMedia) {
        window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e)=>{
          const preferred = e.matches ? 'dark' : 'light';
          applyTheme(preferred);
          render();
        });
      }
    } catch(e) {}

    render();
    return btn;
  }

  document.addEventListener('DOMContentLoaded', function(){
    // Ensure theme applied
    applyTheme(getPreferredTheme());

    // Try to place the toggle into the header
    var header = document.querySelector('header');
    if (header) {
      // Place it near the right side inside header content
      var container = header.querySelector('.max-w-7xl, .container, .flex, div');
      // Fallback: create a container at the end of header
      if (!container) container = header;

      // Find the top bar flex that contains branding and right-side text
      var topbar = header.querySelector('.flex.items-center.justify-between');
      if (topbar) {
        // Append to the right side block if present, else to topbar
        var right = topbar.lastElementChild;
        if (right) {
          right.appendChild(createToggle());
        } else {
          topbar.appendChild(createToggle());
        }
      } else {
        container.appendChild(createToggle());
      }
    }
  });
})();
