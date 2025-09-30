# TailwindCSS Integration Task List

## Overview
Migration from CDN-based TailwindCSS to proper build pipeline integration for Spring Boot + Thymeleaf project.

## Phase 1: Environment Setup (Tasks 1-8)

### Node.js and Package Management
- [x] 1. Install Node.js (version 18+ recommended) on development environment
- [x] 2. Initialize npm project with `npm init -y` in project root
- [x] 3. Install TailwindCSS as dev dependency: `npm install -D tailwindcss@^3.4.0`
- [x] 4. Install PostCSS as dev dependency: `npm install -D postcss@^8.4.0`
- [x] 5. Install Autoprefixer as dev dependency: `npm install -D autoprefixer@^10.4.0`
- [x] 6. Add .gitignore entries for `node_modules/` and `package-lock.json`
- [x] 7. Create package.json scripts for CSS development and production builds
- [x] 8. Verify Node.js toolchain integration with project structure

## Phase 2: Configuration Files (Tasks 9-16)

### TailwindCSS Configuration
- [x] 9. Create `tailwind.config.js` in project root
- [x] 10. Configure content paths for template scanning: `./src/main/resources/templates/**/*.html`
- [x] 11. Configure content paths for JavaScript scanning: `./src/main/resources/static/js/**/*.js`
- [x] 12. Enable dark mode with `darkMode: 'class'` configuration
- [x] 13. Add custom color extensions for `pdf-blue: '#3B82F6'` and `pdf-gray: '#6B7280'`
- [x] 14. Create `postcss.config.js` with TailwindCSS and Autoprefixer plugins
- [x] 15. Configure TailwindCSS plugins array (empty initially, ready for future extensions)
- [x] 16. Test configuration files with `npx tailwindcss --help`

## Phase 3: Source CSS Structure (Tasks 17-24)

### Input CSS Creation
- [x] 17. Create directory structure: `src/main/tailwind/`
- [x] 18. Create `src/main/tailwind/input.css` with base Tailwind directives
- [x] 19. Add `@tailwind base;` directive to input.css
- [x] 20. Add `@tailwind components;` directive to input.css
- [x] 21. Add `@tailwind utilities;` directive to input.css
- [x] 22. Create `@layer components` section for custom component classes
- [x] 23. Migrate existing custom styles to component layer classes
- [x] 24. Test CSS compilation with `npx tailwindcss -i ./src/main/tailwind/input.css -o ./test-output.css`

## Phase 4: Component Migration (Tasks 25-35)

### Custom Component Classes
- [x] 25. Create `.btn-primary` component class with gradient background
- [x] 26. Add hover effects to `.btn-primary` (transform and shadow)
- [x] 27. Create `.upload-area` component class with dashed border styling
- [x] 28. Add hover and dragover states to `.upload-area`
- [x] 29. Create `.operation-card` component class for operation tiles
- [x] 30. Add hover effects to `.operation-card` (transform and shadow)
- [x] 31. Create `.category-title` component class with gradient text
- [x] 32. Migrate `.config-section` transition styles to component layer
- [x] 33. Create utility classes for common spacing and layout patterns
- [x] 34. Test component compilation and verify CSS output
- [x] 35. Document component classes in code comments

## Phase 5: Build Script Configuration (Tasks 36-43)

### NPM Scripts Setup
- [x] 36. Add `dev:css` script for development with watch mode
- [x] 37. Add `build:css` script for production with minification
- [x] 38. Configure input path: `./src/main/tailwind/input.css`
- [x] 39. Configure output path: `./src/main/resources/static/css/app.css`
- [x] 40. Add `NODE_ENV=production` for production builds
- [x] 41. Test development build script execution
- [x] 42. Test production build script execution
- [x] 43. Verify output CSS file generation and content

## Phase 6: Template Updates - Remove CDN (Tasks 44-52)

### CDN Removal from Templates
- [x] 44. Remove CDN script tag from `src/main/resources/templates/index.html`
- [x] 45. Remove CDN script tag from `src/main/resources/templates/operations/base.html`
- [x] 46. Remove inline TailwindCSS configuration from `index.html`
- [x] 47. Remove inline TailwindCSS configuration from `base.html`
- [x] 48. Remove inline `<style>` blocks from `index.html`
- [x] 49. Remove inline `<style>` blocks from `base.html`
- [x] 50. Scan all other templates for CDN references and remove them
- [x] 51. Verify no remaining CDN or inline configuration exists
- [x] 52. Test templates load without CDN dependencies

## Phase 7: Template Updates - Add Build CSS (Tasks 53-60)

### Build CSS Integration
- [x] 53. Add `<link rel="stylesheet" href="/css/app.css">` to `index.html`
- [x] 54. Add `<link rel="stylesheet" href="/css/app.css">` to `base.html`
- [x] 55. Ensure CSS link is placed before existing `theme.css` link
- [x] 56. Update all operation templates to include app.css link
- [x] 57. Verify CSS load order: app.css first, then theme.css
- [x] 58. Test template rendering with build CSS
- [x] 59. Verify all styling is preserved after migration
- [x] 60. Check responsive design functionality

## Phase 8: Dark Mode Integration (Tasks 61-68)

### Dark Mode Compatibility
- [x] 61. Verify existing dark mode JavaScript continues to work
- [x] 62. Test dark mode class application with new CSS structure
- [x] 63. Ensure `theme.css` overrides work with compiled TailwindCSS
- [x] 64. Verify dark mode color scheme consistency
- [x] 65. Test theme toggle functionality
- [x] 66. Check localStorage theme persistence
- [x] 67. Verify system preference detection
- [x] 68. Test dark mode across all templates

## Phase 9: Gradle Integration (Tasks 69-76)

### Build System Integration
- [x] 69. Add `npmInstall` task to `build.gradle.kts`
- [x] 70. Configure `npmInstall` task inputs and outputs
- [x] 71. Add `buildCss` task to `build.gradle.kts`
- [x] 72. Configure `buildCss` task dependencies and inputs/outputs
- [x] 73. Make `processResources` depend on `buildCss` task
- [x] 74. Test Gradle build with CSS compilation
- [x] 75. Verify CSS is built before Spring Boot packaging
- [x] 76. Test production build process end-to-end

## Phase 10: Development Workflow (Tasks 77-84)

### Developer Experience
- [x] 77. Create development startup script combining CSS watch and Spring Boot
- [x] 78. Document development workflow in README.md
- [x] 79. Test hot reload functionality for CSS changes
- [x] 80. Verify template changes trigger appropriate rebuilds
- [x] 81. Create production build verification checklist
- [x] 82. Document troubleshooting common build issues
- [x] 83. Test build process on clean environment
- [x] 84. Verify build works without node_modules (CI/CD simulation)

## Phase 11: Performance Optimization (Tasks 85-92)

### Production Optimization
- [ ] 85. Verify CSS purging removes unused classes
- [ ] 86. Test minification in production builds
- [ ] 87. Measure CSS file size before and after migration
- [ ] 88. Verify no unused TailwindCSS classes remain in output
- [ ] 89. Test gzip compression effectiveness on generated CSS
- [ ] 90. Benchmark page load times with new CSS approach
- [ ] 91. Verify browser caching works correctly for app.css
- [ ] 92. Document performance improvements achieved

## Phase 12: Quality Assurance (Tasks 93-100)

### Testing and Validation
- [ ] 93. Cross-browser testing (Chrome, Firefox, Safari, Edge)
- [ ] 94. Mobile responsiveness testing across devices
- [ ] 95. Accessibility testing with screen readers
- [ ] 96. Visual regression testing for all templates
- [ ] 97. Performance testing under load
- [ ] 98. Verify all custom components render correctly
- [ ] 99. Test error scenarios (missing CSS file, build failures)
- [ ] 100. Validate HTML markup remains semantic and accessible

## Phase 13: Documentation and Cleanup (Tasks 101-108)

### Project Documentation
- [ ] 101. Update README.md with new build requirements
- [ ] 102. Document CSS architecture and component system
- [ ] 103. Create developer onboarding guide for CSS workflow
- [ ] 104. Document custom TailwindCSS configuration decisions
- [ ] 105. Create troubleshooting guide for common issues
- [ ] 106. Remove old CSS-related documentation references
- [ ] 107. Update deployment documentation with build requirements
- [ ] 108. Create CSS maintenance and extension guidelines

## Phase 14: CI/CD Integration (Tasks 109-116)

### Continuous Integration
- [ ] 109. Update CI/CD pipeline to install Node.js dependencies
- [ ] 110. Add CSS build step to CI/CD workflow
- [ ] 111. Configure caching for node_modules in CI/CD
- [ ] 112. Add CSS build verification to automated tests
- [ ] 113. Configure production CSS build in deployment pipeline
- [ ] 114. Test deployment process with new build requirements
- [ ] 115. Verify CSS assets are properly included in deployment artifacts
- [ ] 116. Document CI/CD configuration changes

## Phase 15: Final Validation (Tasks 117-120)

### Production Readiness
- [ ] 117. Deploy to staging environment and verify functionality
- [ ] 118. Perform end-to-end testing in staging environment
- [ ] 119. Validate all PDF operations work with new CSS
- [ ] 120. Get stakeholder approval for visual consistency

## Completion Metrics

### Success Criteria
- All 120 tasks completed and marked with [x]
- Zero CDN dependencies in production
- CSS file size optimized (target: <50KB compressed)
- Page load time maintained or improved
- All existing functionality preserved
- Dark mode fully functional
- Mobile responsiveness maintained
- Cross-browser compatibility verified

### Rollback Plan
- Keep CDN integration code in version control until migration is fully validated
- Maintain ability to quickly revert to CDN approach if critical issues arise
- Document rollback procedure for emergency situations

---

**Total Tasks: 120**
**Estimated Timeline: 2-3 weeks**
**Prerequisites: Node.js 18+, npm/yarn package manager**