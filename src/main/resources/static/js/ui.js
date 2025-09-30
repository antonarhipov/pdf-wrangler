(function(){
  // Global helper to toggle advanced options panels on operation pages
  // Works with inline onclick handlers: onclick="toggleAdvancedOptions()"
  // Also supports being called as onclick="toggleAdvancedOptions(event)" for better scoping
  window.toggleAdvancedOptions = function(evt){
    try {
      // Try to scope to the nearest section when event is provided
      var btn = evt && (evt.currentTarget || evt.target);
      var section = btn && btn.closest ? btn.closest('.border-t') : null;

      // Prefer scoped lookup within the same section
      var panel = section ? section.querySelector('#advancedOptions') : document.getElementById('advancedOptions');
      var arrow = section ? section.querySelector('#advancedArrow') : document.getElementById('advancedArrow');

      if (!panel) return; // nothing to toggle

      var isHidden = panel.classList.contains('hidden');
      if (isHidden) {
        panel.classList.remove('hidden');
      } else {
        panel.classList.add('hidden');
      }

      if (arrow && arrow.classList) {
        arrow.classList.toggle('rotate-90');
      }
    } catch (e) {
      // Fail silently to avoid breaking the page
    }
  };
})();
