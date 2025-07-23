# clj-swagger

Starting postgres
```sh
docker compose up db -d
```

Starting a CLJ Repl
```sh
clj -M:dev:nrepl
```

Starting a CLJS Repl
```sh
cd frontend
npm install
npx shadow-cljs watch app
```

Starting the clojure-mcp server
```sh
PORT=$(cat .nrepl-port 2>/dev/null); clojure -X:mcp :port $PORT :shadow-port 7889 :shadow-build "app"
```

Starting the playwright mcp server
```sh
npx @playwright/mcp@latest
```

### Testing

```sh
clj -X:test
```

## License
