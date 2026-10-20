# bookstack stack module

- Module id: `bookstack`
- Module repo: `bookstack-stack-module`
- Source repo: none declared
- Lifecycle: `active`

## Owned overlays
- `stack.runtime.yaml`
- `stack.config/bookstack`

## Dependencies
- `stack-foundation`

## Validation

```sh
./tests/validate.sh
```

## Database bootstrap endpoint

Permission bootstrap SQL uses the same `DB_HOST` and `DB_PORT` as Laravel, including
mapped cross-domain ports. If not supplied in the environment, the optional port
is read from the generated `.env`, default3306. A quoted client wrapper applies it
to every query without shell interpolation. Argument fixtures prove default/mapped
port handling, not installed database or BookStack acceptance.

## Lifecycle

`active` modules are expected to keep `stack.module.json`, owned overlays, and `tests/validate.sh` in sync.
