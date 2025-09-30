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

  // Handle file selection for split operation
  window.handleFileSelection = function() {
    try {
      var fileInput = document.getElementById('fileInput');
      var selectedFileDiv = document.getElementById('selectedFile');
      var fileDetailsDiv = document.getElementById('fileDetails');
      
      if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
        return;
      }
      
      var file = fileInput.files[0];
      
      // Display file information
      var fileSize = (file.size / (1024 * 1024)).toFixed(2); // Convert to MB
      fileDetailsDiv.innerHTML = '<div><p class="font-medium text-gray-900">' + file.name + '</p>' +
                                 '<p class="text-sm text-gray-500">' + fileSize + ' MB</p></div>';
      
      // Show the selected file section
      selectedFileDiv.classList.remove('hidden');
    } catch (e) {
      console.error('Error handling file selection:', e);
    }
  };

  // Remove selected file
  window.removeFile = function() {
    try {
      var fileInput = document.getElementById('fileInput');
      var selectedFileDiv = document.getElementById('selectedFile');
      
      if (fileInput) {
        fileInput.value = '';
      }
      
      if (selectedFileDiv) {
        selectedFileDiv.classList.add('hidden');
      }
    } catch (e) {
      console.error('Error removing file:', e);
    }
  };

  // Update split options based on selected strategy
  window.updateSplitOptions = function() {
    try {
      var strategySelect = document.getElementById('splitStrategy');
      if (!strategySelect) return;
      
      var selectedStrategy = strategySelect.value;
      
      // Hide all strategy-specific options
      var pageRangesOptions = document.getElementById('pageRangesOptions');
      var fileSizeOptions = document.getElementById('fileSizeOptions');
      var documentSectionOptions = document.getElementById('documentSectionOptions');
      
      if (pageRangesOptions) pageRangesOptions.classList.add('hidden');
      if (fileSizeOptions) fileSizeOptions.classList.add('hidden');
      if (documentSectionOptions) documentSectionOptions.classList.add('hidden');
      
      // Show the relevant options based on strategy
      if (selectedStrategy === 'pageRanges' && pageRangesOptions) {
        pageRangesOptions.classList.remove('hidden');
      } else if (selectedStrategy === 'fileSize' && fileSizeOptions) {
        fileSizeOptions.classList.remove('hidden');
      } else if (selectedStrategy === 'documentSection' && documentSectionOptions) {
        documentSectionOptions.classList.remove('hidden');
      }
    } catch (e) {
      console.error('Error updating split options:', e);
    }
  };

  // Add a new page range input field
  window.addPageRange = function() {
    try {
      var pageRangesList = document.getElementById('pageRangesList');
      if (!pageRangesList) return;
      
      // Create new page range input container
      var newRangeDiv = document.createElement('div');
      newRangeDiv.className = 'flex items-center space-x-2';
      
      // Create input field
      var newInput = document.createElement('input');
      newInput.type = 'text';
      newInput.name = 'pageRanges';
      newInput.placeholder = 'e.g., 6-10';
      newInput.className = 'page-range-input px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-pdf-blue focus:border-transparent flex-1';
      
      // Create remove button
      var removeButton = document.createElement('button');
      removeButton.type = 'button';
      removeButton.className = 'px-3 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors';
      removeButton.textContent = 'Remove';
      removeButton.onclick = function() {
        removePageRange(newRangeDiv);
      };
      
      // Append elements
      newRangeDiv.appendChild(newInput);
      newRangeDiv.appendChild(removeButton);
      pageRangesList.appendChild(newRangeDiv);
      
      // Focus on the new input
      newInput.focus();
    } catch (e) {
      console.error('Error adding page range:', e);
    }
  };

  // Remove a page range input field
  window.removePageRange = function(rangeDiv) {
    try {
      if (rangeDiv && rangeDiv.parentNode) {
        rangeDiv.parentNode.removeChild(rangeDiv);
      }
    } catch (e) {
      console.error('Error removing page range:', e);
    }
  };
})();
