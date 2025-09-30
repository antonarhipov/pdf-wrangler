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

  // Handle file selection for operations (split or merge)
  window.handleFileSelection = function() {
    try {
      var fileInput = document.getElementById('fileInput');

      if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
        return;
      }

      // Check if this is a merge operation (multiple files) or split operation (single file)
      if (fileInput.hasAttribute('multiple')) {
        // Handle multiple files for merge operation
        handleMultipleFileSelection(fileInput.files);
      } else {
        // Handle single file for split operation
        handleSingleFileSelection(fileInput.files[0]);
      }
    } catch (e) {
      console.error('Error handling file selection:', e);
    }
  };

  // Handle single file selection for split operation
  function handleSingleFileSelection(file) {
    var selectedFileDiv = document.getElementById('selectedFile');
    var fileDetailsDiv = document.getElementById('fileDetails');

    if (!selectedFileDiv || !fileDetailsDiv) return;

    // Display file information
    var fileSize = (file.size / (1024 * 1024)).toFixed(2); // Convert to MB
    fileDetailsDiv.innerHTML = '<div><p class="font-medium text-gray-900">' + file.name + '</p>' +
                               '<p class="text-sm text-gray-500">' + fileSize + ' MB</p></div>';

    // Show the selected file section
    selectedFileDiv.classList.remove('hidden');
  }

  // Handle multiple file selection for merge operation
  function handleMultipleFileSelection(files) {
    var selectedFilesDiv = document.getElementById('selectedFiles');
    var fileListDiv = document.getElementById('fileList');

    if (!selectedFilesDiv || !fileListDiv) return;

    // Clear existing file list
    fileListDiv.innerHTML = '';

    // Add each file to the list
    for (var i = 0; i < files.length; i++) {
      addFileToList(files[i], i);
    }

    // Show the selected files section
    selectedFilesDiv.classList.remove('hidden');

    // Update file count in header
    updateFileCountDisplay(files.length);
  }

  // Remove selected file (for split operation)
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

  // Add a file to the merge file list
  function addFileToList(file, index) {
    var fileListDiv = document.getElementById('fileList');
    if (!fileListDiv || !file) return;

    var fileSize = (file.size / (1024 * 1024)).toFixed(2); // Convert to MB

    // Create list item with drag and drop support
    var listItem = document.createElement('li');
    listItem.className = 'file-item bg-gray-50 border border-gray-200 rounded-lg p-4 hover:bg-gray-100 transition-colors';
    listItem.draggable = true;
    listItem.dataset.fileIndex = index;
    listItem.dataset.fileName = file.name;

    listItem.innerHTML =
      '<div class="flex items-center justify-between">' +
        '<div class="flex items-center space-x-3 flex-1">' +
          '<div class="drag-handle cursor-move text-gray-400 hover:text-gray-600">' +
            '<svg class="h-5 w-5" fill="currentColor" viewBox="0 0 20 20">' +
              '<path d="M3 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zM3 10a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zM3 16a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1z"></path>' +
            '</svg>' +
          '</div>' +
          '<div class="file-icon">' +
            '<svg class="h-8 w-8 text-red-600" fill="currentColor" viewBox="0 0 20 20">' +
              '<path fill-rule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h6a1 1 0 100-2H7z" clip-rule="evenodd"></path>' +
            '</svg>' +
          '</div>' +
          '<div class="file-details flex-1">' +
            '<p class="font-medium text-gray-900">' + file.name + '</p>' +
            '<p class="text-sm text-gray-500">' + fileSize + ' MB</p>' +
          '</div>' +
          '<div class="file-order text-sm text-gray-500 font-medium">' +
            '#' + (index + 1) +
          '</div>' +
        '</div>' +
        '<button type="button" onclick="removeFileFromList(this)" class="ml-4 text-red-600 hover:text-red-800 transition-colors">' +
          '<svg class="h-5 w-5" fill="currentColor" viewBox="0 0 20 20">' +
            '<path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"></path>' +
          '</svg>' +
        '</button>' +
      '</div>';

    // Add drag event listeners
    addDragEventListeners(listItem);

    fileListDiv.appendChild(listItem);
  }

  // Remove a file from the merge file list
  window.removeFileFromList = function(button) {
    try {
      var listItem = button.closest('.file-item');
      if (listItem) {
        listItem.remove();

        // Update file order numbers and check if list is empty
        updateFileOrderNumbers();

        var fileListDiv = document.getElementById('fileList');
        var selectedFilesDiv = document.getElementById('selectedFiles');

        if (fileListDiv && fileListDiv.children.length === 0 && selectedFilesDiv) {
          selectedFilesDiv.classList.add('hidden');
          // Clear file input
          var fileInput = document.getElementById('fileInput');
          if (fileInput) {
            fileInput.value = '';
          }
        }

        updateFileCountDisplay(fileListDiv ? fileListDiv.children.length : 0);
      }
    } catch (e) {
      console.error('Error removing file from list:', e);
    }
  };

  // Update file order numbers after reordering or removal
  function updateFileOrderNumbers() {
    var fileListDiv = document.getElementById('fileList');
    if (!fileListDiv) return;

    var fileItems = fileListDiv.querySelectorAll('.file-item');
    fileItems.forEach(function(item, index) {
      var orderElement = item.querySelector('.file-order');
      if (orderElement) {
        orderElement.textContent = '#' + (index + 1);
      }
      item.dataset.fileIndex = index;
    });
  }

  // Update file count display in the header
  function updateFileCountDisplay(count) {
    var selectedFilesDiv = document.getElementById('selectedFiles');
    if (!selectedFilesDiv) return;

    var headerText = selectedFilesDiv.querySelector('h3');
    if (headerText) {
      headerText.textContent = 'Selected Files (' + count + ' file' + (count !== 1 ? 's' : '') + ') - Drag to reorder:';
    }
  }

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

  // Add drag event listeners to a file list item
  function addDragEventListeners(listItem) {
    var draggedItem = null;

    listItem.addEventListener('dragstart', function(e) {
      draggedItem = this;
      this.classList.add('opacity-50');
      e.dataTransfer.effectAllowed = 'move';
      e.dataTransfer.setData('text/html', this.outerHTML);
    });

    listItem.addEventListener('dragend', function(e) {
      this.classList.remove('opacity-50');
      draggedItem = null;

      // Remove all drag-over styling
      var fileListDiv = document.getElementById('fileList');
      if (fileListDiv) {
        var items = fileListDiv.querySelectorAll('.file-item');
        items.forEach(function(item) {
          item.classList.remove('border-pdf-blue', 'bg-blue-50');
        });
      }
    });

    listItem.addEventListener('dragover', function(e) {
      if (e.preventDefault) {
        e.preventDefault();
      }
      e.dataTransfer.dropEffect = 'move';
      return false;
    });

    listItem.addEventListener('dragenter', function(e) {
      if (this !== draggedItem) {
        this.classList.add('border-pdf-blue', 'bg-blue-50');
      }
    });

    listItem.addEventListener('dragleave', function(e) {
      this.classList.remove('border-pdf-blue', 'bg-blue-50');
    });

    listItem.addEventListener('drop', function(e) {
      if (e.stopPropagation) {
        e.stopPropagation();
      }

      if (draggedItem !== this) {
        // Get all items and their positions
        var fileListDiv = document.getElementById('fileList');
        var items = Array.from(fileListDiv.querySelectorAll('.file-item'));
        var draggedIndex = items.indexOf(draggedItem);
        var targetIndex = items.indexOf(this);

        // Remove the dragged item from its current position
        draggedItem.remove();

        // Insert the dragged item in the new position
        if (targetIndex < draggedIndex) {
          // Insert before the target
          fileListDiv.insertBefore(draggedItem, this);
        } else {
          // Insert after the target
          fileListDiv.insertBefore(draggedItem, this.nextSibling);
        }

        // Update order numbers
        updateFileOrderNumbers();
      }

      this.classList.remove('border-pdf-blue', 'bg-blue-50');
      return false;
    });
  }
})();
