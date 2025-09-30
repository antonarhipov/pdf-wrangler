# TailwindCSS Build System Troubleshooting Guide

## Overview
This guide provides solutions to common issues encountered when working with the TailwindCSS build system in PDF Wrangler.

## Environment Issues

### Node.js and npm Problems

#### Issue: "node: command not found"
**Symptoms:**
- Build fails with "node: command not found"
- npm commands don't work

**Solutions:**
1. Install Node.js 18+ from [nodejs.org](https://nodejs.org/)
2. Verify installation: `node --version`
3. Restart terminal after installation
4. Check PATH environment variable includes Node.js

#### Issue: "npm: command not found"
**Symptoms:**
- npm commands fail
- Node.js is installed but npm is missing

**Solutions:**
1. Reinstall Node.js (npm comes bundled)
2. Install npm separately: `curl -L https://www.npmjs.com/install.sh | sh`
3. Check npm version: `npm --version`

#### Issue: npm permission errors
**Symptoms:**
- "EACCES: permission denied" errors
- Cannot install packages globally

**Solutions:**
1. Use npm prefix for local installs: `npm config set prefix ~/.npm-global`
2. Add to PATH: `export PATH=~/.npm-global/bin:$PATH`
3. Use npx for one-time commands: `npx tailwindcss --help`
4. Fix npm permissions: `sudo chown -R $(whoami) ~/.npm`

### Dependency Issues

#### Issue: "Cannot resolve dependency" errors
**Symptoms:**
- npm install fails
- Missing dependency errors

**Solutions:**
1. Clear npm cache: `npm cache clean --force`
2. Delete node_modules: `rm -rf node_modules`
3. Delete package-lock.json: `rm package-lock.json`
4. Reinstall: `npm install`
5. Use npm ci for clean install: `npm ci`

#### Issue: Version conflicts
**Symptoms:**
- Peer dependency warnings
- Version mismatch errors

**Solutions:**
1. Check package.json for version conflicts
2. Update dependencies: `npm update`
3. Install specific versions: `npm install tailwindcss@^3.4.0`
4. Use npm audit: `npm audit fix`

## Build Process Issues

### TailwindCSS Compilation Problems

#### Issue: CSS not compiling
**Symptoms:**
- app.css file not generated
- Build completes but no output

**Solutions:**
1. Check input file exists: `ls -la src/main/tailwind/input.css`
2. Verify tailwind.config.js: `npx tailwindcss --help`
3. Run build manually: `npm run build:css`
4. Check for syntax errors in input.css
5. Verify content paths in tailwind.config.js

#### Issue: "Cannot find module" errors
**Symptoms:**
- Build fails with module not found
- Import/require errors

**Solutions:**
1. Verify all dependencies installed: `npm list`
2. Check file paths in configuration
3. Ensure postcss.config.js exists
4. Reinstall dependencies: `npm ci`

#### Issue: CSS output is empty or minimal
**Symptoms:**
- app.css generated but very small
- Missing styles in output

**Solutions:**
1. Check content paths in tailwind.config.js:
   ```javascript
   content: [
     "./src/main/resources/templates/**/*.html",
     "./src/main/resources/static/js/**/*.js"
   ]
   ```
2. Verify templates contain TailwindCSS classes
3. Disable purging temporarily: `safelist: [{pattern: /.*/}]`
4. Check for typos in class names

### Watch Mode Issues

#### Issue: Watch mode not detecting changes
**Symptoms:**
- CSS doesn't rebuild on file changes
- Manual rebuild required

**Solutions:**
1. Check if watch process is running: `ps aux | grep tailwindcss`
2. Kill existing processes: `pkill -f tailwindcss`
3. Restart watch mode: `npm run dev:css`
4. Check file permissions
5. Use polling mode: `--watch --poll`

#### Issue: Multiple watch processes
**Symptoms:**
- Multiple TailwindCSS processes running
- Conflicting builds

**Solutions:**
1. Kill all processes: `pkill -f tailwindcss`
2. Use development script: `./dev-start.sh`
3. Check for background processes: `jobs`
4. Use process manager like PM2

### Gradle Integration Issues

#### Issue: Gradle build fails with npm errors
**Symptoms:**
- "./gradlew build" fails
- npm-related error messages

**Solutions:**
1. Verify Node.js accessible to Gradle
2. Check npmInstall task configuration
3. Run npm commands manually first
4. Clear Gradle cache: `./gradlew clean`
5. Check build.gradle.kts syntax

#### Issue: CSS not included in JAR
**Symptoms:**
- JAR builds but CSS missing
- 404 errors for CSS files

**Solutions:**
1. Verify buildCss task runs: `./gradlew buildCss`
2. Check task dependencies in build.gradle.kts
3. Ensure processResources depends on buildCss
4. Check JAR contents: `jar tf build/libs/*.jar | grep css`

## Configuration Issues

### TailwindCSS Configuration Problems

#### Issue: Custom colors not working
**Symptoms:**
- pdf-blue, pdf-gray classes not available
- Custom theme not applied

**Solutions:**
1. Check tailwind.config.js theme extension:
   ```javascript
   theme: {
     extend: {
       colors: {
         'pdf-blue': '#3B82F6',
         'pdf-gray': '#6B7280'
       }
     }
   }
   ```
2. Rebuild CSS: `npm run build:css`
3. Clear browser cache
4. Check for typos in color names

#### Issue: Dark mode not working
**Symptoms:**
- Dark mode classes not applied
- Theme toggle not functioning

**Solutions:**
1. Verify darkMode configuration: `darkMode: 'class'`
2. Check HTML class application: `document.documentElement.classList.contains('dark')`
3. Verify theme.css overrides
4. Test JavaScript theme toggle
5. Check localStorage persistence

### PostCSS Configuration Issues

#### Issue: PostCSS errors
**Symptoms:**
- Build fails with PostCSS errors
- Plugin-related error messages

**Solutions:**
1. Check postcss.config.js exists and is valid:
   ```javascript
   module.exports = {
     plugins: {
       tailwindcss: {},
       autoprefixer: {},
     }
   }
   ```
2. Verify plugin versions compatibility
3. Clear node_modules and reinstall
4. Check for syntax errors in CSS

## Template and Styling Issues

### Missing Styles

#### Issue: Styles not applied to elements
**Symptoms:**
- HTML renders but no styling
- Classes present but no effect

**Solutions:**
1. Check CSS file loading: View page source, verify CSS link
2. Inspect element: Check if classes are in compiled CSS
3. Clear browser cache: Hard refresh (Ctrl+F5)
4. Check CSS file path: `/css/app.css`
5. Verify CSS file exists and has content

#### Issue: Custom components not working
**Symptoms:**
- .btn-primary, .upload-area classes not styled
- Component classes missing

**Solutions:**
1. Check input.css @layer components section
2. Verify component definitions syntax
3. Rebuild CSS: `npm run build:css`
4. Check for CSS compilation errors
5. Inspect compiled CSS for component classes

### Development vs Production Differences

#### Issue: Styles work in development but not production
**Symptoms:**
- Development looks correct
- Production build missing styles

**Solutions:**
1. Check NODE_ENV=production purging
2. Verify content paths include all templates
3. Use safelist for dynamic classes
4. Compare dev vs prod CSS file sizes
5. Test production build locally

## Performance Issues

### Build Speed Problems

#### Issue: Slow CSS compilation
**Symptoms:**
- Build takes very long time
- Watch mode is sluggish

**Solutions:**
1. Optimize content paths (be specific)
2. Use .gitignore patterns to exclude files
3. Reduce template scanning scope
4. Update to latest TailwindCSS version
5. Use SSD for faster file I/O

#### Issue: Large CSS file size
**Symptoms:**
- app.css is unexpectedly large
- Slow page loading

**Solutions:**
1. Check purging is enabled in production
2. Review safelist configuration
3. Remove unused custom CSS
4. Enable gzip compression
5. Analyze CSS content for unused rules

## Development Workflow Issues

### Hot Reload Problems

#### Issue: Changes not reflected immediately
**Symptoms:**
- CSS changes require manual refresh
- Template changes not updating

**Solutions:**
1. Verify watch mode is running
2. Check browser cache settings
3. Use hard refresh (Ctrl+F5)
4. Restart development server
5. Check file modification times

#### Issue: Development script not working
**Symptoms:**
- ./dev-start.sh fails
- Processes don't start correctly

**Solutions:**
1. Check script permissions: `chmod +x dev-start.sh`
2. Verify script syntax
3. Check for missing dependencies
4. Run components separately for debugging
5. Check process IDs and cleanup

## Debugging Commands

### Diagnostic Commands
```bash
# Check Node.js and npm versions
node --version
npm --version

# Verify dependencies
npm list --depth=0

# Check TailwindCSS installation
npx tailwindcss --help

# Test CSS compilation
npm run build:css

# Check file sizes
ls -lh src/main/resources/static/css/

# Verify Gradle tasks
./gradlew tasks --group=build

# Check running processes
ps aux | grep -E "(node|tailwind|gradle)"

# Test configuration
npx tailwindcss -c tailwind.config.js --help
```

### CSS Analysis Commands
```bash
# Check CSS content
head -n 50 src/main/resources/static/css/app.css

# Search for specific classes
grep -n "btn-primary" src/main/resources/static/css/app.css

# Count CSS rules
grep -c "{" src/main/resources/static/css/app.css

# Check file modification time
stat src/main/resources/static/css/app.css
```

### Template Analysis Commands
```bash
# Find TailwindCSS classes in templates
grep -r "class=" src/main/resources/templates/ | head -10

# Check for CDN references
grep -r "tailwindcss" src/main/resources/templates/

# Verify CSS links
grep -r "app.css" src/main/resources/templates/
```

## Getting Help

### Log Analysis
1. Check console output for error messages
2. Look for stack traces in build logs
3. Enable verbose logging: `npm run build:css --verbose`
4. Check browser developer tools console
5. Review Gradle build logs

### Community Resources
- [TailwindCSS Documentation](https://tailwindcss.com/docs)
- [TailwindCSS GitHub Issues](https://github.com/tailwindlabs/tailwindcss/issues)
- [PostCSS Documentation](https://postcss.org/)
- [Gradle Documentation](https://docs.gradle.org/)

### Internal Resources
- Check `docs/tasks-tailwind.md` for implementation details
- Review `docs/production-build-checklist.md` for verification steps
- Consult README.md for setup instructions
- Check Git history for recent changes

## Prevention Tips

### Best Practices
1. Always use `npm ci` for clean installs
2. Keep dependencies up to date
3. Test builds in clean environments
4. Use version control for all configuration files
5. Document any custom modifications
6. Regular backup of working configurations
7. Monitor build performance metrics
8. Use consistent Node.js versions across environments

### Monitoring
1. Set up build time alerts
2. Monitor CSS file size changes
3. Track dependency vulnerabilities
4. Watch for deprecation warnings
5. Regular performance benchmarks

---

**Last Updated:** September 2024  
**Version:** 1.0  
**Maintainer:** PDF Wrangler Development Team