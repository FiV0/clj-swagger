# Clojure Swagger API Project

## Overview
A Clojure web API project built with Reitit and Ring that provides Swagger documentation and API endpoints. The project demonstrates modern Clojure web development practices using functional routing, spec-based validation, and integrated Swagger UI.

## Project Structure

### Core Files
- **`src/clj_swagger.clj`** - Main application logic with HTTP routes, handlers, and Integrant system configuration
- **`src/main.clj`** - Entry point with `-main` function for application startup
- **`test/clj_template_test.clj`** - Test suite
- **`build.clj`** - Build configuration using tools.build for JAR compilation
- **`deps.edn`** - Project dependencies and development aliases
- **`dev/user.clj`** - Development namespace for REPL workflow

### Resource Files
- **`resources/logback.xml`** - Logging configuration
- **`resources/public/`** - Static resources directory

## Key Dependencies & Their Roles

### Core Web Stack
- **`metosin/reitit`** (0.7.2) - Data-driven routing library with coercion and middleware support
- **`ring/ring-core`** (1.10.0) - HTTP abstraction layer
- **`info.sunng/ring-jetty9-adapter`** (0.22.4) - Jetty web server adapter with HTTP/2 support
- **`metosin/muuntaja`** (0.6.10) - Content negotiation and format transformation

### System Management
- **`integrant/integrant`** (0.13.1) - Component lifecycle management system
- **`integrant/repl`** (dev) - REPL integration for system management

### API Documentation
- **`reitit.swagger`** - Automatic Swagger/OpenAPI spec generation
- **`reitit.swagger-ui`** - Integrated Swagger UI interface

### Data Processing
- **`com.cognitect/transit-clj`** (1.0.329) - Transit data format support
- **`cheshire/cheshire`** (dev) - JSON processing
- **`jsonista.core`** - Fast JSON library

### Development Tools
- **`org.clojure/tools.namespace`** (dev) - Namespace reloading for REPL workflow
- **`hato/hato`** (dev) - HTTP client for testing
- **`nrepl/nrepl`** - Network REPL server

## Available APIs & Usage

### HTTP Endpoints
The application provides RESTful endpoints with automatic Swagger documentation:

```clojure
;; Example route handlers using multimethod dispatch
(defmethod route-handler :get [_] 
  ;; GET endpoint implementation
  )

(defmethod route-handler :post [_]
  ;; POST endpoint with request body processing
  )

(defmethod route-handler :get-with-param [_]
  ;; Parameterized GET endpoint
  )
```

### Swagger Integration
- **`/swagger.json`** - OpenAPI specification endpoint
- **`/swagger-ui`** - Interactive API documentation interface

### System Management
```clojure
;; Integrant system configuration
(defmethod ig/init-key :clj-swagger/server [_ opts]
  ;; Server initialization
  )

(defmethod ig/halt-key! :clj-swagger/server [_ server]
  ;; Clean server shutdown
  )
```

## Architecture & Component Interaction

### Request Flow
1. **Jetty Server** receives HTTP requests
2. **Reitit Router** matches routes and applies middleware
3. **Muuntaja** handles content negotiation and format transformation
4. **Route Handlers** process business logic using multimethod dispatch
5. **Spec Coercion** validates request/response data
6. **Swagger** auto-generates documentation from route definitions

### System Lifecycle
- **Integrant** manages component dependencies and startup/shutdown order
- **Development REPL** provides interactive development with system reloading

## Implementation Patterns

### Functional Routing
- Data-driven route definitions with embedded middleware and coercion specs
- Multimethod dispatch for handler organization
- Interceptor-based middleware chain

### Configuration Management
- External configuration via Integrant system maps
- Environment-specific settings support
- Component dependency injection

### Content Handling
- Automatic content type negotiation
- Multiple format support (JSON, Transit, etc.)
- Request/response transformation pipelines

## Development Workflow

### REPL Development
```bash
# Start development REPL
clj -M:dev:nrepl

# Start MCP server for tool integration
clj -X:mcp
```

### Testing
```bash
# Run test suite
clj -X:test
```

### Building
```bash
# Clean and build JAR
clj -T:build clean
clj -T:build jar

# Run compiled application
java -jar target/lib-0.1.4.jar
```

### Local Development
```bash
# Run application directly
clj -M -m main
```

## Extension Points

### Adding New Endpoints
1. Define route data in `http-routes`
2. Implement handler via `route-handler` multimethod
3. Add request/response specs for validation
4. Swagger documentation auto-generated

### Middleware Integration
- Add interceptors to route definitions
- Extend muuntaja for new content types
- Custom exception handling via reitit.http.interceptors.exception

### System Components
- Add new Integrant keys for additional services
- Define component dependencies in system configuration
- Implement lifecycle methods for proper startup/shutdown

### Database Integration
- Add database component to Integrant system
- Implement connection pooling and transaction management
- Integrate with route handlers for data persistence

## Configuration Examples

### Adding Database Component
```clojure
(defmethod ig/init-key :clj-swagger/database [_ config]
  ;; Database connection setup
  )
```

### Custom Middleware
```clojure
;; Add to route middleware chain
{:middleware [custom-auth-middleware ri.muuntaja/format-middleware]}
```

This project serves as a solid foundation for building production-ready Clojure web APIs with comprehensive documentation and modern development practices.