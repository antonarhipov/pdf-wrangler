#!/bin/bash

# PDF Wrangler Development Startup Script
# This script starts both TailwindCSS watch mode and Spring Boot development server
# for optimal developer experience with hot reload capabilities

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[PDF-WRANGLER]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[PDF-WRANGLER]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[PDF-WRANGLER]${NC} $1"
}

print_error() {
    echo -e "${RED}[PDF-WRANGLER]${NC} $1"
}

# Function to cleanup background processes on exit
cleanup() {
    print_status "Shutting down development servers..."
    if [ ! -z "$CSS_PID" ]; then
        kill $CSS_PID 2>/dev/null || true
        print_status "TailwindCSS watch stopped"
    fi
    if [ ! -z "$SPRING_PID" ]; then
        kill $SPRING_PID 2>/dev/null || true
        print_status "Spring Boot server stopped"
    fi
    print_success "Development environment stopped"
    exit 0
}

# Set up signal handlers
trap cleanup SIGINT SIGTERM

print_status "Starting PDF Wrangler Development Environment"
print_status "=============================================="

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    print_error "Node.js is not installed. Please install Node.js 18+ to continue."
    exit 1
fi

# Check if npm is installed
if ! command -v npm &> /dev/null; then
    print_error "npm is not installed. Please install npm to continue."
    exit 1
fi

# Check if Java is installed
if ! command -v java &> /dev/null; then
    print_error "Java is not installed. Please install Java 21+ to continue."
    exit 1
fi

# Install npm dependencies if needed
if [ ! -d "node_modules" ]; then
    print_status "Installing npm dependencies..."
    npm install
    print_success "npm dependencies installed"
else
    print_status "npm dependencies already installed"
fi

# Start TailwindCSS in watch mode
print_status "Starting TailwindCSS watch mode..."
npm run dev:css &
CSS_PID=$!
print_success "TailwindCSS watch started (PID: $CSS_PID)"

# Wait a moment for CSS to compile initially
sleep 2

# Start Spring Boot development server
print_status "Starting Spring Boot development server..."
./gradlew bootRun &
SPRING_PID=$!
print_success "Spring Boot server started (PID: $SPRING_PID)"

print_success "Development environment is ready!"
print_status "=============================================="
print_status "🎨 TailwindCSS: Watching for changes in src/main/tailwind/input.css"
print_status "🌐 Spring Boot: http://localhost:8080"
print_status "🔄 Hot reload: CSS changes will be automatically compiled"
print_status "📝 Templates: Thymeleaf templates will be reloaded on change"
print_status "=============================================="
print_warning "Press Ctrl+C to stop both servers"

# Wait for both processes
wait $CSS_PID $SPRING_PID