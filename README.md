# clj-swagger

Starting a CLJ Repl
```sh
clj -M:dev:nrepl
```

Starting a CLJS Repl
```
cd frontend
npm install
npx shadow-cljs watch app
```

Starting the clojure-mcp server
```sh
PORT=$(cat .nrepl-port 2>/dev/null); clojure -X:mcp :port $PORT :shadow-port 7889 :shadow-build "app"
```

### Testing

```sh
clj -X:test
```

## License
